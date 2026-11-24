package dev.bbkb.ime.keyboard.inputboard.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.inputmethod.EditorInfo
import android.inputmethodservice.InputMethodService
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.core.content.FileProvider
import java.io.File

/** Copies image clips into app-private storage and pastes them with commitContent. */
object ClipboardImages {
    const val DIR = "clipboard_images"

    @JvmStatic
    fun capture(context: android.content.Context, clip: ClipData, nowMs: Long): ClipEntry? {
        if (clip.itemCount <= 0) return null
        val description = clip.description ?: return null
        val mime = (0 until description.mimeTypeCount)
            .map { description.getMimeType(it) }
            .firstOrNull { it.startsWith("image/") }
            ?: return null
        val uri: Uri = clip.getItemAt(0).uri ?: return null
        val dir = File(context.filesDir, DIR)
        if (!dir.exists() && !dir.mkdirs()) return null
        val name = "$nowMs-${uri.hashCode().toUInt()}.img"
        val out = File(dir, name)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            } ?: return null
            if (out.length() <= 0L) {
                out.delete()
                return null
            }
            writeThumb(out)
            ClipEntry(
                id = 0,
                text = "",
                label = description.label?.toString(),
                mime = mime,
                createdAtMs = nowMs,
                imageFile = "$DIR/$name",
            )
        } catch (e: Exception) {
            out.delete()
            null
        }
    }

    private fun writeThumb(image: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(image.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 192 || bounds.outHeight / sample > 192) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(image.absolutePath, opts) ?: return
        File(image.parentFile, image.name + ".thumb").outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        }
        bitmap.recycle()
    }

    @JvmStatic
    fun delete(context: android.content.Context, entry: ClipEntry) {
        val relative = entry.imageFile ?: return
        File(context.filesDir, relative).delete()
        File(context.filesDir, "$relative.thumb").delete()
    }

    @JvmStatic
    fun commit(ime: InputMethodService, entry: ClipEntry, editor: EditorInfo?): Boolean {
        val relative = entry.imageFile ?: return false
        val file = File(ime.filesDir, relative)
        if (!file.isFile || editor == null) return false
        val mime = entry.mime.ifEmpty { "image/*" }
        val uri = FileProvider.getUriForFile(ime, ime.packageName + ".fileprovider", file)
        val info = InputContentInfoCompat(uri, ClipDescription("image", arrayOf(mime)), null)
        val connection = ime.currentInputConnection ?: return false
        return InputConnectionCompat.commitContent(
            connection,
            editor,
            info,
            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
            null,
        )
    }
}
