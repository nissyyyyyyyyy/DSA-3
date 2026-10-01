package spellcheck;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/**
 * Main - menu, input, algorithm selection and output.
 *
 * Run from the PROJECT ROOT (the folder that contains "corpus"):
 *     javac -d bin src/main/java/spellcheck/*.java
 *     java -cp bin spellcheck.Main
 *
 * WHO DOES WHAT
 *   Main             menu + algorithm selector + printing results
 *   DictionaryLoader reads corpus/dictionary.txt
 *   SpellChecker     tokenize -> Trie lookup -> suggestions -> corrected text
 *   SuggestionEngine edit distance (dynamic programming) + candidate filtering
 *   KMP              one exact pattern            (CO2)
 *   AhoCorasick      many exact patterns at once  (CO2 extra)
 *   Evaluator        precision / recall / F1 / correction accuracy on labelled data
 */
public class Main {

    private static final String LINE = "=".repeat(60);
    private static final String DICTIONARY_PATH = "corpus/dictionary.txt";
    private static final String TEST_DATA_PATH = "corpus/test_data.csv";
    private static final int MAX_ROWS_SHOWN = 20;   // keeps output readable for big inputs
    private static List<String> commonWords = new ArrayList<>();   // corpus/common_words.txt

    // ------------------------------------------------------------------
    // ALGORITHM SELECTOR (spelling task)
    // The choice depends on the TASK (spelling check) and the INPUT SIZE.
    //   up to 20 words   -> Single Word / Short Sentence -> simple strategy
    //   21 - 300 words   -> Paragraph                    -> with candidate filtering
    //   more than 300    -> Large Text                   -> with candidate filtering
    // Aho-Corasick and KMP are NOT chosen here: they are chosen only when the user picks
    // a pattern-matching task from the menu (options 3 and 4).
    // ------------------------------------------------------------------
    private static final int SHORT_LIMIT = 20;
    private static final int PARAGRAPH_LIMIT = 300;

    /** Holds the decision made by the selector. */
    private static class Strategy {
        final String inputType;
        final String name;
        final String reason;
        final boolean useFiltering;

        Strategy(String inputType, String name, String reason, boolean useFiltering) {
            this.inputType = inputType;
            this.name = name;
            this.reason = reason;
            this.useFiltering = useFiltering;
        }
    }

    private static Strategy selectSpellingStrategy(int wordCount) {
        if (wordCount <= 1) {
            return new Strategy("Single Word", "Simple Dictionary Lookup + Edit Distance",
                    "The input is tiny, so a straightforward check is enough. Each suspicious word is compared with every dictionary word.", false);
        }
        if (wordCount <= SHORT_LIMIT) {
            return new Strategy("Short Sentence", "Simple Dictionary Lookup + Edit Distance",
                    "The input is small, so direct dictionary checking is sufficient. No multi-pattern matching is needed.", false);
        }
        String filterReason = "The input has many words, so we skip dictionary words whose length differs by more than "
                + SuggestionEngine.MAX_DISTANCE + " (they cannot be within " + SuggestionEngine.MAX_DISTANCE + " edits).";
        if (wordCount <= PARAGRAPH_LIMIT) {
            return new Strategy("Paragraph", "Dictionary Lookup + Candidate Filtering + Edit Distance", filterReason, true);
        }
        return new Strategy("Large Text", "Dictionary Lookup + Candidate Filtering + Edit Distance",
                filterReason + " Aho-Corasick is not used: this is spelling correction, not multi-pattern searching.", true);
    }

