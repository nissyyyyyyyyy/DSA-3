package spellcheck;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SpellChecker - the spelling pipeline (it does NOT do KMP / Aho-Corasick).
 *
 *   1. Tokenize            split the text into words
 *   2. Dictionary lookup   Trie: is the word in the dictionary?     -> error DETECTION
 *   3. Suggestions         SuggestionEngine (edit distance)         -> error CORRECTION
 *   4. Corrected output    replace each error by its best suggestion
 *
 * IMPORTANT (say this in the viva): a word that is missing from the dictionary is only a
 * POTENTIAL error. Names ("Navya"), technical terms and new words may be valid but absent.
 * Also, a real word used wrongly ("their" instead of "there") is NOT detected, because a
 * dictionary lookup only checks that the word exists, not that it fits the sentence.
 */
public class SpellChecker {

    // A word = one or more letters. Digits, spaces and punctuation separate words.
    private static final Pattern WORD_PATTERN = Pattern.compile("\\p{L}+");

    /** How many suggestions we keep for every misspelled word. */
    public static final int TOP_N = 3;

    private final Trie trie = new Trie();
    private final SuggestionEngine suggestionEngine;

    /** Builds the Trie and the suggestion engine from the loaded dictionary words. */
    public SpellChecker(List<String> dictionaryWords, List<String> commonWords) {
        for (String word : dictionaryWords) {
            trie.insert(word);
        }
        suggestionEngine = new SuggestionEngine(dictionaryWords, commonWords);
    }

    /** Number of distinct words in the dictionary (taken from the Trie, not assumed). */
    public int getDictionarySize() {
        return trie.getWordCount();
    }

    /** Dictionary lookup only (case-insensitive). Time: O(L). Used by the Evaluator. */
    public boolean isCorrect(String word) {
        return trie.search(word.toLowerCase());
    }

    /**
     * Word is flagged as a potential error if:
     *   (a) it is NOT in the dictionary, OR
     *   (b) it IS in the dictionary but is a short (max 4 letters), uncommon word that is one
     *       edit away from a common word. The big dictionary contains junk entries like
     *       "wat", "yoe", "nama"; rule (b) catches those.
     */
    public boolean isPotentialError(String word) {
        String w = word.toLowerCase();
        if (!trie.search(w)) {
            return true;
        }
        return w.length() <= 4 && !suggestionEngine.isCommon(w) && suggestionEngine.isNearCommonWord(w);
    }

    /**
     * Suggestions for ONE word, in three steps:
     *   1. STRETCHED WORDS: "helloooo" has a letter repeated 3+ times. We first squeeze the
     *      repeated letters (to 2, then to 1) and search with that shorter word.
     *   2. NORMAL SEARCH: dictionary words within 2 edits.
     *   3. WIDER SEARCH: if still nothing, allow up to 3 edits (badly mistyped words).
     */
    public List<SuggestionEngine.Suggestion> suggest(String word, boolean useFiltering) {
        String w = word.toLowerCase();
        List<SuggestionEngine.Suggestion> found;

        if (hasLongRun(w)) {
            // Collect suggestions for both squeezed versions, keep the smallest distance per word.
            Map<String, SuggestionEngine.Suggestion> merged = new HashMap<>();
            for (int maxRun = 2; maxRun >= 1; maxRun--) {
                for (SuggestionEngine.Suggestion sg : suggestionEngine.suggest(squeezeRuns(w, maxRun), 10, useFiltering)) {
                    SuggestionEngine.Suggestion old = merged.get(sg.word);
                    if (old == null || sg.distance < old.distance) {
                        merged.put(sg.word, sg);
                    }
                }
            }
            if (!merged.isEmpty()) {
                List<SuggestionEngine.Suggestion> list = new ArrayList<>(merged.values());
                // Common English words first, then smaller distance, then alphabetical.
                list.sort(java.util.Comparator
                        .comparing((SuggestionEngine.Suggestion sg) -> !suggestionEngine.isCommon(sg.word))
                        .thenComparingInt(sg -> sg.distance)
                        .thenComparing(sg -> sg.word));
                return new ArrayList<>(list.subList(0, Math.min(TOP_N, list.size())));
            }
        }
        found = suggestionEngine.suggest(w, TOP_N, useFiltering);
        if (found.isEmpty()) {
            // Wider search (3 edits) - only trust COMMON words here, so nonsense gets no fake match.
            found = new ArrayList<>();
            for (SuggestionEngine.Suggestion sg : suggestionEngine.suggest(w, 30, useFiltering, SuggestionEngine.MAX_DISTANCE + 1)) {
                if (suggestionEngine.isCommon(sg.word) && found.size() < TOP_N) {
                    found.add(sg);
                }
            }
        }
        return found;
    }

