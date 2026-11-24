package dev.bbkb.ime.keyboard.inputboard.emoji

import android.content.Context
import android.graphics.Paint
import android.os.Build
import dev.bbkb.ime.core.shared.Logger
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Emoji data from Emojibase plus the Unicode 17/18 additions, read from compact assets.
 */
class EmojibaseDataProvider(private val context: Context) {

    companion object {
        private const val TAG = "EmojibaseDataProvider"
        private const val MAX_SEARCH_RESULTS = 80  // ~2 pages worth

        // Shared instance, loaded on first use. Holders read it through getSharedLoaded/peekShared
        // and keep no field, so after releaseShared nothing retains the old dataset.
        @Volatile
        private var sharedLoadedInstance: EmojibaseDataProvider? = null

        /** Guards the parse only. [releaseShared] must never take it (see there). */
        private val loadLock = Any()

        /** True while [getSharedLoaded] is parsing, so a preload does not queue behind it. */
        @Volatile
        private var loadInFlight = false

        /** True from a preload's submission until it has run: coalesces repeated preloads. */
        private val preloadQueued = AtomicBoolean(false)

        /** For [preloadAsync] callers without an executor: one low-priority daemon thread that exits when idle. */
        private val defaultPreloadExecutor: Executor by lazy {
            ThreadPoolExecutor(0, 1, 10L, TimeUnit.SECONDS, LinkedBlockingQueue()) { r ->
                Thread(r, "emojibase-preload").apply {
                    isDaemon = true
                    priority = Thread.MIN_PRIORITY
                }
            }
        }

        @JvmStatic
        fun getSharedLoaded(context: Context): EmojibaseDataProvider {
            sharedLoadedInstance?.let { return it }
            synchronized(loadLock) {
                sharedLoadedInstance?.let { return it }
                loadInFlight = true
                try {
                    val provider = EmojibaseDataProvider(context.applicationContext)
                    provider.loadEmojiData()
                    sharedLoadedInstance = provider
                    return provider
                } finally {
                    loadInFlight = false
                }
            }
        }

        /** The shared provider if it is already loaded, else null: for UI-thread callers that must not pay for the parse. */
        @JvmStatic
        fun peekShared(): EmojibaseDataProvider? = sharedLoadedInstance

        /** Warms the shared provider on this class's low-priority executor so first use skips the parse. */
        @JvmStatic
        fun preloadAsync(context: Context) = preloadAsync(context, defaultPreloadExecutor)

        /** Warms the shared provider on [executor]; a no-op when loaded, loading or already queued. */
        @JvmStatic
        fun preloadAsync(context: Context, executor: Executor) {
            if (sharedLoadedInstance != null || loadInFlight) return
            if (!preloadQueued.compareAndSet(false, true)) return
            val appContext = context.applicationContext
            try {
                executor.execute {
                    try {
                        getSharedLoaded(appContext)
                    } finally {
                        preloadQueued.set(false)
                    }
                }
            } catch (e: RejectedExecutionException) {
                preloadQueued.set(false)
                Logger.warn(TAG, "emojibase preload rejected: $e")
            }
        }

        /**
         * Drops the shared dataset and indexes (nothing else evicts it); [getSharedLoaded] re-parses.
         * A plain volatile write, never the parse lock: called from onTrimMemory on the main thread.
         * A parse already running still publishes its result, for the caller that needed it.
         */
        @JvmStatic
        fun releaseShared() {
            sharedLoadedInstance = null
        }

        /**
         * Glyph cache key: a new OS build, dataset or [canDraw] rule invalidates it. "whole" names the
         * rule; changing the rule must change the key because the cache trusts every stored yes.
         */
        private fun glyphCacheKey(identity: String) =
            "${Build.FINGERPRINT}|${Build.VERSION.SDK_INT}|$identity|whole"

        /**
         * Whether the font draws [emoji] as itself, given [hasGlyph] (`Paint.hasGlyph`). Asks about the
         * whole emoji (parts of a missing ZWJ sequence or flag each have glyphs), then again without
         * U+FE0F, which fully-qualified forms like ✋️ carry and the font may lack.
         */
        @JvmStatic
        internal fun canDraw(emoji: String, hasGlyph: (String) -> Boolean): Boolean {
            if (hasGlyph(emoji)) return true
            if (emoji.indexOf('\uFE0F') < 0) return false
            val bare = emoji.replace("\uFE0F", "")
            return bare.isNotEmpty() && hasGlyph(bare)
        }
    }

