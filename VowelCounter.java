import java.util.Scanner;
public class VowelCounter {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // Take input from user
        System.out.print("Enter a string: ");
        String input = sc.nextLine().toLowerCase();
        // Count vowels
        int count = 0;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == 'a' || ch == 'e' || ch == 'i' || ch == 'o' || ch == 'u') {
                count++;
            }
        }
        // Output result
        System.out.println("Number of vowels: " + count);
        sc.close();
    }
}
