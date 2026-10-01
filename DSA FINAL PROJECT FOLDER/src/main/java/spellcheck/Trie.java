package spellcheck;

import java.util.HashMap;
import java.util.Map;

/**
 * Trie (prefix tree) - used for DICTIONARY LOOKUP.
 *
 * (Kept from the reference project; only the unused startsWith() was removed.)
 *
 * IDEA: every dictionary word is stored letter by letter along a path.
 *       Words that share a prefix ("car", "card", "care") share the same path.
 *
 *            root
 *             |
 *             c --- a --- r*  --- d*      (* = a complete word ends here)
 *                          \----- e*
 *
 * WHY A TRIE?
 *   - Looking up a word takes O(L) time, where L = length of the word.
 *     It does NOT depend on the number of words in the dictionary.
 *   - Space: O(total letters stored) - shared prefixes are stored only once.
 *
 * VIVA NOTE: a Trie only answers "is this word in the dictionary?".
 *            It does NOT correct spelling. Correction is done by SuggestionEngine.
 */
public class Trie {

    /** One node = one letter position. It remembers its children and whether a word ends here. */
    private static class Node {
        Map<Character, Node> children = new HashMap<>();
        boolean isEndOfWord = false;
    }

    private final Node root = new Node();
    private int wordCount = 0;

    /** Adds a word to the Trie. Time: O(L). */
    public void insert(String word) {
        Node current = root;
        for (int i = 0; i < word.length(); i++) {
            char letter = word.charAt(i);
            // If there is no child for this letter yet, create one; then move down.
            current = current.children.computeIfAbsent(letter, c -> new Node());
        }
        if (!current.isEndOfWord) {
            wordCount++;               // count each distinct word only once
        }
        current.isEndOfWord = true;
    }

    /** Returns true only if the exact word is stored. Time: O(L). */
    public boolean search(String word) {
        Node current = root;
        for (int i = 0; i < word.length(); i++) {
            current = current.children.get(word.charAt(i));
            if (current == null) {
                return false;          // path breaks -> word is not in the dictionary
            }
        }
        return current.isEndOfWord;    // path exists, but a word must END here
    }

    /** Number of distinct words stored. */
    public int getWordCount() {
        return wordCount;
    }
}