    private var emojiList: List<EmojiData>? = null

    /** Lower-cased search fields and their bigram index, built lazily; see [searchData]. */
    @Volatile
    private var searchData: SearchData? = null
    private var emojiByCategory: MutableMap<EmojiCategory, MutableList<EmojiData>>? = null

    /** Skin-tone variants by base emoji, under both the fully-qualified and VS16-stripped forms. */
    private var skinsByEmoji: Map<String, List<String>> = emptyMap()
    private val paint = Paint()

    /** Loads and categorizes the compact asset ([EmojibaseCompactFormat]). */
    fun loadEmojiData() {
        try {
            val compact = readDataset()
            applyDataset(compact.entries) { emojis -> glyphFlags(emojis, compact.identity) }
        } catch (e: Exception) {
            // A missing or malformed asset: the board still opens, with no emoji and no search; say why.
            Logger.warn(TAG, "emojibase data failed to load: $e")
        }
    }

    internal fun readDataset(): EmojibaseCompactFormat.Parsed {
        val base = readCompact(EmojibaseCompactFormat.ASSET)
        val additions = try {
            readCompact("bbmodern/emoji-unicode-18.tsv")
        } catch (e: Exception) {
            Logger.warn(TAG, "Unicode emoji supplement failed to load: $e")
            return base
        }
        val entries = base.entries.toMutableList()
        val indices = base.entries.withIndex().associate { (index, entry) ->
            entry.emoji.replace("\uFE0F", "") to index
        }
        for (entry in additions.entries) {
            val index = indices[entry.emoji.replace("\uFE0F", "")]
            if (index == null) {
                entries.add(entry)
            } else if (entry.skins != null) {
                val previous = entries[index]
                entries[index] = previous.copy(
                    skins = (previous.skins.orEmpty() + entry.skins).distinctBy { it.emoji.replace("\uFE0F", "") },
                )
            }
        }
        return EmojibaseCompactFormat.Parsed(entries, "${base.identity}|${additions.identity}")
    }

    private fun readCompact(asset: String): EmojibaseCompactFormat.Parsed =
        context.assets.open(asset).bufferedReader(Charsets.UTF_8).use(EmojibaseCompactFormat::read)

    /**
     * Builds the category and skin-tone indexes. [drawable] (tests pass their own) is asked once, about
     * the base emoji then every variant; undrawable emoji leave the board and search, undrawable
     * variants leave the popup.
     */
    internal fun applyDataset(allEmojis: List<EmojiData>, drawable: (List<String>) -> BooleanArray) {
        val baseEmojis = allEmojis.filter { isBaseEmoji(it) }

        val variantList = ArrayList<String>()
        val seenVariants = HashSet<String>()
        for (e in allEmojis) {
            e.skins?.forEach { if (seenVariants.add(it.emoji)) variantList.add(it.emoji) }
        }
        val flags = drawable(baseEmojis.map { it.emoji } + variantList)

        val supportedEmojis = baseEmojis.filterIndexed { i, _ -> flags[i] }
        val drawableVariants = HashSet<String>(variantList.size * 2)
        variantList.forEachIndexed { i, variant -> if (flags[baseEmojis.size + i]) drawableVariants.add(variant) }

        val categoryMap = supportedEmojis.groupBy { it.category }
            .mapValues { it.value.toMutableList() }
            .toMutableMap()

        emojiByCategory = categoryMap
        emojiList = null
        searchData = null

        val skins = HashMap<String, List<String>>(allEmojis.size * 2)
        for (e in allEmojis) {
            val variants = e.skins?.map { it.emoji }?.filter { it in drawableVariants } ?: continue
            if (variants.isEmpty()) continue
            // The stripped form never displaces an entry whose own form is that exact string.
            val stripped = stripVariationSelectors(e.emoji)
            if (stripped != null && !skins.containsKey(stripped)) {
                skins[stripped] = variants
            }
            skins[e.emoji] = variants
        }
        skinsByEmoji = skins
    }

