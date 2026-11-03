/* ****************************************************************
 * GutenbergIngestor Class
 *
 * This class represents an object with the primary responsibility
 * of opening a text file, extracting the metadata and crating a
 * DocumentMetadata object, and maintaining a buffer with a
 * sanitized sentence from said text file.
 *
 * If a user tries to open a file that does not exist, the program
 * will generate a FileNotFoundException.
 *
 * Written by Gabriel Goble (GGG220000) for CS4485.0W1, Sentence-Builder Project,
 * starting October 16, 2026
 * ****************************************************************/
package backend.Ingestion;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GutenbergIngestor {
    final private DocumentMetadata metadata;
    private BufferedReader bufferedReader;
    private String sanitizedSentence;
    private String currentParagraph;
    private boolean eof = false;
    private long bytesRead = 0;

    //Regex patterns used to ensure paragraphs are valid in grabSentence() method
    private final Pattern punctuationPattern = Pattern.compile("[.!?;]");

    private static final Set<String> ABBREVIATIONS = new HashSet<>(Arrays.asList(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr",
            "vs", "etc", "ie", "eg", "inc", "ltd", "co"
    ));

    public GutenbergIngestor(){
        metadata = new DocumentMetadata();
        sanitizedSentence = "";
        currentParagraph = "";
    }

    /**
     Opens the file at the specified path for ingestion.
     @param filename The path of the file you wish to open.
     @return True if successful, False if unsuccessful.
     @author Gabriel Goble (GGG220000)
     @DateWritten 10/16/2026
     */
    public boolean openFile(String filename) {
        Path path = Paths.get(filename);

        try {
            //create the reader and extract the size of the file in bytes
            bufferedReader = Files.newBufferedReader(path);
            metadata.setSizeBytes((int) Files.size(path));
        } catch (Exception e) {
            //should the file not exist or otherwise be un-accessible, return false
            return false;
        }
        return true;
    }

    /**
     Reads the metadata from the file and stores it.
     @return True if metadata was successfully extracted.
     @author Gabriel Goble (GGG220000)
     @DateWritten 10/16/2026
     */
    public boolean extractMetadata()
    {
        //Metadata is always formatted in the order Title, Author
        //Release Date, ebook number

        String currentLine = "";

        //Extract Title
        while(!currentLine.contains("Title:"))
        {
            currentLine = getNextLine();

            //If title is found, update metadata
            if(currentLine.contains("Title:"))
                metadata.setTitle(currentLine.substring(currentLine.indexOf(":")+2));
        }


        //Extract Author
        while(!currentLine.contains("Author:"))
        {
            currentLine = getNextLine();

            //If Author is found, update metadata
            if(currentLine.contains("Author:"))
                metadata.setAuthor(currentLine.substring(currentLine.indexOf(":")+2));
        }

        //Extract Release Date and ebook number
        while(!currentLine.contains("Release date:"))
        {
            currentLine = getNextLine();

            //If release date is found, update metadata
            if(currentLine.contains("Release date:")){
                int endOfDate = currentLine.indexOf("[") - 1;
                metadata.setDate(currentLine.substring(currentLine.indexOf(":")+2, endOfDate));

                //since e-book number is on the same line, extract that too
                String bookNumber = currentLine.substring(currentLine.indexOf("#")+1, currentLine.indexOf("]"));
                metadata.setBookNumber(Integer.parseInt(bookNumber));
            }
        }

        //Since we've extracted all the metadata we need, we parse through and discard
        //the rest of the header
        while(!currentLine.contains("*** START OF THE PROJECT GUTENBERG EBOOK"))
                currentLine = getNextLine();

        return true;
    }

    /**
     * Grabs the metadata.
     * @return Stored metadata object.
     */
    public DocumentMetadata getMetadata(){
        return metadata;
    }

    /**
     * Internal function that grabs the next sentence from the input file.
     *
     * Sentence grabbed will be put straight into sanitizedSentence field.
     * @author Gabriel Goble (GGG220000)
     * @DateWritten 10/16/2026
     */
    private void grabSentence(){
        //Determining what is a valid sentence is an awful, evil, punishing task
        //start by chunking into paragraphs

        //a line is the start of a new paragraph if contains more than
        //2 new lines in a row

        if(currentParagraph.isBlank()) {
            grabParagraph();
        }

        //Once we find a valid paragraph, pull a sentence
        Matcher matcher = punctuationPattern.matcher(currentParagraph);

        //if there is a sentence ending punctuation, extract
        //the first sentence and remove it from the paragraph

        StringBuilder sb = new StringBuilder();
        int index;
        do {
            if(!matcher.find())
                index = currentParagraph.length();
            else
                index = matcher.start() + 1;
            sb.append(currentParagraph, 0, index);
        }
        while(isAbbreviation(index) && index < currentParagraph.length());
        sanitizedSentence = sb.toString();
        currentParagraph = currentParagraph.substring(index);

        //System.out.println(sanitizedSentence);
    }

    /**
     * Internal function that grabs the next paragraph from the input file.
     *
     * Sentence grabbed will be put straight into currentParagraph field.
     * @author Gabriel Goble (GGG220000)
     * @DateWritten 10/20/2026
     */
    private void grabParagraph(){
        StringBuilder paragraph = new StringBuilder();
        //skip past all blank lines before the next paragraph
        String line;
        do {
            line = getNextLine();
            if (line == null) {
                eof = true;
                return;
            }
        } while (line.isBlank());

        //Read lines and add them to paragraph until we
        //hit a paragraph break
        do
        {
            paragraph.append(" ");
            paragraph.append(line);
            line = getNextLine();
            if (line == null) {
                eof = true;
                return;
            }
        } while(!line.isBlank());

        //store freshly read paragraph
        currentParagraph = paragraph.toString();

        //Check if we are at the end of document
        if(currentParagraph.contains("*** END OF THE PROJECT GUTENBERG EBOOK")) {
            eof = true;
            currentParagraph = "";
            return;
        }

        //Once we find a valid paragraph, pull a sentence
        Matcher matcher = punctuationPattern.matcher(currentParagraph);

        //if there are no sentence ending punctuation, sentence
        //is probably invalid
        if(!matcher.find()) {
            currentParagraph = "";
            grabParagraph();
            return;
        }


    }


    /**
     * Helper function that takes the index of a period and checks if the word
     * immediately preceding it is in our list of known abbreviations.
     *
     * @param index The index of a period '.'
     * @return returns true if word before index is a known abbreviation.
     * @author Gabriel Goble (GGG220000)
     * @DateWritten 10/16/2026
     */
    private boolean isAbbreviation(int index){
        StringBuilder sb = new StringBuilder();
        for(int i = index-2; i >= 0 ; i--){
            char c = currentParagraph.charAt(i);
            if(!Character.isWhitespace(c))
                sb.append(c);
            else
                break;
        }

        sb.reverse();
        return ABBREVIATIONS.contains(sb.toString().toLowerCase(Locale.ROOT));
    }

    /**
     * Returns the stored sanitized sentence and has the ingestor
     * grab the next one from source file.
     * @return Returns the current sentence without punctuation and with
     * the punctuation normalized.
     * @author Gabriel Goble (GGG220000)
     * @DateWritten 10/16/2026
     */
    public String getSanitizedSentence(){
        //If the end of file was reached and the final paragraph has
        //already been parsed, output null
        if(eof && currentParagraph.isBlank())
            return null;

        //store the current one temporarily
        String temp = sanitizedSentence;

        //loads the next sentence into sanitizedSentenceField
        grabSentence();
        sanitizeSentence();

        //if the field was empty before, grab the sentence just loaded up.
        if(temp.isBlank())
            temp = getSanitizedSentence();

        return temp;
    }
    /**
     * Takes the sentence currently in the buffer and sanitizes it for.
     *
     * New sentence will be put straight into sanitizedSentence field.
     * @author Gabriel Goble (GGG220000)
     * @DateWritten 10/20/2026
     */
    private void sanitizeSentence(){
        if(eof)
            return;
        sanitizedSentence = TextSanitizer.sanitize(sanitizedSentence);
    }

    /**
     * @return Gets the total number of bytes read so far by the program.
     * @author Gabriel Goble (GGG220000)
     * @DateWritten 10/20/2026
     */
    public long getBytesRead() {
        return bytesRead;
    }

    /**
     * Grabs the next line from source file while updating bytesRead counter.
     * @return The next line from the source file.
     * @author Gabriel Goble (GGG220000)
     * @DateWritten 10/20/2026
     */
    public String getNextLine(){
        try {
            String line = bufferedReader.readLine();
            bytesRead += line.length();
            return line;
        } catch (IOException e) {
            return null;
        }
    }
}
