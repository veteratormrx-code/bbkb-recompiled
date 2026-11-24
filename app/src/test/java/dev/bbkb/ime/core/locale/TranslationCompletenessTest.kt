package dev.bbkb.ime.core.locale

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every language with a subtype in `res/xml/method.xml` must have complete UI strings and plurals
 * in its `values-*` folders. Parsed straight off disk, no Android runtime. `values-*` folders for
 * languages with no subtype (am, kk, kl, my, ne, si, sw, ur, zu) are not checked.
 */
class TranslationCompletenessTest {

    /** `app/src/main`, however the test happens to be launched (cwd is usually `app/`). */
    private fun mainDir(): File {
        val dir = sequenceOf("src/main", "app/src/main", "../src/main", "../app/src/main", "../../app/src/main")
            .map { File(it).canonicalFile }
            .firstOrNull { it.isDirectory }
        assertTrue("app/src/main not found from ${File(".").canonicalPath}", dir != null)
        return dir!!
    }

    private fun resDir() = File(mainDir(), "res")

    private val stringNameRegex = Regex("""<(?:string|plurals)\s+name="([^"]+)"([^>]*?)(?:/>|>)""")
    private val subtypeLocaleRegex = Regex("""imeSubtypeLocale="([^"]+)"""")

    /** `fil` lives in the legacy `tl` folder; `zz` is the plain-alphabet subtype, not a language. */
    private val subtypeLangToResLang = mapOf("fil" to "tl")
    private val notALanguage = setOf("zz")

    /**
     * Subtype languages with no `values-*` folder at all (the UI shows English). Add the folder and
     * translate instead; list a language here, with a reason, only when that is impossible.
     */
    private val languagesWithNoResourceFolder = emptySet<String>()

    /**
     * Locale folders knowingly incomplete; name one here with a reason rather than weakening the test.
     * The 4PDA feature set (OLED themes, number row, ML Kit, text-edit bar, privacy) was added with
     * complete English and Russian strings only; the locales below fall back to English for those 37
     * strings until they are translated. See docs/qa/known-limitations.md.
     */
    private val incompleteLocaleAllowlist = setOf(
        "values-af", "values-ar", "values-az-rAZ", "values-be-rBY", "values-bg",
        "values-bn-rBD", "values-bn-rIN", "values-bs", "values-ca", "values-cs",
        "values-cy", "values-da", "values-de", "values-el", "values-es", "values-es-rUS",
        "values-et-rEE", "values-eu-rES", "values-fa", "values-fi", "values-fr",
        "values-fr-rCA", "values-ga", "values-gl-rES", "values-hi", "values-hr",
        "values-hu", "values-hy-rAM", "values-in", "values-is-rIS", "values-it",
        "values-iw", "values-ja", "values-jv", "values-ka-rGE", "values-km-rKH",
        "values-kn-rIN", "values-ko", "values-ky-rKG", "values-lo-rLA", "values-lt",
        "values-lv", "values-mk-rMK", "values-ml-rIN", "values-mn-rMN", "values-mr-rIN",
        "values-ms", "values-ms-rMY", "values-nb", "values-nl", "values-nl-rBE",
        "values-pl", "values-pt", "values-pt-rPT", "values-ro", "values-sk", "values-sl",
        "values-sq", "values-sr", "values-su", "values-sv", "values-ta-rIN",
        "values-te-rIN", "values-th", "values-tl", "values-tr", "values-uk",
        "values-uz-rUZ", "values-vi", "values-zh-rCN", "values-zh-rHK", "values-zh-rTW",
    )

    /** Every `<string name=...>` and `<plurals name=...>` in the folder not marked `translatable="false"`. */
    private fun stringNames(dir: File, translatableOnly: Boolean): Set<String> {
        if (!dir.isDirectory) return emptySet()
        val names = mutableSetOf<String>()
        dir.listFiles { f -> f.extension == "xml" && !f.name.startsWith("donottranslate") }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                val text = file.readText()
                stringNameRegex.findAll(text).forEach { m ->
                    val untranslatable = m.groupValues[2].contains("""translatable="false"""")
                    if (!translatableOnly || !untranslatable) names += m.groupValues[1]
                }
            }
        return names
    }

    /** Resource language codes the app ships a keyboard subtype for. */
    private fun subtypeLanguages(): Set<String> {
        val methodXml = File(resDir(), "xml/method.xml")
        assertTrue("method.xml not found at ${methodXml.path}", methodXml.isFile)
        val text = methodXml.readText()
        val languages = subtypeLocaleRegex.findAll(text)
            .map { it.groupValues[1].substringBefore('_') }
            .filter { it !in notALanguage }
            .map { subtypeLangToResLang[it] ?: it }
            .toSet()
        assertTrue("no subtypes parsed out of method.xml", languages.size > 20)
        return languages
    }

    /** `values-de`, `values-pt-rPT`, `values-b+sr+Latn`, ... for one language code. */
    private fun foldersForLanguage(language: String): List<File> =
        resDir().listFiles { f -> f.isDirectory }
            ?.filter { dir ->
                val q = dir.name.removePrefix("values-")
                dir.name.startsWith("values-") &&
                    (q == language || q.startsWith("$language-r") || q.startsWith("b+$language+"))
            }
            ?.sortedBy { it.name }
            ?: emptyList()

    /** Every `values-*` folder serving a subtype language; English is excluded (`values/` is English). */
    private fun targetFolders(): List<File> =
        subtypeLanguages().filter { it != "en" }.flatMap { foldersForLanguage(it) }.sortedBy { it.name }

    @Test
    fun everySubtypeLanguageHasAResourceFolder() {
        val withoutFolder = subtypeLanguages()
            .filter { it != "en" && foldersForLanguage(it).isEmpty() }
            .filter { it !in languagesWithNoResourceFolder }
            .sorted()
        assertTrue(
            "method.xml ships a subtype for these languages but res/ has no values-<lang> folder " +
                "for them: $withoutFolder. Add the folder and translate it, or add the code to " +
                "languagesWithNoResourceFolder with a reason.",
            withoutFolder.isEmpty()
        )
    }

    @Test
    fun allowlistEntriesAreStillRelevant() {
        val known = resDir().listFiles { f -> f.isDirectory }?.map { it.name }?.toSet().orEmpty()
        val stale = incompleteLocaleAllowlist.filterNot { it in known }
        assertTrue("incompleteLocaleAllowlist names folders that no longer exist: $stale", stale.isEmpty())
        val languages = subtypeLanguages()
        val staleLangs = languagesWithNoResourceFolder.filterNot { it in languages }
        assertTrue(
            "languagesWithNoResourceFolder names languages with no subtype any more: $staleLangs",
            staleLangs.isEmpty()
        )
        val nowPresent = languagesWithNoResourceFolder.filter { foldersForLanguage(it).isNotEmpty() }
        assertTrue(
            "these languages now have a values-<lang> folder and must come off " +
                "languagesWithNoResourceFolder: $nowPresent",
            nowPresent.isEmpty()
        )
    }

    @Test
    fun everyTargetLocaleTranslatesEveryBaseString() {
        val base = stringNames(File(resDir(), "values"), translatableOnly = true)
        assertTrue("suspiciously few base strings: ${base.size}", base.size > 500)

        val report = StringBuilder()
        var failed = 0
        for (folder in targetFolders()) {
            if (folder.name in incompleteLocaleAllowlist) continue
            val translated = stringNames(folder, translatableOnly = false)
            val missing = (base - translated).sorted()
            if (missing.isNotEmpty()) {
                failed++
                report.append("\n  ${folder.name}: ${missing.size} untranslated, e.g. ")
                    .append(missing.take(6).joinToString(", "))
            }
        }
        assertTrue(
            "$failed locale folder(s) are missing translations for strings in values/strings.xml." +
                " Translate them, or (only as a last resort) list the folder in " +
                "incompleteLocaleAllowlist with a reason:$report",
            failed == 0
        )
    }

    @Test
    fun everyTargetLocaleFileIsWellFormedXml() {
        val factory = DocumentBuilderFactory.newInstance()
        val broken = mutableListOf<String>()
        for (folder in targetFolders()) {
            folder.listFiles { f -> f.extension == "xml" }?.forEach { file ->
                try {
                    factory.newDocumentBuilder().parse(file)
                } catch (e: Exception) {
                    broken += "${folder.name}/${file.name}: ${e.message}"
                }
            }
        }
        assertTrue("malformed resource XML: $broken", broken.isEmpty())
    }
}
