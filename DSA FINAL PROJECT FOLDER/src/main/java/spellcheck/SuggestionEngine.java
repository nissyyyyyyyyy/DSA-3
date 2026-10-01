package spellcheck;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SuggestionEngine - finds the dictionary words that are CLOSEST to a misspelled word.
 * (Based on the reference project's Correction.java, simplified.)
 *
 * THREE IDEAS
 *
 * 1. EDIT DISTANCE (dynamic programming)
 *    The minimum number of edits needed to turn word A into word B.
 *    Allowed edits (each costs 1):
 *        insert a letter      "mesage"  -> "message"   (insert 's')
 *        delete a letter      "helllo"  -> "hello"     (delete 'l')
 *        substitute a letter  "cet"     -> "cat"       (e -> a)
 *        swap two neighbours  "recieve" -> "receive"   (ie -> ei)   <- Damerau extension
 *    Smaller distance = more similar words.
 *
 * 2. CANDIDATE FILTERING (optional, chosen by the algorithm selector)
 *    Comparing with all ~370,000 words is wasteful. If two words differ in length by more
 *    than MAX_DISTANCE, their edit distance is MORE than MAX_DISTANCE (each edit changes
 *    the length by at most 1). So we can safely skip them. The filter never loses a
 *    valid suggestion - it only saves time.
 *
 * 3. SOUNDEX (tie-breaker only)
 *    Many words have the same edit distance (e.g. "mesage" is 1 edit from "message" AND
 *    from "menage"). Soundex turns a word into a code of how it SOUNDS, so between equal
 *    distances we prefer the word that sounds like the typed word ("message" = M220 = "mesage").
 *    Soundex never overrides edit distance - it is used only when distances are equal.
 */
public class SuggestionEngine {

    /** We only suggest words that are at most this many edits away. */
    public static final int MAX_DISTANCE = 2;

    /** One suggested word and its edit distance from the misspelled word. */
    public static class Suggestion {
        public final String word;
        public final int distance;
        public final boolean soundsAlike;   // same Soundex code as the typed word?

        public Suggestion(String word, int distance, boolean soundsAlike) {
            this.word = word;
            this.distance = distance;
            this.soundsAlike = soundsAlike;
        }
    }

    private final List<String> allWords;
    // Common English words: word -> position in the list (0 = most common). Used to rank suggestions.
    private final Map<String, Integer> commonRank = new HashMap<>();
    // Dictionary grouped by word length: length -> list of words with that length.
    private final Map<Integer, List<String>> wordsByLength = new HashMap<>();
    // Total number of edit-distance calculations done so far (lets us SHOW what filtering saves).
    private long comparisonCount = 0;

    public SuggestionEngine(List<String> dictionaryWords, List<String> commonWords) {
        this.allWords = dictionaryWords;
        for (int i = 0; i < commonWords.size(); i++) {
            commonRank.put(commonWords.get(i), i);
        }
        for (String word : dictionaryWords) {
            wordsByLength.computeIfAbsent(word.length(), k -> new ArrayList<>()).add(word);
        }
    }

    /**
     * Returns the best 'topN' suggestions for a misspelled word.
     *
     * @param useFiltering true  -> compare only with words whose length is within +-MAX_DISTANCE
     *                     false -> compare with EVERY dictionary word (simple, slower)
     */
    public List<Suggestion> suggest(String word, int topN, boolean useFiltering) {
        return suggest(word, topN, useFiltering, MAX_DISTANCE);
    }

    /** Same as above, but with a chosen maximum edit distance (used for the wider fallback search). */
    public List<Suggestion> suggest(String word, int topN, boolean useFiltering, int maxDistance) {
        List<String> candidates = useFiltering ? filterByLength(word, maxDistance) : allWords;

        String typedCode = soundex(word);
        List<Suggestion> found = new ArrayList<>();
        for (String candidate : candidates) {
            comparisonCount++;
            int distance = editDistance(word, candidate);
            if (distance > 0 && distance <= maxDistance) {      // distance 0 = the word itself, skip it
                found.add(new Suggestion(candidate, distance, soundex(candidate).equals(typedCode)));
            }
        }

        // Ranking: 1) smaller edit distance first
        //          2) if equal: a COMMON English word (corpus/common_words.txt) before a rare one
        //          3) then a word that is the typed word + missing letter(s) ("goin" -> "going")
        //          4) then the word that sounds like the typed word (Soundex)
        //          5) then the more common word (earlier in common_words.txt)
        //          6) then the word whose length is closest to the typed word
        //          7) then alphabetical order (so the output is always the same)
        found.sort(Comparator
                .comparingInt((Suggestion s) -> s.distance)
                .thenComparing((Suggestion s) -> !commonRank.containsKey(s.word))   // common English words first
                .thenComparing((Suggestion s) -> !containsInOrder(s.word, word))     // 'missing letter' typos first
                .thenComparing((Suggestion s) -> !s.soundsAlike)      // false (= sounds alike) sorts first
                .thenComparingInt((Suggestion s) -> commonRank.getOrDefault(s.word, Integer.MAX_VALUE)) // common words first
                .thenComparingInt(s -> Math.abs(s.word.length() - word.length()))
                .thenComparing(s -> s.word));

        return found.subList(0, Math.min(topN, found.size()));
    }

    /** True if all letters of 'small' appear in 'big' in the same order (big = small + missing letters). */
    private static boolean containsInOrder(String big, String small) {
        int j = 0;
        for (int i = 0; i < big.length() && j < small.length(); i++) {
            if (big.charAt(i) == small.charAt(j)) {
                j++;
            }
        }
        return j == small.length();
    }

    public boolean isCommon(String word) {
        return commonRank.containsKey(word);
    }

    /**
     * True if some COMMON word is exactly 1 edit away from 'word' (and 'word' is not just that
     * common word with a suffix added, like cat -> cats). Used to catch rare/junk dictionary
     * words such as "wat" (near "what") that a plain dictionary lookup would accept.
     */
    public boolean isNearCommonWord(String word) {
        for (String common : commonRank.keySet()) {
            if (Math.abs(common.length() - word.length()) <= 1
                    && !word.startsWith(common) && !common.startsWith(word)
                    && editDistance(word, common) == 1) {
                return true;
            }
        }
        return false;
    }

    /** How many edit-distance calculations have been done so far. */
    public long getComparisonCount() {
        return comparisonCount;
    }

    /** Candidate filtering: collect only dictionary words with length in [len-2, len+2]. */
    private List<String> filterByLength(String word, int maxDistance) {
        List<String> candidates = new ArrayList<>();
        for (int len = Math.max(1, word.length() - maxDistance); len <= word.length() + maxDistance; len++) {
            List<String> sameLength = wordsByLength.get(len);
            if (sameLength != null) {
                candidates.addAll(sameLength);
            }
        }
        return candidates;
    }

    /**
     * Damerau-Levenshtein edit distance using dynamic programming.
     *
     * TABLE MEANING:  d[i][j] = edit distance between the first i letters of a
     *                           and the first j letters of b.
     *
     * BASE CASES:     d[i][0] = i   (delete all i letters)
     *                 d[0][j] = j   (insert all j letters)
     *
     * RECURRENCE (each cell is built from cells already computed):
     *     d[i][j] = min( d[i-1][j]   + 1      -> delete a[i-1]
     *                    d[i][j-1]   + 1      -> insert b[j-1]
     *                    d[i-1][j-1] + cost ) -> substitute (cost = 0 if letters equal, else 1)
     *     and, if the last two letters are swapped:
     *                    d[i-2][j-2] + 1      -> transposition
     *
     * WHY DYNAMIC PROGRAMMING?  The same sub-problems (prefix pairs) are needed again and
     * again. Storing them in a table means each one is solved only once - instead of the
     * exponential time of trying every possible sequence of edits.
     *
     * COMPLEXITY (p = length of a, q = length of b):  Time O(p * q),  Space O(p * q).
     */
    public static int editDistance(String a, String b) {
        int p = a.length();
        int q = b.length();
        int[][] d = new int[p + 1][q + 1];

        for (int i = 0; i <= p; i++) d[i][0] = i;
        for (int j = 0; j <= q; j++) d[0][j] = j;

        for (int i = 1; i <= p; i++) {
            for (int j = 1; j <= q; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;

                int best = Math.min(d[i - 1][j] + 1,        // delete
                           Math.min(d[i][j - 1] + 1,        // insert
                                    d[i - 1][j - 1] + cost)); // substitute / match

                // Transposition: "ab" in one word appears as "ba" in the other.
                if (i > 1 && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    best = Math.min(best, d[i - 2][j - 2] + 1);
                }
                d[i][j] = best;
            }
        }
        return d[p][q];
    }

    /** Soundex digit of one letter. Vowels, h, w, y have no digit ('0'). */
    private static char soundexDigit(char c) {
        switch (c) {
            case 'b': case 'f': case 'p': case 'v': return '1';
            case 'c': case 'g': case 'j': case 'k': case 'q': case 's': case 'x': case 'z': return '2';
            case 'd': case 't': return '3';
            case 'l': return '4';
            case 'm': case 'n': return '5';
            case 'r': return '6';
            default: return '0';
        }
    }

    /**
     * Soundex code = first letter + up to 3 digits describing the sounds that follow.
     * Neighbouring letters with the same digit count once. Example: "recieve" and
     * "receive" both give R210, so they "sound alike".
     */
    public static String soundex(String word) {
        if (word.isEmpty()) {
            return "0000";
        }
        StringBuilder code = new StringBuilder();
        code.append(Character.toUpperCase(word.charAt(0)));
        char previous = soundexDigit(word.charAt(0));

        for (int i = 1; i < word.length() && code.length() < 4; i++) {
            char digit = soundexDigit(word.charAt(i));
            if (digit != '0' && digit != previous) {
                code.append(digit);
            }
            previous = digit;
        }
        while (code.length() < 4) {
            code.append('0');            // pad to 4 characters
        }
        return code.toString();
    }
}
