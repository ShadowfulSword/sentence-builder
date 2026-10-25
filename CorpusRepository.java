import java.io.BufferedReader; //use buffer reader to read the file because it negates some of the IO calls that FileReader makes
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CorpusRepository{

    public static void main(String[] args){
        GutenbergIngestor ing = new GutenbergIngestor();
        boolean success = ing.openFile("Gutenberg/testcorpus.txt");
        if(!success) {
            System.out.println("File not found");
            return;
        }

        success = ing.extractMetadata();
        if(!success) {
            System.out.println("Metadata extraction failed");
            return;
        }


        DocumentMetadata metadata = ing.getMetadata();
        System.out.println(metadata.getTitle());
        System.out.println(metadata.getAuthor());
        System.out.println(metadata.getDate());
        System.out.println(metadata.getBookNumber());
        System.out.println(metadata.getSizeBytes());

//        for(int i = 0; i < 10; i++)
//            System.out.println(ing.getSanitizedSentence() + "\n");

        CorpusTokenizer tokenizer = new CorpusTokenizer(ing);
        int count = 0;
        while(tokenizer.hasNext()) {
            Token token = tokenizer.next();
            System.out.println(token.getWord() + " " + token.isStart() + " " + token.isEnd());
        }

    }
}