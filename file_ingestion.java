import java.io.BufferedReader; //use buffer reader to read the file because it negates some of the IO calls that FileReader makes
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class file_ingestion{

    //take in the buffer reader input and skip lines till you hit *** START OF... ***
    //skip everyline you take till you hit that goal as we don't account for those in our DB
    private void passStart(BufferedReader input) throws IOException {
        String line = input.readLine();
        //read every line till you either hit "*** START OF" or you hit the end of the file
        while(line != null){
            if(line.startsWith("*** START OF")) {
                return;
            }
            line = input.readLine();
        }

    }

    //skip the begining and then read and (till the DB integration is done) print each word in a given text file
    //make use of a BufferReader and make sure you only take the words themselves
    // so probably make a regex that leaves out numbers and punctuations
    public void readFile(Path file) throws IOException {
        //try-wtih-resource so the file closes even when an exception is thrown
        try(BufferedReader input = Files.newBufferedReader(file)){
            passStart(input);
            String line = input.readLine();
            while(input != null){
                if(line.startsWith("*** END OF"))
                    break;

                for(String word : line.toLowerCase().split("\\W+")){
                    if(!word.isEmpty())
                        System.out.println();
                }
            }
        }
    }

    //a method or some way to track the progress of the file completion
    // still trying to figure that out.kip the begining and then read and (till the DB integration is done)  Current idea is using a task class
    //get the full size of the text document and as we prosess and get each line we update that task with the byte difference


    //Side note: ideally we would need to make this part of the process faster for sanity's sake since we will be reading a lot of files
    //my research indicates that using BufferReader (or multiple with multi threading and a buffer size) will be good enough for the reading part
    //however, we will be making hundreds of thousands of IO calls to write to DB so, we really need to figure out how to optimize that
    //my inital idea was to  gather allinput. the words and batch send them but I do not yet know the best way to do that

    public static void main(String[] args){
        GutenbergIngestor ing = new GutenbergIngestor();
        boolean success = ing.openFile("Gutenberg\\testcorpus.txt");
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


        ing.grabSentence();
        ing.grabSentence();
        ing.grabSentence();
        ing.grabSentence();
        ing.grabSentence();

        System.out.println("Hello World");
    }
}