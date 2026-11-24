package dev.bbkb.ime.harness

import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Field matrix for soft-keyboard checks: text, number, phone, uri, email, password, WebView, image. */
class HarnessActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        fun add(label: String, inputType: Int, hint: String, action: Int = EditorInfo.IME_ACTION_NEXT) {
            column.addView(TextView(this).apply { text = label })
            column.addView(EditText(this).apply {
                this.inputType = inputType
                this.hint = hint
                imeOptions = action
                importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_YES
            })
        }
        add("text", InputType.TYPE_CLASS_TEXT, "plain")
        add("multiline", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, "multiline", EditorInfo.IME_ACTION_NONE)
        add("number", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED, "number")
        add("phone", InputType.TYPE_CLASS_PHONE, "phone")
        add("url", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, "url")
        add("email", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, "email")
        add("password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, "password")
        column.addView(TextView(this).apply { text = "webview" })
        column.addView(WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 400)
            loadData(
                """<html><body><input id='n' type='number' placeholder='web number'/><br/>
                   <input id='t' type='text' placeholder='web text'/></body></html>""",
                "text/html",
                "utf-8",
            )
        })
        column.addView(TextView(this).apply { text = "image target (commitContent)" })
        column.addView(EditText(this).apply {
            hint = "paste image here"
            inputType = InputType.TYPE_CLASS_TEXT
        })
        setContentView(ScrollView(this).apply { addView(column) })
    }
}
