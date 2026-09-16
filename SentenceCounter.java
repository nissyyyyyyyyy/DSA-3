import java.util.Scanner;

public class SentenceCounter {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // Take input text
        System.out.println("Enter a paragraph:");
        String text = sc.nextLine();
        // Split text into sentences using punctuation marks
        String[] sentences = text.split("[.!?]+");
        // Count sentences (ignoring empty entries)
        int count = 0;
        for (String sentence : sentences) {
            if (sentence.trim().length() > 0) {
                count++;
            }
        }
        // Output result
        System.out.println("Number of sentences: " + count);
        sc.close();
    }
}
