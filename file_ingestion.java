import java.io.BufferedReader; //use buffer reader to read the file because it negates some of the IO calls that FileReader makes
import java.nio.file.Path;

public class file_ingestion{

    //take in the buffer reader input and skip lines till you hit *** START OF... ***
    //skip everyline you take till you hit that goal as we don't account for those in our DB
    private void passStart(BufferedReader input){

    }

    //skip the begining and then read and (till the DB integration is done) print each word in a given text file
    //make use of a BufferReader and make sure you only take the words themselves
    // so probably make a regex that leaves out numbers and punctuations
    private void readFile(Path file){

    }

    //a method or some way to track the progress of the file completion
    // still trying to figure that out. Current idea is using a task class
    //get the full size of the text document and as we prosess and get each line we update that task with the byte difference


    //Side note: ideally we would need to make this part of the process faster for sanity's sake since we will be reading a lot of files
    //my research indicates that using BufferReader (or multiple with multi threading and a buffer size) will be good enough for the reading part
    //however, we will be making hundreds of thousands of IO calls to write to DB so, we really need to figure out how to optimize that
    //my inital idea was to  gather all the words and batch send them but I do not yet know the best way to do that

    public static void main(String[] args){
        System.out.println("Hello World");
    }
}