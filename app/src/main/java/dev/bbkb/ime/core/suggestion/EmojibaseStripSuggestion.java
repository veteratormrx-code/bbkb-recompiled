package dev.bbkb.ime.core.suggestion;

import android.content.Context;

import java.util.ArrayList;
import java.util.Locale;

import dev.bbkb.ime.core.engine.Dictionary;
import dev.bbkb.ime.core.locale.LocaleUtils;
import dev.bbkb.ime.core.settings.PrefsManager;
import dev.bbkb.ime.core.settings.util.SettingsManager;
import dev.bbkb.ime.core.settings.util.SettingsValues;
import dev.bbkb.ime.keyboard.inputboard.emoji.EmojiData;
import dev.bbkb.ime.keyboard.inputboard.emoji.EmojiSearchScoring;
import dev.bbkb.ime.keyboard.inputboard.emoji.EmojiToneMemory;
import dev.bbkb.ime.keyboard.inputboard.emoji.EmojibaseDataProvider;

/**
 * The strip's emoji suggestion, from emojibase instead of the engine. The engine's own emoji
 * entries are dropped; with the Emoji suggestions switch on, the top emojibase match for the
 * composing word (2+ chars) is appended with the remembered skin tone. The strip's emoji slot and
 * the period key's flick suggestion both read it. Runs on the suggestion thread; the first call
 * parses emojibase unless {@link EmojibaseDataProvider#preloadAsync} ran.
 */
public final class EmojibaseStripSuggestion {

    /** The suggestion kind the engine used for its emoji predictions; kept for the strip and the period key. */
    public static final int KIND_EMOJI = 11;

    /** Shorter words match too much of the dictionary to be worth an emoji. */
    public static final int MIN_WORD_LENGTH = 2;

    private EmojibaseStripSuggestion() {
    }

    /**
     * Drops the engine's emoji entries and appends the emojibase hit for {@code composingText}.
     *
     * @param locale the suggestion locale; no hit is added for Chinese or Japanese, whose pick path
     *               reads the engine's own word info, which an emojibase entry does not carry
     */
    public static void apply(Context context, ArrayList<SuggestedWords.SuggestedWordInfo> suggestions,
            String composingText, Locale locale) {
        apply(context, suggestions, composingText, null, locale);
    }

    /**
     * Same as {@link #apply(Context, ArrayList, String, Locale)}, with {@code textBeforeCursor}
     * for the word that a trailing space just committed. Composing is empty then; the emoji stays
     * the one that word had while it was being typed.
     */
    public static void apply(Context context, ArrayList<SuggestedWords.SuggestedWordInfo> suggestions,
            String composingText, String textBeforeCursor, Locale locale) {
        SettingsValues settings = SettingsManager.getInstance().getSettingsValues();
        if (settings == null || !settings.isEmojiPredictionsEnabled) {
            dropEngineEmoji(suggestions);
            return;
        }
        String hit = topHit(context, composingText, textBeforeCursor, locale);
        if (hit != null) {
            // A dictionary emoji replaces the engine's. With no match the engine's own emoji
            // stays: that is the hint drawn above the period, the same way a word sits on a letter.
            dropEngineEmoji(suggestions);
            suggestions.add(emojiEntry(hit));
        }
    }

    /** Removes every kind-{@value #KIND_EMOJI} entry, in place. */
    public static void dropEngineEmoji(ArrayList<SuggestedWords.SuggestedWordInfo> suggestions) {
        for (int i = suggestions.size() - 1; i >= 0; i--) {
            if (suggestions.get(i).isKind(KIND_EMOJI)) {
                suggestions.remove(i);
            }
        }
    }

    /**
     * The toned emoji for {@code composingText}, or null for a short word, Chinese/Japanese, or no
     * match above {@link EmojiSearchScoring#STRIP_MIN_RELEVANCE}.
     */
    public static String topHit(Context context, String composingText, Locale locale) {
        return topHit(context, composingText, null, locale);
    }

    /**
     * The toned emoji for the word being typed, or for the word a trailing space just finished
     * when composing is empty. Null for a short word, Chinese/Japanese, or no match above
     * {@link EmojiSearchScoring#STRIP_MIN_RELEVANCE}.
     */
    public static String topHit(Context context, String composingText, String textBeforeCursor, Locale locale) {
        if (locale != null && LocaleUtils.isChineseOrJapanese(locale)) {
            return null;
        }
        String text = wordForEmoji(composingText, textBeforeCursor);
        String hit = lookup(context, text);
        if (hit != null) {
            return hit;
        }
        // "the cat" and "я кот" are the last word. "smile 4" already matched as a phrase above.
        String last = wordIgnoringSpaceBefore(text);
        if (last.equals(text)) {
            return null;
        }
        return lookup(context, last);
    }

    private static String lookup(Context context, String word) {
        if (word.length() < MIN_WORD_LENGTH) {
            return null;
        }
        EmojibaseDataProvider provider = EmojibaseDataProvider.getSharedLoaded(context);
        EmojiData hit = provider.topHit(word, EmojiSearchScoring.STRIP_MIN_RELEVANCE);
        if (hit == null) {
            return null;
        }
        String preferred = EmojiToneMemory.preferred(PrefsManager.INSTANCE.getPrefs(context), hit.getEmoji());
        return preferred != null ? preferred : hit.getEmoji();
    }

    /**
     * Text the emoji is chosen from. Spaces at the edges are not part of it. Once a space
     * commits the word, composing is empty and the text is what stands before the cursor.
     * A new composing word, however short, replaces that finished word.
     */
    static String wordForEmoji(String composingText, String textBeforeCursor) {
        String word = trimWordSpaces(composingText);
        if (!word.isEmpty()) {
            return word;
        }
        // The word was committed. Keeping its emoji is its own suggestion setting.
        if (!keepsEmojiAfterSpace()) {
            return "";
        }
        return trimWordSpaces(textBeforeCursor);
    }

    /** On unless Suggestions turns "Emoji after a space" off. A missing settings object stays on. */
    private static boolean keepsEmojiAfterSpace() {
        SettingsValues settings = SettingsManager.getInstance().getSettingsValues();
        return settings == null || settings.isEmojiAfterSpaceEnabled;
    }

    /** Drops spaces at the edges. Spaces inside the text stay, so "smile 4" is still one query. */
    private static String trimWordSpaces(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int start = 0;
        int end = text.length();
        while (start < end && isWordSpace(text.charAt(start))) {
            start++;
        }
        while (end > start && isWordSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    /** The last word in {@code text}. Spaces around it are not part of the word. */
    static String wordIgnoringSpaceBefore(String composingText) {
        if (composingText == null) {
            return "";
        }
        int end = composingText.length();
        while (end > 0 && isWordSpace(composingText.charAt(end - 1))) {
            end--;
        }
        int start = end;
        while (start > 0 && !isWordSpace(composingText.charAt(start - 1))) {
            start--;
        }
        return composingText.substring(start, end);
    }

    private static boolean isWordSpace(char c) {
        return c == ' ' || c == '\u00A0' || c == '\u202F';
    }

    /** A kind-{@value #KIND_EMOJI} entry for {@code emoji}, with no engine word info behind it. */
    public static SuggestedWords.SuggestedWordInfo emojiEntry(String emoji) {
        return new SuggestedWords.SuggestedWordInfo(emoji, 0, KIND_EMOJI, Dictionary.DICTIONARY_HARDCODED, -1, -1, null);
    }
}
