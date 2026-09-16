import java.util.Scanner;
public class PalindromeCheck {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // Take input from user
        System.out.print("Enter a string: ");
        String input = sc.nextLine();
        // Preprocessing: remove spaces, punctuation, and convert to lowercase
        String cleaned = input.replaceAll("[^a-zA-Z]", "").toLowerCase();
        // Reverse the cleaned string
        String reversed = new StringBuilder(cleaned).reverse().toString();
        // Check palindrome
        if (cleaned.equals(reversed)) {
            System.out.println("The string is a palindrome.");
        } else {
            System.out.println("The string is NOT a palindrome.");
        }
        sc.close();
    }
}
