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
import java.io.IOException

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
        val out = try {
            File.createTempFile("clip-", ".img", dir)
        } catch (e: IOException) {
            return null
        }
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Image unavailable")
            input.use {
                out.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var size = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        size += read
                        if (size > ClipboardHistoryManager.MAX_STORE_BYTES) throw IOException("Image exceeds history budget")
                        output.write(buffer, 0, read)
                    }
                }
            }
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
                imageFile = "$DIR/${out.name}",
            )
        } catch (e: Exception) {
            out.delete()
            File(dir, out.name + ".thumb").delete()
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
        if (!entry.isImage()) return
        val image = fileFor(context.filesDir, entry.imageFile) ?: return
        image.delete()
        File(image.parentFile, image.name + ".thumb").delete()
    }

    @JvmStatic
    fun clear(context: android.content.Context) {
        val filesDir = runCatching { context.filesDir }.getOrNull() ?: return
        File(filesDir, DIR).listFiles()?.forEach { it.delete() }
    }

    @JvmStatic
    fun pruneOrphans(context: android.content.Context, entries: List<ClipEntry>) {
        val filesDir = context.filesDir
        val referenced = HashSet<String>()
        for (entry in entries) {
            val file = fileFor(filesDir, entry.imageFile) ?: continue
            referenced.add(file.name)
            if (file.isFile) referenced.add(file.name + ".thumb")
        }
        File(filesDir, DIR).listFiles()?.forEach { file ->
            if (file.name !in referenced) file.delete()
        }
    }

    internal fun fileFor(filesDir: File, relative: String?): File? {
        if (relative == null || !relative.startsWith("$DIR/")) return null
        val name = relative.removePrefix("$DIR/")
        if (!name.endsWith(".img") || name.contains('/') || name.contains('\\')) return null
        return File(File(filesDir, DIR), name)
    }

    @JvmStatic
    fun commit(ime: InputMethodService, entry: ClipEntry, editor: EditorInfo?): Boolean {
        val file = fileFor(ime.filesDir, entry.imageFile) ?: return false
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
