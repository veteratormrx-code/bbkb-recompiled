package dev.bbkb.ime.harness

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.ContentInfoCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Visible, independently focusable editors for exercising the installed IME. */
class HarnessActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        var editorId = 1
        fun add(
            label: String,
            inputType: Int = InputType.TYPE_CLASS_TEXT,
            hint: String = label,
            action: Int = EditorInfo.IME_ACTION_NEXT,
            configure: EditText.() -> Unit = {},
        ): EditText {
            val field = AppCompatEditText(this).apply {
                id = editorId++
                tag = hint
                this.inputType = inputType
                this.hint = hint
                imeOptions = action
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                configure()
            }
            column.addView(TextView(this).apply {
                text = label
                labelFor = field.id
            })
            column.addView(field)
            return field
        }
        add("text", InputType.TYPE_CLASS_TEXT, "plain")
        add("multiline", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, "multiline", EditorInfo.IME_ACTION_NONE)
        add("number", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED, "number")
        add("integer", InputType.TYPE_CLASS_NUMBER)
        add("decimal", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        add("signed integer", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED)
        add("phone", InputType.TYPE_CLASS_PHONE, "phone")
        add("url", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, "url")
        add("email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, "email")
        add("password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, "password")
        add("numeric password", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        add("search", action = EditorInfo.IME_ACTION_SEARCH)
        add("name", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
        add("address", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS)
        add("no suggestions", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        add("no personalized learning", action = EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        add("capitalize sentences", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        add("max length 8") { filters = arrayOf(InputFilter.LengthFilter(8)) }
        add("reject digits") {
            filters = arrayOf(InputFilter { source, start, end, _, _, _ ->
                val incoming = source.subSequence(start, end)
                if (incoming.any { it.isDigit() }) incoming.filterNot { it.isDigit() } else null
            })
        }
        add("selection") { setText("alpha bravo charlie") }
        add("autofill username") {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            setAutofillHints(View.AUTOFILL_HINT_USERNAME)
        }
        add("autofill password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD) {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
        }
        add("action send", action = EditorInfo.IME_ACTION_SEND)
        add("action go", action = EditorInfo.IME_ACTION_GO)
        add("action done", action = EditorInfo.IME_ACTION_DONE)
        column.addView(TextView(this).apply { text = "webview" })
        column.addView(WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (390 * resources.displayMetrics.density).toInt(),
            )
            loadDataWithBaseURL(
                null,
                """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                   <body><input type="number" placeholder="web number"><br>
                   <input type="text" placeholder="web text"><br>
                   <input type="email" placeholder="web email"><br>
                   <input type="url" placeholder="web URL"><br>
                   <input type="tel" placeholder="web telephone"><br>
                   <input type="password" placeholder="web password"><br>
                   <input type="search" placeholder="web search"><br>
                   <input inputmode="numeric" placeholder="web inputmode numeric"><br>
                   <input inputmode="decimal" placeholder="web inputmode decimal"><br>
                   <input inputmode="tel" placeholder="web inputmode tel"><br>
                   <textarea placeholder="web textarea"></textarea>
                   <p contenteditable="true" aria-label="web contenteditable">Edit this text</p></body></html>""",
                "text/html",
                "utf-8",
                null,
            )
        })
        add("plain target", hint = "text only")
        val richEditor = add("image target", hint = "paste image here")
        val receivedImage = ImageView(this).apply { adjustViewBounds = true }
        val result = TextView(this).apply { text = "No image received" }
        column.addView(result)
        column.addView(receivedImage)
        ViewCompat.setOnReceiveContentListener(richEditor, arrayOf("image/*")) { _, content ->
            val parts = content.partition { it.uri != null }
            val handled = parts.first ?: return@setOnReceiveContentListener parts.second
            val uri = handled.clip.getItemAt(0).uri
            receivedImage.setImageURI(uri)
            result.text = if (receivedImage.drawable != null) {
                "Image received (source ${handled.source})"
            } else {
                "Image decoding failed"
            }
            if (receivedImage.drawable != null && handled.source == ContentInfoCompat.SOURCE_INPUT_METHOD) {
                receivedImage.contentDescription = "Image inserted by keyboard"
            } else {
                receivedImage.contentDescription = null
            }
            parts.second
        }
        val scrollView = ScrollView(this).apply { addView(column) }
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
            val padding = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(padding.left, padding.top, padding.right, padding.bottom)
            insets
        }
        setContentView(scrollView)
    }
}
