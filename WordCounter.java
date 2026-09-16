import java.util.Scanner;
public class WordCounter {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // Take input text
        System.out.println("Enter a sentence or paragraph:");
        String text = sc.nextLine();
        // Preprocessing: trim spaces
        text = text.trim();
        // Split text into words using whitespace
        String[] words = text.split("\\s+");
        // Count words
        int count = words.length;
        // Output result
        System.out.println("Number of words: " + count);
        sc.close();
    }
}