    // ------------------------------------------------------------------
    // main(): load the dictionary once, then show the menu until the user exits.
    // ------------------------------------------------------------------
    public static void main(String[] args) throws IOException {
        System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));

        System.out.println("Loading dictionary from '" + DICTIONARY_PATH + "' ...");
        List<String> words;
        try {
            words = DictionaryLoader.load(DICTIONARY_PATH);
        } catch (IOException e) {
            System.out.println("Could not read " + DICTIONARY_PATH + " (" + e.getMessage() + ").");
            System.out.println("Run the program from the project root folder (the one that contains 'corpus').");
            return;
        }
        long start = System.nanoTime();
        try {
            commonWords = DictionaryLoader.load("corpus/common_words.txt");
        } catch (IOException e) {
            System.out.println("Warning: corpus/common_words.txt not found - suggestions will not be ranked by word frequency.");
        }
        SpellChecker checker = new SpellChecker(words, commonWords);
        System.out.printf("Dictionary words loaded: %,d   (Trie built in %.0f ms)%n", checker.getDictionarySize(), millisSince(start));

        // Quick mode:  java -cp bin spellcheck.Main "your sentence here"
        if (args.length > 0) {
            runSpellCheck(checker, String.join(" ", args));
            return;
        }

        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        while (true) {
            System.out.println();
            System.out.println("1. Spell check - type a word / sentence / paragraph");
            System.out.println("2. Spell check - text file");
            System.out.println("3. KMP           (search ONE pattern)");
            System.out.println("4. Aho-Corasick  (search MANY patterns)");
            System.out.println("5. Evaluation    (accuracy on labelled test data)");
            System.out.println("6. Run demo tests 1-7");
            System.out.println("7. Max-Flow (assign each error a different correction)");
            System.out.println("8. Vertex Cover 2-approximation");
            System.out.println("9. Random accuracy test (reservoir sampling + Monte Carlo)");
            System.out.println("10. Miller-Rabin primality test");
            System.out.println("11. Parallel reduce + prefix sum on a text");
            System.out.println("0. Exit");
            String choice = ask(scanner, "Choose an option: ");
            if (choice == null || choice.equals("0")) {
                System.out.println("Goodbye!");
                return;
            }
            switch (choice) {
                case "1": {
                    String text = ask(scanner, "Enter text: ");
                    if (text != null && !text.trim().isEmpty()) runSpellCheck(checker, text);
                    break;
                }
                case "2": {
                    String text = readFile(ask(scanner, "Path of .txt file (Enter = corpus/input_docs/doc1.txt): ", "corpus/input_docs/doc1.txt"));
                    if (text != null) runSpellCheck(checker, text);
                    break;
                }
                case "3": {
                    String text = readTextOrFile(ask(scanner, "Text (or path of a .txt file): "));
                    String pattern = ask(scanner, "Pattern to search: ");
                    if (text != null && pattern != null) runKmp(text, pattern);
                    break;
                }
                case "4": {
                    String text = readTextOrFile(ask(scanner, "Text (or path of a .txt file): "));
                    String patternLine = ask(scanner, "Patterns separated by commas: ");
                    if (text != null && patternLine != null) runAhoCorasick(text, patternLine.split(","));
                    break;
                }
                case "5":
                    runEvaluation(checker, ask(scanner, "Test file (Enter = " + TEST_DATA_PATH + "): ", TEST_DATA_PATH));
                    break;
                case "6":
                    runDemoTests(checker);
                    break;
                case "7": {
                    String text = ask(scanner, "Enter a sentence with mistakes: ");
                    if (text != null && !text.trim().isEmpty()) runMaxFlowDemo(checker, text);
                    break;
                }
                case "8":
                    runVertexCover(ask(scanner, "Edges like 0-1,1-2,2-3 (Enter = sample graph): ", "0-1,1-2,2-3,3-4,4-0,1-5,5-6"));
                    break;
                case "9":
                    runMonteCarlo(checker, ask(scanner, "Number of random test words (Enter = 40): ", "40"));
                    break;
                case "10":
                    runMillerRabin(ask(scanner, "Enter an integer (try 561, 97, 1000003): ", "561"));
                    break;
                case "11": {
                    String text = readFile(ask(scanner, "Text file (Enter = corpus/input_docs/large_text.txt): ", "corpus/input_docs/large_text.txt"));
                    if (text != null) runParallelDemo(checker, text);
                    break;
                }
                default:
                    System.out.println("Please enter a number from the menu.");
            }
        }
    }

    // ------------------------------------------------------------------
    // MODE 1: spell check
    // ------------------------------------------------------------------
    private static void runSpellCheck(SpellChecker checker, String text) {
        int wordCount = SpellChecker.countWords(text);
        Strategy strategy = selectSpellingStrategy(wordCount);       // <-- algorithm selection

        long comparisonsBefore = checker.getComparisonCount();
        long start = System.nanoTime();
        SpellChecker.Result result = checker.check(text, strategy.useFiltering);
        double elapsedMs = millisSince(start);
        long comparisons = checker.getComparisonCount() - comparisonsBefore;

        System.out.println();
        System.out.println(LINE);
        System.out.println("DICTIONARY-BASED ERROR DETECTION SYSTEM");
        System.out.println(LINE);
        System.out.printf("Dictionary Words Loaded: %,d%n%n", checker.getDictionarySize());
        System.out.println("Input Type : " + strategy.inputType);
        System.out.println("Input Size : " + wordCount + " word(s), " + text.length() + " characters");
        System.out.println("Input:");
        System.out.println(preview(text));
        System.out.println();
        System.out.println("Selected Strategy:\n  " + strategy.name);
        System.out.println("Algorithms Used:");
        System.out.println("  - Trie lookup (finds words missing from the dictionary)");
        System.out.println("  - Damerau-Levenshtein edit distance / dynamic programming (ranks suggestions)");
        System.out.println(strategy.useFiltering
                ? "  - Candidate filtering by word length (skips impossible candidates -> fewer comparisons)"
                : "  - No filtering: input is small, so each error is compared with ALL dictionary words");
        System.out.println("Reason for Selection:\n  " + strategy.reason);
        System.out.println();

        System.out.println("Detected Potential Errors: " + result.errors.size());
        for (int i = 0; i < Math.min(MAX_ROWS_SHOWN, result.errors.size()); i++) {
            SpellChecker.ErrorInfo e = result.errors.get(i);
            System.out.println("  Line " + e.line + ", Word " + e.wordNumber + ": " + e.word);
        }
        printMoreNote(result.errors.size());

        // Suggestions: show each distinct wrong word once.
        Map<String, List<SuggestionEngine.Suggestion>> distinct = new LinkedHashMap<>();
        for (SpellChecker.ErrorInfo e : result.errors) {
            distinct.putIfAbsent(e.word.toLowerCase(), e.suggestions);
        }
        System.out.println();
        System.out.println("Suggestions (best first, with edit distance):");
        int shown = 0;
        for (Map.Entry<String, List<SuggestionEngine.Suggestion>> entry : distinct.entrySet()) {
            if (shown++ == MAX_ROWS_SHOWN) break;
            System.out.println("  " + entry.getKey() + " -> " + formatSuggestions(entry.getValue()));
        }
        printMoreNote(distinct.size());

        System.out.println();
        System.out.println("Corrected Output:");
        System.out.println(preview(result.correctedText));
        System.out.println();
        double textAccuracy = result.totalTokens == 0 ? 0
                : (result.totalTokens - result.errors.size()) * 100.0 / result.totalTokens;
        System.out.println("------------------ RESULT SUMMARY ------------------");
        System.out.println("Input            : " + preview(text));
        System.out.println("Corrected Output : " + preview(result.correctedText));
        System.out.println("Algorithm Used   : " + strategy.name);
        System.out.println("Total Tokens     : " + result.totalTokens);
        System.out.println("Errors Found     : " + result.errors.size());
        System.out.println("Suggestions Made : " + result.suggestionsGenerated);
        System.out.printf("Text Accuracy    : %.1f%%  (= words accepted as correct / total words)%n", textAccuracy);
        System.out.println("Correction Accuracy : N/A (needs the correct answer to compare; see menu option 5)");
        System.out.printf("Edit-distance comparisons : %,d%n", comparisons);
        System.out.printf("Execution Time   : %.3f ms%n", elapsedMs);
        System.out.println("----------------------------------------------------");
        System.out.println("Note: a word missing from the dictionary is only a POTENTIAL error (names, technical terms).");
        System.out.println(LINE);
    }

    // ------------------------------------------------------------------
    // MODE 2: KMP (single pattern)
    // ------------------------------------------------------------------
    private static void runKmp(String text, String pattern) {
        long start = System.nanoTime();
        List<Integer> positions = KMP.search(text, pattern);
        double elapsedMs = millisSince(start);

        System.out.println();
        System.out.println(LINE);
        System.out.println("KMP - SINGLE PATTERN SEARCH");
        System.out.println(LINE);
        System.out.println("Input Size         : " + text.length() + " characters");
        System.out.println("Input Text:\n" + preview(text));
        System.out.println();
        System.out.println("Selected Algorithm : KMP");
        System.out.println("Reason for Selection:\n  The task involves searching for ONE exact pattern in the text.");
        System.out.println();
        System.out.println("Search Pattern     : " + pattern);
        if (!pattern.isEmpty() && pattern.length() <= 20) {
            System.out.println("LPS table          : " + java.util.Arrays.toString(KMP.buildLpsTable(pattern)));
        }
        if (positions.isEmpty()) {
            System.out.println("Match Result       : NOT FOUND");
            System.out.println("Match Position     : N/A");
        } else {
            System.out.println("Match Result       : FOUND (" + positions.size() + " occurrence(s))");
            System.out.println("Match Position(s)  : " + positions.subList(0, Math.min(MAX_ROWS_SHOWN, positions.size()))
                    + (positions.size() > MAX_ROWS_SHOWN ? " ..." : "") + "   (0-based start index)");
        }
        System.out.printf("Execution Time     : %.3f ms%n", elapsedMs);
        System.out.println("Note: KMP is exact, case-sensitive matching - it does not do spelling correction.");
        System.out.println(LINE);
    }

    // ------------------------------------------------------------------
    // MODE 3: Aho-Corasick (many patterns)
    // ------------------------------------------------------------------
    private static void runAhoCorasick(String text, String[] rawPatterns) {
        // Clean the pattern list: trim spaces, drop empty and duplicate patterns.
        Set<String> patterns = new LinkedHashSet<>();
        for (String p : rawPatterns) {
            if (!p.trim().isEmpty()) patterns.add(p.trim());
        }
        if (patterns.isEmpty()) {
            System.out.println("No patterns entered.");
            return;
        }

        long buildStart = System.nanoTime();
        AhoCorasick automaton = new AhoCorasick();
        for (String p : patterns) automaton.addPattern(p);   // build the Trie
        automaton.build();                                   // build the failure links
        double buildMs = millisSince(buildStart);

        long searchStart = System.nanoTime();
        List<AhoCorasick.Match> matches = automaton.search(text);   // ONE pass over the text
        double searchMs = millisSince(searchStart);

        // Group the matches by pattern so we can print them neatly.
        Map<String, List<Integer>> positionsByPattern = new LinkedHashMap<>();
        for (String p : patterns) positionsByPattern.put(p, new ArrayList<>());
        for (AhoCorasick.Match m : matches) positionsByPattern.get(m.pattern).add(m.start);

        System.out.println();
        System.out.println(LINE);
        System.out.println("AHO-CORASICK - MULTI-PATTERN SEARCH");
        System.out.println(LINE);
        System.out.println("Input Size         : " + text.length() + " characters");
        System.out.println("Input Text:\n" + preview(text));
        System.out.println();
        System.out.println("Selected Algorithm : Aho-Corasick");
        System.out.println("Reason for Selection:\n  The task requires searching for multiple patterns in a text. Aho-Corasick uses a Trie\n  and failure links to find all of them in ONE pass over the text.");
        System.out.println();
        System.out.println("Number of Patterns : " + patterns.size() + "   (Trie nodes: " + automaton.getNodeCount() + ")");
        System.out.println("Matched Patterns and positions (0-based start index):");
        int matchedPatterns = 0;
        for (Map.Entry<String, List<Integer>> entry : positionsByPattern.entrySet()) {
            List<Integer> pos = entry.getValue();
            if (pos.isEmpty()) {
                System.out.println("  " + entry.getKey() + " : not found");
            } else {
                matchedPatterns++;
                System.out.println("  " + entry.getKey() + " : " + pos.size() + " match(es) at "
                        + pos.subList(0, Math.min(10, pos.size())) + (pos.size() > 10 ? " ..." : ""));
            }
        }
        System.out.println("Patterns found     : " + matchedPatterns + " of " + patterns.size());
        System.out.println("Total matches      : " + matches.size());
        System.out.printf("Execution Time     : build %.3f ms, search %.3f ms%n", buildMs, searchMs);
        System.out.println("Note: Aho-Corasick finds exact known patterns; it is not a spelling-correction algorithm.");
        System.out.println(LINE);
    }

    // ------------------------------------------------------------------
    // MODE 4: evaluation
    // ------------------------------------------------------------------
    private static void runEvaluation(SpellChecker checker, String path) {
        System.out.println();
        System.out.println(LINE);
        System.out.println("EVALUATION ON LABELLED TEST DATA");
        System.out.println(LINE);
        long start = System.nanoTime();
        try {
            Evaluator.run(path, checker);
        } catch (IOException e) {
            System.out.println("Could not read test file: " + e.getMessage());
            return;
        }
        System.out.printf("%nExecution Time: %.3f ms%n", millisSince(start));
        System.out.println(LINE);
    }

    // ------------------------------------------------------------------
    // Demo tests 1-7 (same functions as the menu, with fixed inputs)
    // ------------------------------------------------------------------
    private static void runDemoTests(SpellChecker checker) {
        System.out.println("\n>>> TEST 1: correct word");
        runSpellCheck(checker, "beautiful");
        System.out.println("\n>>> TEST 2: misspelled word");
        runSpellCheck(checker, "recieve");
        System.out.println("\n>>> TEST 3: short sentence");
        runSpellCheck(checker, "I recieve a mesage.");
        System.out.println("\n>>> TEST 4: paragraph (corpus/input_docs/paragraph.txt)");
        String paragraph = readFile("corpus/input_docs/paragraph.txt");
        if (paragraph != null) runSpellCheck(checker, paragraph);
        System.out.println("\n>>> TEST 5: large text file (corpus/input_docs/large_text.txt)");
        String large = readFile("corpus/input_docs/large_text.txt");
        if (large != null) runSpellCheck(checker, large);
        System.out.println("\n>>> TEST 6: KMP");
        runKmp("This is an advanced algorithms project. Algorithms are fun; I study algorithms daily.", "algorithms");
        System.out.println("\n>>> TEST 7: Aho-Corasick on the large text");
        if (large != null) runAhoCorasick(large, new String[]{"algorithm", "data", "structure", "pattern", "dictionary"});
    }

    // ------------------------------------------------------------------
    // Small helper methods
    // ------------------------------------------------------------------


    // ------------------------------------------------------------------
    // MODULE 4: max-flow. Bipartite matching = errors (left) <-> suggestions (right).
    //   source -> error (cap 1) -> suggestion (cap 1) -> sink (cap 1)
    //   Max-flow = the largest set of (error, suggestion) pairs where NO suggestion is used twice.
    //   (Integer capacities => integer flow => a real matching. Min-cut = König's theorem side.)
    // ------------------------------------------------------------------
    private static void runMaxFlowDemo(SpellChecker checker, String text) {
        SpellChecker.Result result = checker.check(text, true);
        Map<String, List<SuggestionEngine.Suggestion>> distinct = new LinkedHashMap<>();
        for (SpellChecker.ErrorInfo e : result.errors) {
            distinct.putIfAbsent(e.word.toLowerCase(), e.suggestions);
        }
        System.out.println();
        System.out.println(LINE);
        System.out.println("MAX-FLOW - ONE-TO-ONE ASSIGNMENT OF CORRECTIONS");
        System.out.println(LINE);
        System.out.println("Input: " + preview(text));
        if (distinct.isEmpty()) {
            System.out.println("No potential errors found, so there is nothing to assign.");
            return;
        }
        List<String> errorWords = new ArrayList<>(distinct.keySet());
        List<String> suggestionWords = new ArrayList<>();
        for (List<SuggestionEngine.Suggestion> list : distinct.values()) {
            for (SuggestionEngine.Suggestion sg : list) {
                if (!suggestionWords.contains(sg.word)) suggestionWords.add(sg.word);
            }
        }
        int errorCount = errorWords.size();
        int source = 0;
        int sink = errorCount + suggestionWords.size() + 1;
        MaxFlow flow = new MaxFlow(sink + 1);
        for (int i = 0; i < errorCount; i++) {
            flow.addEdge(source, 1 + i, 1);
            for (SuggestionEngine.Suggestion sg : distinct.get(errorWords.get(i))) {
                flow.addEdge(1 + i, 1 + errorCount + suggestionWords.indexOf(sg.word), 1);
            }
        }
        for (int j = 0; j < suggestionWords.size(); j++) {
            flow.addEdge(1 + errorCount + j, sink, 1);
        }

        System.out.println("Network: " + errorCount + " error word(s), " + suggestionWords.size()
                + " candidate correction(s), source = 0, sink = " + sink);
        long start = System.nanoTime();
        int ekFlow = flow.edmondsKarp(source, sink);
        double ekMs = millisSince(start);
        System.out.println("\nAssignment (found by Edmonds-Karp):");
        for (int i = 0; i < errorCount; i++) {
            List<Integer> targets = flow.usedTargets(1 + i);
            String assigned = targets.isEmpty() ? "(no free correction)"
                    : suggestionWords.get(targets.get(0) - 1 - errorCount);
            System.out.println("  " + errorWords.get(i) + " -> " + assigned);
        }
        int ekAugmentations = flow.getAugmentations();

        flow.reset();
        start = System.nanoTime();
        int dinicFlow = flow.dinic(source, sink);
        double dinicMs = millisSince(start);
        int cutCapacity = 0;
        for (int[] edge : flow.minCutEdges(source)) cutCapacity += edge[2];

        System.out.println();
        System.out.println("Edmonds-Karp (BFS augmenting paths) : max-flow = " + ekFlow + ", augmenting paths = "
                + ekAugmentations + String.format(", time %.3f ms", ekMs));
        System.out.println("Dinic (level graph, blocking flow)  : max-flow = " + dinicFlow + ", phases = "
                + flow.getPhases() + String.format(", time %.3f ms", dinicMs));
        System.out.println("Min-cut capacity                    : " + cutCapacity
                + "  (equals max-flow: " + (cutCapacity == ekFlow && ekFlow == dinicFlow) + ")");
        System.out.println("Meaning: no correction word is used twice; if two errors want the same word, one gets its next best.");
        System.out.println("Complexity: Edmonds-Karp O(V*E^2), Dinic O(V^2*E)  (V = nodes, E = edges).");
        System.out.println(LINE);
    }

    // ------------------------------------------------------------------
    // MODULE 5: vertex cover (NP-complete decision problem) + 2-approximation
    // ------------------------------------------------------------------
    private static void runVertexCover(String edgeText) {
        List<int[]> edges;
        try {
            edges = Approx.parseEdges(edgeText);
        } catch (NumberFormatException e) {
            System.out.println("Please enter edges like 0-1,1-2,2-3");
            return;
        }
        int vertexCount = 0;
        for (int[] e : edges) vertexCount = Math.max(vertexCount, Math.max(e[0], e[1]) + 1);
        if (edges.isEmpty() || vertexCount > 20) {
            System.out.println("Enter at least one edge, with vertex numbers below 20 (exact search is exponential).");
            return;
        }
        int[] matching = new int[1];
        long start = System.nanoTime();
        Set<Integer> cover = Approx.approximateCover(edges, matching);
        double approxMs = millisSince(start);
        start = System.nanoTime();
        int optimum = Approx.exactMinimumCover(vertexCount, edges);
        double exactMs = millisSince(start);

        System.out.println();
        System.out.println(LINE);
        System.out.println("VERTEX COVER - 2-APPROXIMATION vs EXACT");
        System.out.println(LINE);
        System.out.println("Vertices: " + vertexCount + ", edges: " + edges.size());
        System.out.println("Approximation (maximal matching): cover = " + cover + ", size = " + cover.size()
                + ", matching size = " + matching[0] + String.format(", time %.3f ms", approxMs));
        System.out.println("Certificate check (verifier, O(E))  : valid cover = " + Approx.isCover(cover, edges));
        System.out.println("Exact minimum (try all subsets)     : size = " + optimum + String.format(", time %.3f ms", exactMs));
        System.out.printf("Ratio approx/optimal                : %.2f  (guaranteed <= 2.00: %b)%n",
                cover.size() / (double) optimum, cover.size() <= 2 * optimum);
        System.out.println("Note: exact search is exponential (NP-hard); the approximation is polynomial O(E).");
        System.out.println("This is a syllabus demonstration, separate from the spelling pipeline.");
        System.out.println(LINE);
    }

    // ------------------------------------------------------------------
    // MODULE 6: Monte Carlo accuracy estimate using reservoir sampling + random typos
    // ------------------------------------------------------------------
    private static void runMonteCarlo(SpellChecker checker, String trialsText) {
        int trials;
        try {
            trials = Integer.parseInt(trialsText.trim());
        } catch (NumberFormatException e) {
            trials = 40;
        }
        trials = Math.max(5, Math.min(trials, 300));
        List<String> pool = new ArrayList<>();
        for (String w : commonWords) {
            if (w.length() >= 5) pool.add(w);
        }
        if (pool.isEmpty()) {
            System.out.println("corpus/common_words.txt is needed for this test.");
            return;
        }
        java.util.Random random = new java.util.Random();
        long start = System.nanoTime();
        List<String> sample = Randomised.reservoirSample(pool, trials, random);
        int detected = 0, top1 = 0, top3 = 0;
        List<String> failures = new ArrayList<>();
        for (String original : sample) {
            String typo = Randomised.corrupt(original, random);
            boolean flagged = checker.isPotentialError(typo);
            List<SuggestionEngine.Suggestion> suggestions = flagged ? checker.suggest(typo, true) : new ArrayList<>();
            if (flagged) detected++;
            boolean first = !suggestions.isEmpty() && suggestions.get(0).word.equals(original);
            boolean inTop3 = false;
            for (SuggestionEngine.Suggestion sg : suggestions) if (sg.word.equals(original)) inTop3 = true;
            if (first) top1++;
            else if (failures.size() < 8) failures.add(typo + " -> " + (suggestions.isEmpty() ? "(none)" : suggestions.get(0).word) + " (expected " + original + ")");
            if (inTop3) top3++;
        }
        double elapsed = millisSince(start);
        int n = sample.size();

        System.out.println();
        System.out.println(LINE);
        System.out.println("RANDOM ACCURACY TEST");
        System.out.println(LINE);
        System.out.println("Method: reservoir-sample " + n + " random common words (uniform), make one random typo in each");
        System.out.println("(delete / insert / substitute / swap), then check detection and correction.");
        System.out.println("Every run uses NEW random words, so the numbers vary a little each time (Monte Carlo).");
        System.out.println();
        System.out.println("Detection rate   : " + formatEstimate(detected, n));
        System.out.println("Top-1 correction : " + formatEstimate(top1, n));
        System.out.println("Top-3 correction : " + formatEstimate(top3, n));
        if (!failures.isEmpty()) {
            System.out.println("\nSome wrong first suggestions:");
            for (String fail : failures) System.out.println("  " + fail);
        }
        System.out.printf("%nExecution Time : %.0f ms%n", elapsed);
        System.out.println("Note: this is an ESTIMATE from random samples (see the +- interval), not a fixed accuracy.");
        System.out.println(LINE);
    }

    /** "83.0% (33/40)  95% interval +-11.6%"  (normal approximation of a proportion). */
    private static String formatEstimate(int successes, int n) {
        double p = successes / (double) n;
        double margin = 1.96 * Math.sqrt(p * (1 - p) / n);
        return String.format("%.1f%% (%d/%d)   95%% interval +-%.1f%%", p * 100, successes, n, margin * 100);
    }

    // ------------------------------------------------------------------
    // MODULE 6: Miller-Rabin
    // ------------------------------------------------------------------
    private static void runMillerRabin(String numberText) {
        long n;
        try {
            n = Long.parseLong(numberText.trim());
        } catch (NumberFormatException e) {
            System.out.println("Please enter a whole number (up to 18 digits).");
            return;
        }
        int rounds = 10;
        java.util.Random random = new java.util.Random();
        long start = System.nanoTime();
        boolean probablyPrime = Randomised.millerRabin(n, rounds, random);
        double mrMs = millisSince(start);
        // Deterministic check (trial division) just to compare, only for numbers up to 10^12.
        String exact = "skipped (number too large for trial division here)";
        if (n <= 1_000_000_000_000L) {
            boolean prime = n >= 2;
            for (long d = 2; d * d <= n && prime; d++) if (n % d == 0) prime = false;
            exact = prime ? "prime" : "composite";
        }
        System.out.println();
        System.out.println(LINE);
        System.out.println("MILLER-RABIN PRIMALITY TEST");
        System.out.println(LINE);
        System.out.println("Number            : " + n);
        System.out.println("Miller-Rabin      : " + (probablyPrime ? "PROBABLY PRIME" : "COMPOSITE (a witness was found - this answer is certain)")
                + "  [" + rounds + " random rounds]");
        System.out.println("Trial division    : " + exact);
        System.out.printf("Miller-Rabin time : %.3f ms%n", mrMs);
        System.out.println("Error bound: a composite is wrongly called prime with probability <= 4^-" + rounds + " (about 1 in a million).");
        System.out.println("561 = 3*11*17 is a Carmichael number: it fools the Fermat test but Miller-Rabin catches it.");
        System.out.println("'COMPOSITE' is always correct; only 'PROBABLY PRIME' can (rarely) be wrong.");
        System.out.println(LINE);
    }

    // ------------------------------------------------------------------
    // MODULE 6: parallel reduce + Blelloch prefix sum, with work/span numbers
    // ------------------------------------------------------------------
    private static void runParallelDemo(SpellChecker checker, String text) {
        String[] lines = text.split("\\R");
        int cores = Runtime.getRuntime().availableProcessors();

        // Warm-up so that timing is fair (the first run includes JIT compilation).
        java.util.Arrays.stream(lines).mapToInt(checker::countPotentialErrors).sum();

        long start = System.nanoTime();
        int sequentialTotal = java.util.Arrays.stream(lines).mapToInt(checker::countPotentialErrors).sum();
        double seqMs = millisSince(start);

        start = System.nanoTime();
        int parallelTotal = java.util.Arrays.stream(lines).parallel().mapToInt(checker::countPotentialErrors).sum();   // parallel REDUCE
        double parMs = millisSince(start);

        int[] perLine = new int[lines.length];
        long work = 0, longestLine = 0;
        for (int i = 0; i < lines.length; i++) {
            perLine[i] = checker.countPotentialErrors(lines[i]);
            long tokens = SpellChecker.countWords(lines[i]);
            work += tokens;
            longestLine = Math.max(longestLine, tokens);
        }
        int[] scan = Randomised.blellochScan(perLine);
        int running = 0;
        boolean scanCorrect = true;
        for (int i = 0; i < perLine.length; i++) {
            if (scan[i] != running) scanCorrect = false;
            running += perLine[i];
        }
        long span = longestLine + (long) Math.ceil(Math.log(Math.max(2, lines.length)) / Math.log(2));

        System.out.println();
        System.out.println(LINE);
        System.out.println("PARALLEL REDUCE + PREFIX SUM");
        System.out.println(LINE);
        System.out.println("Lines: " + lines.length + ", tokens: " + work + ", CPU cores available: " + cores);
        System.out.println("Task: count potential errors in every line, then add them up (a REDUCE).");
        System.out.println();
        System.out.printf("Sequential reduce : %d errors, %.3f ms%n", sequentialTotal, seqMs);
        System.out.printf("Parallel reduce   : %d errors, %.3f ms  (same answer: %b)%n", parallelTotal, parMs, sequentialTotal == parallelTotal);
        System.out.println("Blelloch prefix-sum of per-line error counts is correct: " + scanCorrect
                + "  (errors before line 1 = " + (scan.length > 0 ? scan[0] : 0)
                + ", before the last line = " + (scan.length > 0 ? scan[scan.length - 1] : 0) + ")");
        System.out.println();
        System.out.println("Work T1 (all token checks)            : " + work);
        System.out.println("Span  Tinf (longest line + tree depth): " + span);
        System.out.println("Max possible speedup T1/Tinf          : " + String.format("%.1f", work / (double) Math.max(1, span)));
        System.out.println("Brent's bound on " + cores + " cores: Tp <= T1/p + Tinf = " + (work / cores + span) + " token checks");
        System.out.println("Note: real speedup depends on the machine; small inputs may not run faster in parallel.");
        System.out.println(LINE);
    }

    /** Prints a prompt and reads one line; returns null when the input has ended. */
    private static String ask(Scanner scanner, String prompt) {
        System.out.print(prompt);
        return scanner.hasNextLine() ? scanner.nextLine() : null;
    }

    /** Same, but returns a default value when the user just presses Enter. */
    private static String ask(Scanner scanner, String prompt, String defaultValue) {
        String answer = ask(scanner, prompt);
        return (answer == null || answer.trim().isEmpty()) ? defaultValue : answer.trim();
    }

    /** If the input is the path of an existing file, read that file; otherwise use it as text. */
    private static String readTextOrFile(String input) {
        if (input == null) return null;
        if (Files.isRegularFile(Paths.get(input.trim()))) {
            return readFile(input.trim());
        }
        return input;
    }

    /** Reads a whole text file, or prints a message and returns null. */
    private static String readFile(String path) {
        try {
            Path file = Paths.get(path);
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("Could not read file '" + path + "': " + e.getMessage());
            return null;
        }
    }

    private static double millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    /** Long texts are shortened for display only (processing always uses the full text). */
    private static String preview(String text) {
        String trimmed = text.trim();
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 300) + " ... [" + (trimmed.length() - 300) + " more characters]";
    }

    private static String formatSuggestions(List<SuggestionEngine.Suggestion> list) {
        if (list.isEmpty()) return "(no suggestion found within 3 edits)";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i).word).append(" (").append(list.get(i).distance).append(")");
        }
        return sb.toString();
    }

    private static void printMoreNote(int total) {
        if (total > MAX_ROWS_SHOWN) {
            System.out.println("  ... and " + (total - MAX_ROWS_SHOWN) + " more (only the first " + MAX_ROWS_SHOWN + " are shown)");
        }
    }
}