    /** [isEmojiSupported] for each emoji, through the on-disk glyph cache. */
    private fun glyphFlags(emojis: List<String>, identity: String): BooleanArray {
        // No cache dir (e.g. credential storage still locked) only means no cache, never no emoji.
        val dir = try { context.cacheDir } catch (e: Exception) { null }
        return EmojiGlyphCache.flags(dir, glyphCacheKey(identity), emojis, ::isEmojiSupported)
    }

    /** False for variants and components: toned, gendered, directional, skin/hair components, regional indicators. */
    private fun isBaseEmoji(emoji: EmojiData): Boolean {
        if (emoji.tone != null) {
            return false
        }

        // Components (group < 0).
        if (emoji.group < 0) {
            return false
        }

        if (emoji.gender != null) {
            return false
        }

        val hexcode = emoji.codepoints
        val desc = emoji.description.lowercase()

        // Skin tone modifiers 1F3FB..1F3FF.
        if (hexcode.length == 5 && hexcode >= "1F3FB" && hexcode <= "1F3FF") {
            return false
        }

        // Hair components 1F9B0..1F9B3.
        if (hexcode.startsWith("1F9B") && hexcode.length == 5) {
            val lastChar = hexcode.last()
            if (lastChar in '0'..'3') {
                return false
            }
        }

        if (hexcode.contains("200D") && (desc.contains("facing right") ||
            desc.contains("facing left") ||
            desc.contains("rightward") ||
            desc.contains("leftward"))) {
            return false
        }

        if (desc.startsWith("regional indicator")) {
            return false
        }

        if (desc.endsWith("skin tone") || desc == "bald") {
            return false
        }

        return true
    }

    /** Whether this device's font can draw [emoji]: see [canDraw]. */
    private fun isEmojiSupported(emoji: String): Boolean = try {
        canDraw(emoji, paint::hasGlyph)
    } catch (e: Exception) {
        false
    }

    /** The drawable skin-tone variants of a base emoji; empty means no popup. */
    fun getSkinToneVariants(baseEmoji: String): List<String> {
        // Callers may hold an unqualified form (a bare U+1F44D in custom symbol prefs), so fall
        // back to the VS16-stripped key.
        skinsByEmoji[baseEmoji]?.let { return it }
        val stripped = stripVariationSelectors(baseEmoji) ?: return emptyList()
        return skinsByEmoji[stripped] ?: emptyList()
    }

    private fun stripVariationSelectors(s: String?): String? = s?.replace("\uFE0F", "")

    fun getEmojisByCategory(category: EmojiCategory): List<EmojiData> {
        return emojiByCategory?.get(category) ?: emptyList()
    }

    fun getAllEmojis(): List<EmojiData> {
        if (emojiList == null) {
            emojiList = emojiByCategory?.values?.flatten() ?: emptyList()
        }
        return emojiList ?: emptyList()
    }

    /**
     * Searches description, tags, shortcodes and Russian annotations; up to MAX_SEARCH_RESULTS by
     * relevance (0 = no match), ties in dataset order. Thread-safe.
     */
    fun searchEmojis(query: String): List<EmojiData> {
        val lowerQuery = query.trim().lowercase()
        if (lowerQuery.isEmpty()) return emptyList()

        val data = searchData()
        val scored = ArrayList<Pair<SearchEntry, Int>>()
        data.forEachCandidate(lowerQuery) { entry ->
            val score = EmojiSearchScoring.score(entry.description, entry.tags, entry.aliases, lowerQuery)
            if (score > 0) scored.add(entry to score)
        }
        return scored
            .sortedByDescending { it.second }
            .take(MAX_SEARCH_RESULTS)
            .map { it.first.data }
    }

