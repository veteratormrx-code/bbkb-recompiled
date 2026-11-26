package dev.bbkb.ime.keyboard.slideboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhraseTemplatesTest {
    @Test
    fun aRepeatedFieldIsFilledOnceAndValuesAreLiteral() {
        val template = "{имя}, hello {имя}. {day}"
        assertEquals(listOf("имя", "day"), PhraseTemplates.fields(template))
        assertEquals("{day}, hello {day}. Monday",
            PhraseTemplates.fill(template, mapOf("имя" to "{day}", "day" to "Monday")))
    }

    @Test
    fun preservesTemplateOrderAndGroupsInPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_phrase_templates", Context.MODE_PRIVATE)
        val templates = listOf(
            PhraseTemplates.Template("Work", "Hello, {name}!"),
            PhraseTemplates.Template("Дом", "Позвоню вечером"),
        )
        PhraseTemplates.save(prefs, templates)
        assertEquals(templates, PhraseTemplates.read(prefs))
    }
}
