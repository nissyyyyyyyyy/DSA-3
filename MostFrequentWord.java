import java.util.*;
public class MostFrequentWord {
    public static void main(String[] args) {
        String text = "Hackathons are fun. Texthack hackathons are exciting and fun.";
        // Preprocessing: lowercase and remove punctuation
        text = text.toLowerCase().replaceAll("[^a-zA-Z\\s]", "");
        // Tokenization
        String[] words = text.split("\\s+");
        // Count word frequencies
        Map<String, Integer> wordCount = new HashMap<>();
        for (String word : words) {
            wordCount.put(word, wordCount.getOrDefault(word, 0) + 1);
        }
        // Find the most frequent word
        String mostFrequent = null;
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : wordCount.entrySet()) {
            if (entry.getValue() > maxCount) {
                mostFrequent = entry.getKey();
                maxCount = entry.getValue();
            }
        }
        // Output result
        System.out.println("Most frequent word: " + mostFrequent);
        System.out.println("Frequency: " + maxCount);
    }
}
