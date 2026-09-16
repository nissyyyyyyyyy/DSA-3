import java.io.*;
import java.util.*;

class Article {
    String title, author, date, content;

    public Article(String title, String author, String date, String content) {
        this.title = (title != null) ? title : "Untitled";
        this.author = (author != null) ? author : "Unknown Author";
        this.date = (date != null) ? date : "Unknown Date";
        this.content = (content != null) ? content : "";
    }

    public void displayInfo() {
        System.out.println("Title   : " + title);
        System.out.println("Author  : " + author);
        System.out.println("Date    : " + date);
        int wordCount = content.trim().isEmpty() ? 0 : content.trim().split("\\s+").length;
        System.out.println("Length  : " + wordCount + " words");
    }
}

class NaiveSearch {
    public static int countOccurrences(String text, String keyword) {
        if (keyword.isEmpty() || text.isEmpty()) return 0;
        
        int n = text.length(), m = keyword.length(), count = 0;
        for (int i = 0; i <= n - m; i++) {
            int j;
            for (j = 0; j < m; j++) {
                if (text.charAt(i + j) != keyword.charAt(j)) break;
            }
            if (j == m) count++;
        }
        return count;
    }
}

public class TextHackCorpus { 
    private final List<Article> corpus = new ArrayList<>();

    public void loadCorpus(String folderPath) {
        File folder = new File(folderPath);

        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("Warning: Folder '" + folderPath + "' does not exist or is not a directory.");
            System.out.println("Please create an '" + folderPath + "' directory and place .txt files inside.");
            return;
        }

        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            System.out.println("No files found in folder: " + folderPath);
            return;
        }

        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".txt")) {
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String title = br.readLine();
                    String author = br.readLine();
                    String date = br.readLine();
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        content.append(line).append(" ");
                    }
                    corpus.add(new Article(title, author, date, content.toString()));
                } catch (IOException e) {
                    System.out.println("Failed to read file: " + file.getName());
                }
            }
        }
        System.out.println("Successfully loaded " + corpus.size() + " article(s).");
    }

    public void displayCorpus() {
        if (corpus.isEmpty()) {
            System.out.println("Corpus is empty. Add .txt files to the 'articles' directory.");
            return;
        }
        for (int i = 0; i < corpus.size(); i++) {
            System.out.println("\n--- Article " + (i + 1) + " ---");
            corpus.get(i).displayInfo();
        }
    }

    public void keywordAnalytics(String keyword) {
        if (corpus.isEmpty()) {
            System.out.println("Corpus is empty. Add .txt files to the 'articles' directory.");
            return;
        }
        System.out.println("\nKeyword Analysis for: \"" + keyword + "\"");
        for (int i = 0; i < corpus.size(); i++) {
            Article article = corpus.get(i);
            int count = NaiveSearch.countOccurrences(article.content.toLowerCase(), keyword.toLowerCase());
            System.out.println("Article " + (i + 1) + " (" + article.title + ") → " + count + " occurrences");
        }
    }

    public static void main(String[] args) {
        TextHackCorpus loader = new TextHackCorpus();
        loader.loadCorpus("articles"); // Folder containing .txt files

        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("\n--- TextHack Menu ---");
            System.out.println("1. Display Corpus");
            System.out.println("2. Keyword Analytics");
            System.out.println("3. Exit");
            System.out.print("Choose option: ");

            try {
                int choice = Integer.parseInt(sc.nextLine().trim());
                switch (choice) {
                    case 1 -> loader.displayCorpus();
                    case 2 -> { 
                        System.out.print("Enter keyword: "); 
                        loader.keywordAnalytics(sc.nextLine()); 
                    }
                    case 3 -> { 
                        System.out.println("Exiting TextHack..."); 
                        return; 
                    }
                    default -> System.out.println("Invalid choice. Please pick 1, 2, or 3.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input! Please enter a number.");
            }
        }
    }
}