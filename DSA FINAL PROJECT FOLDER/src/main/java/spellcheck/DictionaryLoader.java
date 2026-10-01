package spellcheck;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads the dictionary file (corpus/dictionary.txt).
 *
 * FILE FORMAT: a plain text file with ONE word per line, e.g.
 *     a
 *     aa
 *     aaa
 *
 * The loader lower-cases each word, skips blank lines and removes duplicates.
 * The number of words it returns is the REAL dictionary size (nothing is assumed).
 */
public class DictionaryLoader {

    public static List<String> load(String path) throws IOException {
        // LinkedHashSet = removes duplicates but keeps the file order.
        Set<String> uniqueWords = new LinkedHashSet<>();

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(path), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim().toLowerCase();
                if (!word.isEmpty()) {
                    uniqueWords.add(word);
                }
            }
        }
        return new ArrayList<>(uniqueWords);
    }
}
