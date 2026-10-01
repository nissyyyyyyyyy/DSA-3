package spellcheck;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluator - measures how good the spell checker is, using LABELLED data.
 *
 * Accuracy can only be calculated when we KNOW the right answer. For typed text there is no
 * ground truth, so Main prints "N/A" there. Here we use a test file (corpus/test_data.csv):
 *
 *     correct,corrupted
 *     receive,recieve       <- the corrupted word is really an error
 *     hello,hello           <- same word: NOT an error (used to catch false alarms)
 *
 * DETECTION (does the program notice that a word is wrong?)
 *   "Positive" = the program says "this word is an error".
 *   TP = real error, detected            FP = correct word, wrongly flagged
 *   FN = real error, missed              TN = correct word, not flagged
 *
 *   Precision = TP / (TP + FP)     of everything flagged, how much was really wrong
 *   Recall    = TP / (TP + FN)     of all real errors, how many were found
 *   F1        = 2 * P * R / (P + R)   one number that balances precision and recall
 *
 * CORRECTION (does the program suggest the right word?)   Denominator = all real errors.
 *   Top-1 accuracy   = real errors whose FIRST suggestion equals the correct word
 *   Top-3 recall     = real errors whose correct word is among the 3 suggestions
 *   An error the program did not detect at all counts as a failure.
 *
 * Do not confuse:  accuracy (uses labels)  !=  edit distance (a similarity score)
 *                  !=  execution time      !=  number of detected errors.
 */
public class Evaluator {

    /** Runs the evaluation and prints the report. */
    public static void run(String csvPath, SpellChecker checker) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(csvPath), StandardCharsets.UTF_8);

        int tp = 0, fp = 0, fn = 0, tn = 0;
        int realErrors = 0, top1Correct = 0, top3Correct = 0;
        List<String> wrongCases = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("correct,")) {
                continue;                                   // skip blanks, comments, header
            }
            String[] parts = line.split(",");
            if (parts.length != 2) {
                continue;
            }
            String correct = parts[0].trim().toLowerCase();
            String corrupted = parts[1].trim().toLowerCase();

            boolean isRealError = !correct.equals(corrupted);        // ground truth
            boolean flagged = !checker.isCorrect(corrupted);         // what the program says

            if (isRealError && flagged) tp++;
            else if (!isRealError && flagged) fp++;
            else if (isRealError) fn++;
            else tn++;

            if (isRealError) {
                realErrors++;
                List<SuggestionEngine.Suggestion> suggestions = checker.suggest(corrupted, true);
                boolean top1 = flagged && !suggestions.isEmpty() && suggestions.get(0).word.equals(correct);
                boolean top3 = false;
                for (SuggestionEngine.Suggestion s : suggestions) {
                    if (flagged && s.word.equals(correct)) top3 = true;
                }
                if (top1) top1Correct++;
                if (top3) top3Correct++;
                if (!top1) {
                    String first = suggestions.isEmpty() ? "(none)" : suggestions.get(0).word;
                    wrongCases.add(corrupted + " -> got '" + first + "', expected '" + correct
                            + (flagged ? "'" : "' (not detected: it is a valid dictionary word)"));
                }
            }
        }

        System.out.println("Test file            : " + csvPath);
        System.out.println("Test pairs           : " + (tp + fp + fn + tn)
                + "  (real errors: " + realErrors + ", correct words: " + (fp + tn) + ")");
        System.out.println();
        System.out.println("DETECTION");
        System.out.println("  TP=" + tp + "  FP=" + fp + "  FN=" + fn + "  TN=" + tn);
        double precision = (tp + fp) == 0 ? -1 : tp / (double) (tp + fp);
        double recall = (tp + fn) == 0 ? -1 : tp / (double) (tp + fn);
        double f1 = (precision < 0 || recall < 0 || precision + recall == 0)
                ? -1 : 2 * precision * recall / (precision + recall);
        System.out.println("  Precision : " + percent(precision));
        System.out.println("  Recall    : " + percent(recall));
        System.out.println("  F1-score  : " + percent(f1));
        System.out.println();
        System.out.println("CORRECTION (over " + realErrors + " real errors)");
        System.out.println("  Top-1 correction accuracy : "
                + (realErrors == 0 ? "N/A (no real errors in test file)"
                : percent(top1Correct / (double) realErrors) + "  (" + top1Correct + "/" + realErrors + ")"));
        System.out.println("  Top-3 suggestion recall   : "
                + (realErrors == 0 ? "N/A (no real errors in test file)"
                : percent(top3Correct / (double) realErrors) + "  (" + top3Correct + "/" + realErrors + ")"));

        if (!wrongCases.isEmpty()) {
            System.out.println();
            System.out.println("Cases where the FIRST suggestion was not the expected word:");
            for (String c : wrongCases) {
                System.out.println("  " + c);
            }
        }
    }

    /** Formats 0.9333 as "93.3%"; -1 means "cannot be calculated". */
    private static String percent(double value) {
        return value < 0 ? "N/A (division by zero)" : String.format("%.1f%%", value * 100);
    }
}
