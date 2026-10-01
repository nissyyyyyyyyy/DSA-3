# Dictionary-Based Error Detection and Suggestions in Text Processing System

Pure Java (JDK 11+), no libraries. Built from the reference `spellcheck_java.zip`.
Dictionary: `corpus/dictionary.txt` (370,105 words, loaded and counted at start-up).

## Run (VS Code terminal, from the project root folder)
```
javac -d bin src/main/java/spellcheck/*.java
java -cp bin spellcheck.Main
```
Windows PowerShell: same two commands. If wildcard fails: `dir src\main\java\spellcheck\*.java -Name` is not needed on JDK 11+; otherwise compile each file name.
Mac without javac: `brew install --cask temurin@17`, then reopen the terminal.
Errors: "Could not read corpus/dictionary.txt" = you are not in the project root.

## Menu
1 spell check (typed) | 2 spell check (file) | 3 KMP | 4 Aho-Corasick | 5 evaluation | 6 demo tests 1-7 | 0 exit
Try: `beautiful`, `recieve`, `I recieve a mesage.`, `corpus/input_docs/paragraph.txt`, `corpus/input_docs/large_text.txt`.

## Classes
| File | Job |
|---|---|
| Main | menu, algorithm selector, output |
| DictionaryLoader | reads dictionary |
| Trie | dictionary lookup, O(L) |
| SpellChecker | tokenize -> lookup -> suggest -> corrected text |
| SuggestionEngine | edit distance (DP), length filter, Soundex tie-break |
| KMP | one exact pattern (CO2) |
| AhoCorasick | many exact patterns (CO2 extra) |
| corpus/common_words.txt | ~900 common English words: ranks suggestions and catches junk dictionary words like wat/yoe/nama |
| Evaluator | precision, recall, F1, top-1, top-3 on `corpus/test_data.csv` |

## Algorithm selection (shown in every output)
| Input | Strategy |
|---|---|
| 1 word / up to 20 words | Simple Dictionary Lookup + Edit Distance (compare with all words) |
| 21+ words (paragraph, large file) | Lookup + Candidate Filtering + Edit Distance |
| Menu 3 | KMP (one pattern) |
| Menu 4 | Aho-Corasick (many patterns) |
Aho-Corasick is never used for spelling: it matches known exact patterns only.

## Complexity (L = word length, n = text length, m = pattern length / total pattern length, z = matches, p,q = word lengths, W = dictionary words)
- Trie lookup O(L); build O(total letters).
- Edit distance O(p*q) time and space. One suggestion search: O(W*p*q) without filtering; filtering skips words whose length differs by more than 2 (lossless, since each edit changes length by 1).
- KMP: O(n+m), space O(m). Aho-Corasick: build O(m), search O(n+z), space O(m).

## Evaluation
Only computed from labelled data (menu 5). Typed text shows N/A. On the included 70-pair test file: detection precision 100%, recall 90%, F1 94.7%, top-1 86.7%, top-3 90.0%. The 6 misses are misspellings that happen to be dictionary entries (e.g. "calender"). Numbers depend on the small hand-made test set.

## CO mapping
Primary CO2 String Algorithms: KMP, plus Aho-Corasick as an additional multi-pattern algorithm (not one of the named syllabus items). Edit distance is a supporting DP technique (not a listed CO3 pattern).

## Changes from the reference
Kept Trie, Aho-Corasick, Damerau-Levenshtein, Soundex (tie-break only), line/word positions. Removed: BatchAnalyzer folder mode, JSON report, Trie-vs-naive benchmark, Unicode normalisation (to keep it simple). Added: KMP, selector, Evaluator, comments.
Limits: words with apostrophes are split; first-letter typos still work but ranking uses only the small dictionary without word frequency.

## Viva quick answers
- Purpose: detect words not in a dictionary and suggest closest words.
- Trie: fast lookup independent of dictionary size. Only detects, never corrects.
- Edit distance: minimum insert/delete/substitute/swap edits; DP stores prefix results so each is solved once.
- KMP: one pattern, LPS table, never moves back in text. Aho-Corasick: many patterns, Trie + failure links, one pass. Not for spelling since misspellings are not known patterns.
- Accuracy: from labelled pairs (TP, FP, FN, TN); N/A without labels.

## Known limits (say honestly in viva)
- Words that exist in the dictionary are accepted, except short (max 4 letters) uncommon words one edit from a common word (e.g. wat, yoe, nama). This rule can wrongly flag rare valid short words.
- Edit distance has no sentence context: `wat is yoe nama` becomes `what is you name` (not `your`), because `you` is 1 edit from `yoe` and `your` is 2.

## Quick command (no menu)
```
java -cp bin spellcheck.Main "wat is yoe nama and I recieve a mesage"
```
Prints input, corrected output, algorithm used, tokens, errors, text accuracy, time.

## Modules 4, 5, 6 (menu options 7-11)
| Menu | Module topic | Class | How it relates to the project |
|---|---|---|---|
| 7 | M4 Max-flow: Ford-Fulkerson idea, Edmonds-Karp O(VE^2), Dinic O(V^2 E), min-cut = max-flow, bipartite matching | MaxFlow | Integrated: assigns each error word a DIFFERENT correction (source -> error -> suggestion -> sink, all capacities 1) |
| 8 | M5 NP-completeness: verifier (certificate), vertex cover 2-approximation via maximal matching | Approx | Standalone demo (not part of spelling); compares with exact exponential search |
| 9 | M6 Monte Carlo + reservoir sampling | Randomised | Integrated: random typos on randomly sampled words estimate detection / correction accuracy with a 95% interval |
| 10 | M6 Miller-Rabin (Monte Carlo, witnesses, Carmichael 561) | Randomised | Standalone demo |
| 11 | M6 parallel reduce, Blelloch prefix-sum, work T1 / span Tinf, Brent's bound | Randomised + Main | Integrated: counts errors per line in parallel |
Not implemented (only theory in the syllabus): min-cost max-flow, image segmentation, PTAS/FPTAS, FPT, universal hashing.
Honest note: the primary CO is still CO2 (KMP, Aho-Corasick). Modules 4-6 are supporting extras; be ready to say which ones are standalone.
