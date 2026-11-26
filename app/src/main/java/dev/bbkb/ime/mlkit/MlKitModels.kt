package dev.bbkb.ime.mlkit

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink

/** Official ML Kit model lifecycle. Typing does not call this. Network is only for downloads. */
object MlKitModels {
    data class Status(val gmsAvailable: Boolean, val message: String)

    @JvmStatic
    fun gmsStatus(context: Context): Status {
        val code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        val available = code == ConnectionResult.SUCCESS
        val message = if (available) {
            "Google Play services is available. Models can be downloaded and then used offline."
        } else {
            "Google Play services is not available ($code). Translate and handwriting stay disabled; typing is unchanged."
        }
        return Status(available, message)
    }

    @JvmStatic
    fun downloadTranslate(source: String, target: String, onDone: (Boolean, String) -> Unit) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()
        val translator = Translation.getClient(options)
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { onDone(true, "Model ready: $source → $target") }
            .addOnFailureListener { onDone(false, it.message ?: "Download failed") }
            .addOnCompleteListener { translator.close() }
    }

    fun translationLanguages(): List<String> = TranslateLanguage.getAllLanguages()

    fun downloadedTranslationLanguages(onDone: (Set<String>) -> Unit) {
        RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models -> onDone(models.mapTo(mutableSetOf()) { it.language }) }
            .addOnFailureListener { onDone(emptySet()) }
    }

    @JvmStatic
    fun deleteTranslate(language: String, onDone: (Boolean, String) -> Unit) {
        val model = TranslateRemoteModel.Builder(language).build()
        RemoteModelManager.getInstance().deleteDownloadedModel(model)
            .addOnSuccessListener { onDone(true, "Deleted $language") }
            .addOnFailureListener { onDone(false, it.message ?: "Delete failed") }
    }

    @JvmStatic
    fun translate(source: String, target: String, text: String, onDone: (Boolean, String) -> Unit) {
        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
        )
        translator.translate(text)
            .addOnSuccessListener { onDone(true, it) }
            .addOnFailureListener { onDone(false, it.message ?: "Translate failed") }
            .addOnCompleteListener { translator.close() }
    }

    @JvmStatic
    fun downloadInk(languageTag: String, onDone: (Boolean, String) -> Unit) {
        val id = inkModelId(languageTag)
        if (id == null) {
            onDone(false, "Unknown handwriting language $languageTag")
            return
        }
        val model = DigitalInkRecognitionModel.builder(id).build()
        RemoteModelManager.getInstance().download(model, DownloadConditions.Builder().build())
            .addOnSuccessListener { onDone(true, "Handwriting model ready: $languageTag") }
            .addOnFailureListener { onDone(false, it.message ?: "Download failed") }
    }

    @JvmStatic
    fun deleteInk(languageTag: String, onDone: (Boolean, String) -> Unit) {
        val id = inkModelId(languageTag) ?: return
        val model = DigitalInkRecognitionModel.builder(id).build()
        RemoteModelManager.getInstance().deleteDownloadedModel(model)
            .addOnSuccessListener { onDone(true, "Deleted handwriting $languageTag") }
            .addOnFailureListener { onDone(false, it.message ?: "Delete failed") }
    }

    /** Recognition answers: the best words, or a failure message and no words. */
    data class InkGuess(val ok: Boolean, val texts: List<String>, val message: String)

    @JvmStatic
    fun recognizeInk(languageTag: String, ink: Ink, onDone: (InkGuess) -> Unit) {
        val id = inkModelId(languageTag)
        if (id == null) {
            onDone(InkGuess(false, emptyList(), "Unknown handwriting language"))
            return
        }
        val recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(DigitalInkRecognitionModel.builder(id).build()).build()
        )
        recognizer.recognize(ink)
            .addOnSuccessListener { result ->
                val texts = result.candidates.map { it.text.trim() }.filter { it.isNotEmpty() }.distinct().take(5)
                if (texts.isEmpty()) {
                    onDone(InkGuess(false, emptyList(), ""))
                } else {
                    onDone(InkGuess(true, texts, ""))
                }
            }
            .addOnFailureListener { onDone(InkGuess(false, emptyList(), it.message ?: "Recognition failed")) }
            .addOnCompleteListener { recognizer.close() }
    }

    /** "en" is not a handwriting model. Russian is "ru". */
    private fun inkModelId(languageTag: String): DigitalInkRecognitionModelIdentifier? {
        val tags = when (languageTag) {
            "en" -> listOf("en", "en-US")
            "ru" -> listOf("ru")
            else -> listOf(languageTag)
        }
        for (tag in tags) {
            DigitalInkRecognitionModelIdentifier.fromLanguageTag(tag)?.let { return it }
        }
        return null
    }

    @JvmStatic
    fun russian(): String = TranslateLanguage.RUSSIAN

    @JvmStatic
    fun english(): String = TranslateLanguage.ENGLISH
}