    /** True if some letter is repeated 3 or more times in a row (e.g. "heliooo"). */
    private static boolean hasLongRun(String word) {
        int run = 1;
        for (int i = 1; i < word.length(); i++) {
            run = (word.charAt(i) == word.charAt(i - 1)) ? run + 1 : 1;
            if (run >= 3) {
                return true;
            }
        }
        return false;
    }

    /** Keeps at most 'maxRun' copies of any repeated letter: squeezeRuns("heliooo", 2) = "helioo". */
    private static String squeezeRuns(String word, int maxRun) {
        StringBuilder sb = new StringBuilder();
        int run = 0;
        for (int i = 0; i < word.length(); i++) {
            run = (i > 0 && word.charAt(i) == word.charAt(i - 1)) ? run + 1 : 1;
            if (run <= maxRun) {
                sb.append(word.charAt(i));
            }
        }
        return sb.toString();
    }

    /** How many edit-distance calculations the suggestion engine has done so far. */
    public long getComparisonCount() {
        return suggestionEngine.getComparisonCount();
    }

    /** Counts potential errors in a text without making suggestions (read-only, thread-safe). */
    public int countPotentialErrors(String text) {
        Matcher matcher = WORD_PATTERN.matcher(text);
        int count = 0;
        while (matcher.find()) {
            if (isPotentialError(matcher.group())) {
                count++;
            }
        }
        return count;
    }

    /** Counts the words in a text (used by the algorithm selector to measure input size). */
    public static int countWords(String text) {
        Matcher matcher = WORD_PATTERN.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------------
    // Result classes - simple containers that hold what check() found.
    // ------------------------------------------------------------------

    /** One detected potential error, with its position in the text. */
    public static class ErrorInfo {
        public final int line;        // line number (1-based)
        public final int wordNumber;  // word number inside that line (1-based)
        public final String word;     // the word as typed
        public final List<SuggestionEngine.Suggestion> suggestions;

        public ErrorInfo(int line, int wordNumber, String word, List<SuggestionEngine.Suggestion> suggestions) {
            this.line = line;
            this.wordNumber = wordNumber;
            this.word = word;
            this.suggestions = suggestions;
        }
    }

    /** Everything check() produces for one text. */
    public static class Result {
        public int totalTokens = 0;
        public int suggestionsGenerated = 0;
        public final List<ErrorInfo> errors = new ArrayList<>();
        public String correctedText = "";
    }

    /**
     * Runs the whole pipeline on a text.
     *
     * @param useFiltering passed on to the SuggestionEngine (chosen by the algorithm selector)
     */
    public Result check(String text, boolean useFiltering) {
        Result result = new Result();
        StringBuilder corrected = new StringBuilder();

        // If the same wrong word appears many times, compute its suggestions only once.
        Map<String, List<SuggestionEngine.Suggestion>> alreadySeen = new HashMap<>();

        String[] lines = text.split("\\R", -1);   // \R = any line break
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            Matcher matcher = WORD_PATTERN.matcher(line);
            int wordNumber = 0;
            int copiedUpTo = 0;    // part of the line already copied to the corrected text

            while (matcher.find()) {
                wordNumber++;
                result.totalTokens++;
                String original = matcher.group();
                String replacement = original;      // stays the same if the word is correct

                if (isPotentialError(original)) {                                // step 2: detect
                    String key = original.toLowerCase();
                    List<SuggestionEngine.Suggestion> suggestions = alreadySeen.get(key);
                    if (suggestions == null) {
                        suggestions = suggest(key, useFiltering);            // step 3: suggest
                        alreadySeen.put(key, suggestions);
                    }
                    result.errors.add(new ErrorInfo(lineIndex + 1, wordNumber, original, suggestions));
                    result.suggestionsGenerated += suggestions.size();

                    if (!suggestions.isEmpty()) {                            // step 4: correct
                        replacement = copyCapitalization(original, suggestions.get(0).word);
                    }
                }
                corrected.append(line, copiedUpTo, matcher.start()).append(replacement);
                copiedUpTo = matcher.end();
            }
            corrected.append(line.substring(copiedUpTo));   // text after the last word
            if (lineIndex < lines.length - 1) {
                corrected.append("\n");
            }
        }
        result.correctedText = corrected.toString();
        return result;
    }

    /** If the typed word started with a capital letter, give the suggestion a capital too. */
    private static String copyCapitalization(String original, String suggestion) {
        if (Character.isUpperCase(original.charAt(0))) {
            return Character.toUpperCase(suggestion.charAt(0)) + suggestion.substring(1);
        }
        return suggestion;
    }
}