    /**
     * Best match for [query] scoring at least [minRelevance], or null; the suggestion strip's hit. Runs per
     * suggestion request: scores only bigram-index candidates and memoizes recent answers, keyed on the
     * query alone (the strip applies the skin tone afterwards).
     */
    fun topHit(query: String, minRelevance: Int): EmojiData? {
        val lowerQuery = query.trim().lowercase()
        if (lowerQuery.isEmpty()) return null
        val data = searchData()
        val memoKey = "$minRelevance\u0000$lowerQuery"
        data.memoGet(memoKey)?.let { return it.hit }

        var best: SearchEntry? = null
        var bestScore = minRelevance - 1
        val visit: (SearchEntry) -> Unit = { entry ->
            val score = EmojiSearchScoring.score(entry.description, entry.tags, entry.aliases, lowerQuery)
            if (score > bestScore) {
                bestScore = score
                best = entry
            }
        }
        // With minRelevance <= 0 a zero score (no match at all) can win, and the index only
        // knows about matches, so that case keeps the full scan.
        if (minRelevance > 0) data.forEachCandidate(lowerQuery, visit) else data.entries.forEach(visit)
        val hit = best?.data
        data.memoPut(memoKey, Memo(hit))
        return hit
    }

    /** The search fields lower-cased once per dataset, since the strip searches on every keystroke. */
    private fun searchData(): SearchData =
        searchData ?: SearchData(getAllEmojis().map { SearchEntry.of(it, annotationsFor(it.emoji)) }).also { searchData = it }

    private val russianAnnotations: Map<String, List<String>> by lazy { loadRussianAnnotations() }

    /**
     * Russian words for [emoji]. CLDR rows in `emoji-search.tsv` omit U+FE0F; emojibase stores the
     * fully-qualified form, so a miss on the raw key tries the selector-stripped one.
     */
    private fun annotationsFor(emoji: String): List<String>? {
        russianAnnotations[emoji]?.let { return it }
        val stripped = stripVariationSelectors(emoji)
        if (stripped == null || stripped == emoji) return null
        return russianAnnotations[stripped]
    }

