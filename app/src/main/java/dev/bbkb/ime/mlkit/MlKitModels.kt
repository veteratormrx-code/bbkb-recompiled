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
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink

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
    }

    @JvmStatic
    fun downloadInk(languageTag: String, onDone: (Boolean, String) -> Unit) {
        val id = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
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
        val id = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag) ?: return
        val model = DigitalInkRecognitionModel.builder(id).build()
        RemoteModelManager.getInstance().deleteDownloadedModel(model)
            .addOnSuccessListener { onDone(true, "Deleted handwriting $languageTag") }
            .addOnFailureListener { onDone(false, it.message ?: "Delete failed") }
    }

    @JvmStatic
    fun recognizeInk(languageTag: String, ink: Ink, onDone: (Boolean, String) -> Unit) {
        val id = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
        if (id == null) {
            onDone(false, "Unknown handwriting language")
            return
        }
        val recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(DigitalInkRecognitionModel.builder(id).build()).build()
        )
        recognizer.recognize(ink)
            .addOnSuccessListener { result ->
                val text = result.candidates.firstOrNull()?.text ?: ""
                onDone(true, text)
            }
            .addOnFailureListener { onDone(false, it.message ?: "Recognition failed") }
    }

    @JvmStatic
    fun russian(): String = TranslateLanguage.RUSSIAN

    @JvmStatic
    fun english(): String = TranslateLanguage.ENGLISH
}
