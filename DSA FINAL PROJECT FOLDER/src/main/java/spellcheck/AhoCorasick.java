package spellcheck;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Aho-Corasick automaton - MULTI-PATTERN exact matching.
 * (Kept from the reference project; comments added and a "start position" added to Match.)
 *
 * PROBLEM: search for k patterns in one text.
 *   Running KMP k times reads the text k times.
 *   Aho-Corasick reads the text ONCE, whatever the number of patterns.
 *
 * IT HAS THREE PARTS
 *   1. TRIE of all patterns          (addPattern)
 *   2. FAILURE LINKS                 (build)   -> like KMP's LPS table, but for a whole Trie
 *        fail(node) = the longest proper suffix of the node's string that is
 *                     also a prefix of some pattern (i.e. another node in the Trie).
 *        On a mismatch we jump along the failure link instead of restarting.
 *   3. SEARCH                        (search)  -> one left-to-right pass over the text.
 *
 * OUTPUT LINKS: if the string of a node ends a pattern, or the string of its failure
 *   node does (e.g. patterns "he" and "she"), we report both. Here we simply copy the
 *   failure node's outputs into the node while building.
 *
 * COMPLEXITY (n = text length, m = total length of all patterns, z = number of matches)
 *   Build trie + failure links : O(m)      (with a fixed alphabet)
 *   Search                     : O(n + z)
 *   Space                      : O(m)
 *
 * KMP vs AHO-CORASICK
 *   KMP           -> ONE pattern,  needs a table (array) of size m.
 *   Aho-Corasick  -> MANY patterns at once, needs a Trie with failure links.
 *   Aho-Corasick is a generalisation of KMP to many patterns.
 *
 * VIVA NOTE: Aho-Corasick only finds EXACT occurrences of known patterns.
 *   A misspelled word like "recieve" is not a known pattern, so it cannot find or
 *   fix it. Spelling correction needs edit distance (SuggestionEngine).
 *   It is also not "always the best": for ONE pattern, KMP is simpler and enough.
 */
public class AhoCorasick {

    /** A node of the Trie. */
    private static class Node {
        Map<Character, Node> children = new HashMap<>();
        Node fail;                                   // failure link
        List<String> outputs = new ArrayList<>();   // patterns that end at this node
    }

    /** One match found in the text. */
    public static class Match {
        public final int start;      // index where the match begins (0-based)
        public final String pattern; // which pattern matched

        public Match(int start, String pattern) {
            this.start = start;
            this.pattern = pattern;
        }
    }

    private final Node root = new Node();
    private int nodeCount = 1;       // the root counts as one node
    private boolean built = false;

    /** Part 1: insert one pattern into the Trie. O(length of pattern). */
    public void addPattern(String pattern) {
        Node current = root;
        for (int i = 0; i < pattern.length(); i++) {
            char letter = pattern.charAt(i);
            Node next = current.children.get(letter);
            if (next == null) {
                next = new Node();
                current.children.put(letter, next);
                nodeCount++;
            }
            current = next;
        }
        current.outputs.add(pattern);   // a pattern ends at this node
    }

    /**
     * Part 2: build failure links using BFS (level by level).
     * BFS guarantees that a node's failure node (which is shallower) is already finished.
     */
    public void build() {
        root.fail = root;
        Queue<Node> queue = new ArrayDeque<>();

        // Depth-1 nodes: their failure link is always the root.
        for (Node child : root.children.values()) {
            child.fail = root;
            queue.add(child);
        }

        while (!queue.isEmpty()) {
            Node current = queue.poll();

            for (Map.Entry<Character, Node> entry : current.children.entrySet()) {
                char letter = entry.getKey();
                Node child = entry.getValue();
                queue.add(child);

                // Follow failure links of 'current' until some node has a child for 'letter'
                // (or we reach the root).
                Node failNode = current.fail;
                while (failNode != root && !failNode.children.containsKey(letter)) {
                    failNode = failNode.fail;
                }
                child.fail = failNode.children.getOrDefault(letter, root);

                // Output link: also report the patterns that end at the failure node.
                child.outputs.addAll(child.fail.outputs);
            }
        }
        built = true;
    }

    /** Part 3: scan the text ONCE and report every occurrence of every pattern. O(n + z). */
    public List<Match> search(String text) {
        if (!built) {
            build();
        }
        List<Match> matches = new ArrayList<>();
        Node current = root;

        for (int i = 0; i < text.length(); i++) {
            char letter = text.charAt(i);

            // Mismatch -> follow failure links (never move backwards in the text).
            while (current != root && !current.children.containsKey(letter)) {
                current = current.fail;
            }
            current = current.children.getOrDefault(letter, root);

            // Every pattern stored at this node ends at position i.
            for (String pattern : current.outputs) {
                matches.add(new Match(i - pattern.length() + 1, pattern));
            }
        }
        return matches;
    }

    /** Number of Trie nodes (shows the size of the automaton). */
    public int getNodeCount() {
        return nodeCount;
    }
}