    private fun loadRussianAnnotations(): Map<String, List<String>> {
        val map = HashMap<String, List<String>>()
        try {
            context.assets.open("bbmodern/emoji-search.tsv").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split('\t')
                    if (parts.size < 2) return@forEach
                    val words = LinkedHashSet<String>()
                    // The Russian short name is one alias when it has no Latin letters or digits, so
                    // "большой палец вверх" matches whole. A mixed name ("значок SOS") is not kept
                    // whole: the Latin piece would make a query like "s" hit it. Tokens below still
                    // match single Russian words. English-only tokens are skipped.
                    val label = parts[1].trim().lowercase()
                    if (label.length >= 2 && isRussianAlias(label) && label.none { it in 'a'..'z' || it in '0'..'9' }) {
                        words.add(label)
                    }
                    parts.drop(1).forEach { field ->
                        field.split('|', ' ').forEach { token ->
                            val word = token.trim().lowercase()
                            if (word.length >= 2 && isRussianAlias(word)) words.add(word)
                        }
                    }
                    if (words.isEmpty()) return@forEach
                    val list = words.toList()
                    map[parts[0]] = list
                    val stripped = stripVariationSelectors(parts[0])
                    if (stripped != null && stripped != parts[0] && !map.containsKey(stripped)) {
                        map[stripped] = list
                    }
                }
            }
        } catch (ignored: Exception) {
            // The annotation asset is optional; English emojibase search still works.
        }
        return map
    }

    private fun isRussianAlias(word: String): Boolean = word.any { it in 'а'..'я' || it == 'ё' }

    /** The score [searchEmojis] and [topHit] use, including Russian annotations. For the index test. */
    internal fun indexedScore(data: EmojiData, query: String): Int {
        val lowerQuery = query.trim().lowercase()
        if (lowerQuery.isEmpty()) return 0
        return EmojiSearchScoring.score(
            data.description.lowercase(),
            data.tags?.map { it.lowercase() } ?: emptyList(),
            (data.aliases?.map { it.lowercase() } ?: emptyList()) + (annotationsFor(data.emoji) ?: emptyList()),
            lowerQuery
        )
    }

    private class SearchEntry(
        val data: EmojiData,
        val description: String,
        val tags: List<String>,
        val aliases: List<String>
    ) {
        companion object {
            fun of(data: EmojiData, extraAliases: List<String>? = null) = SearchEntry(
                data,
                data.description.lowercase(),
                data.tags?.map { it.lowercase() } ?: emptyList(),
                (data.aliases?.map { it.lowercase() } ?: emptyList()) + (extraAliases ?: emptyList())
            )
        }
    }

    private class Memo(val hit: EmojiData?)

    /**
     * Search entries plus a bigram index. A non-zero score needs the query as a substring of some field,
     * so scoring only the shortest posting list among the query's bigrams skips exactly the zero scorers,
     * in dataset order. (A word-prefix index would not be exact: "ar" scores 40 on a "starry" tag.)
     */
    private class SearchData(entryList: List<SearchEntry>) {
        val entries: Array<SearchEntry> = entryList.toTypedArray()
        private val bigrams: Map<Int, IntArray>

        private val memo = object : LinkedHashMap<String, Memo>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Memo>?) = size > MEMO_SIZE
        }

        init {
            val building = HashMap<Int, IntList>(4096)
            for (i in entries.indices) {
                val e = entries[i]
                addBigrams(building, e.description, i)
                e.tags.forEach { addBigrams(building, it, i) }
                e.aliases.forEach { addBigrams(building, it, i) }
            }
            bigrams = building.mapValuesTo(HashMap(building.size * 2)) { it.value.toArray() }
        }

        /** Calls [action] on every entry that can score above 0 for [lowerQuery], in dataset order. */
        inline fun forEachCandidate(lowerQuery: String, action: (SearchEntry) -> Unit) {
            if (lowerQuery.length < 2) {
                entries.forEach(action)
                return
            }
            var shortest: IntArray? = null
            for (j in 0 until lowerQuery.length - 1) {
                val postings = bigrams[key(lowerQuery[j], lowerQuery[j + 1])] ?: return  // nothing contains it
                if (shortest == null || postings.size < shortest.size) shortest = postings
            }
            for (i in shortest!!) action(entries[i])
        }

        fun memoGet(key: String): Memo? = synchronized(memo) { memo[key] }

        fun memoPut(key: String, value: Memo) {
            synchronized(memo) { memo[key] = value }
        }

        private fun addBigrams(into: HashMap<Int, IntList>, field: String, index: Int) {
            for (j in 0 until field.length - 1) {
                into.getOrPut(key(field[j], field[j + 1])) { IntList() }.addOnce(index)
            }
        }

        companion object {
            /** A handful covers the composing word, its retries and a backspace or two. */
            const val MEMO_SIZE = 16

            fun key(a: Char, b: Char): Int = (a.code shl 16) or b.code
        }
    }

    /** An append-only int list that skips a repeat of its last value (indexes arrive in order). */
    private class IntList {
        private var items = IntArray(4)
        private var size = 0

        fun addOnce(value: Int) {
            if (size > 0 && items[size - 1] == value) return
            if (size == items.size) items = items.copyOf(size * 2)
            items[size++] = value
        }

        fun toArray(): IntArray = items.copyOf(size)
    }
}

/**
 * The relevance scale shared by [EmojibaseDataProvider.searchEmojis] and [EmojiData.searchRelevance]:
 * description exact 100, prefix 50, contains 25; tag exact 40, prefix 20, contains 10; alias exact
 * 30, contains 5. Zero is exactly "no match". Inputs are already lower-cased.
 */
object EmojiSearchScoring {
    /**
     * What a strip hit needs: an exact or prefix match on the description or a tag.
     * An exact Russian alias scores 30, so a purely Russian query does not qualify alone.
     */
    const val STRIP_MIN_RELEVANCE = 40

    @JvmStatic
    fun score(description: String, tags: List<String>, aliases: List<String>, lowerQuery: String): Int {
        var score = 0
        if (description == lowerQuery) score += 100
        else if (description.startsWith(lowerQuery)) score += 50
        else if (description.contains(lowerQuery)) score += 25

        if (tags.any { it == lowerQuery }) score += 40
        else if (tags.any { it.startsWith(lowerQuery) }) score += 20
        else if (tags.any { it.contains(lowerQuery) }) score += 10

        if (aliases.any { it == lowerQuery }) score += 30
        else if (aliases.any { it.contains(lowerQuery) }) score += 5
        return score
    }
}
