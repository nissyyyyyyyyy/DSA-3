package spellcheck;

import java.util.ArrayList;
import java.util.List;

/**
 * KMP (Knuth-Morris-Pratt) - CO2 String Algorithm.
 * Finds EVERY exact occurrence of ONE pattern inside a text.
 *
 * PROBLEM WITH THE NAIVE METHOD:
 *   When a mismatch happens, naive search goes back and re-compares letters it
 *   has already seen  ->  O(n * m) in the worst case.
 *
 * KMP'S IDEA:
 *   Never move backwards in the text. Use a helper table (called LPS or
 *   "failure" table) computed from the pattern, which says:
 *   "after a mismatch, how many letters of the pattern are already matched?"
 *
 *   LPS[i] = length of the longest PROPER PREFIX of pattern[0..i]
 *            that is also a SUFFIX of pattern[0..i].
 *
 *   Example  pattern = "ababc"
 *            index   :  0 1 2 3 4
 *            LPS     :  0 0 1 2 0
 *            (at index 3, "ab" is both prefix and suffix, so LPS = 2)
 *
 * COMPLEXITY  (n = text length, m = pattern length)
 *   Build LPS table : O(m)
 *   Search          : O(n)
 *   Total time      : O(n + m)      Extra space: O(m)
 *
 * VIVA NOTE: KMP is EXACT matching only. It cannot tell that "recieve" is a
 *            misspelling of "receive", so it is NOT used for spelling correction.
 */
public class KMP {

    /** Step 1: build the LPS (longest prefix-suffix) table of the pattern. O(m). */
    public static int[] buildLpsTable(String pattern) {
        int[] lps = new int[pattern.length()];
        int length = 0;                 // length of the current matching prefix-suffix
        int i = 1;                      // lps[0] is always 0, so start from 1

        while (i < pattern.length()) {
            if (pattern.charAt(i) == pattern.charAt(length)) {
                length++;               // one more letter matches
                lps[i] = length;
                i++;
            } else if (length > 0) {
                length = lps[length - 1];   // fall back to a shorter prefix (do NOT move i)
            } else {
                lps[i] = 0;
                i++;
            }
        }
        return lps;
    }

    /** Step 2: scan the text once. Returns the start index (0-based) of every match. O(n). */
    public static List<Integer> search(String text, String pattern) {
        List<Integer> positions = new ArrayList<>();
        if (pattern.isEmpty() || pattern.length() > text.length()) {
            return positions;
        }

        int[] lps = buildLpsTable(pattern);
        int matched = 0;                // how many pattern letters are matched right now

        for (int i = 0; i < text.length(); i++) {   // i NEVER goes backwards
            // On mismatch, use the table to shrink 'matched' instead of restarting.
            while (matched > 0 && text.charAt(i) != pattern.charAt(matched)) {
                matched = lps[matched - 1];
            }
            if (text.charAt(i) == pattern.charAt(matched)) {
                matched++;
            }
            if (matched == pattern.length()) {          // whole pattern matched
                positions.add(i - pattern.length() + 1);
                matched = lps[matched - 1];             // keep going to find overlapping matches
            }
        }
        return positions;
    }
}
