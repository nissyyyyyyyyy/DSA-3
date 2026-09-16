import java.util.Scanner;
public class UppercaseCounter {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter text:");
        String text = sc.nextLine();
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isUpperCase(text.charAt(i))) {
                count++;
            }
        }
        System.out.println("Uppercase Letters: " + count);
        sc.close();
    }
}