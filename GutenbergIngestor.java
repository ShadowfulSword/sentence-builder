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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GutenbergIngestor {
    final private DocumentMetadata metadata;
    private BufferedReader bufferedReader;
    private String sanitizedSentence;
    private String currentParagraph;

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
            try {
                currentLine = bufferedReader.readLine();
            } catch (IOException e) {
                return false;
            }

            //If title is found, update metadata
            if(currentLine.contains("Title:"))
                metadata.setTitle(currentLine.substring(currentLine.indexOf(":")+2));
        }


        //Extract Author
        while(!currentLine.contains("Author:"))
        {
            try {
                currentLine = bufferedReader.readLine();
            } catch (IOException e) {
                return false;
            }

            //If Author is found, update metadata
            if(currentLine.contains("Author:"))
                metadata.setAuthor(currentLine.substring(currentLine.indexOf(":")+2));
        }

        //Extract Release Date and ebook number
        while(!currentLine.contains("Release date:"))
        {
            try {
                currentLine = bufferedReader.readLine();
            } catch (IOException e) {
                return false;
            }

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
            try {
                currentLine = bufferedReader.readLine();
            } catch (IOException e) {
                return false;
            }

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
    public void grabSentence(){
        //Determining what is a valid sentence is an awful, evil, punishing task
        //start by chunking into paragraphs

        //a line is the start of a new paragraph if contains more than
        //2 new lines in a row
        StringBuilder paragraph = new StringBuilder();

        if(currentParagraph.isBlank()) {
            try {
                String line = "";
                while ((line = bufferedReader.readLine()).isBlank())
                    continue;

                paragraph.append(line);
                while (!(line = bufferedReader.readLine()).isBlank()) {
                    paragraph.append(" ");
                    paragraph.append(line);
                }

                currentParagraph = paragraph.toString();
            } catch (IOException e) {
                return;
            }
        }

        //Once we find a valid paragraph, pull a sentence
        Pattern pattern = Pattern.compile("[.!?;]");
        Matcher matcher = pattern.matcher(currentParagraph);

        //if there are no sentence ending punctuation, sentence
        //is probably invalid
        if(!matcher.find()) {
            currentParagraph = "";
            grabSentence();
            return;
        }

        //if there is a sentence ending punctuation, extract
        //the first sentence and remove it from the paragraph
        int index = matcher.start() + 1;
        sanitizedSentence = currentParagraph.substring(0, index);
        currentParagraph = currentParagraph.substring(index);
        System.out.println(sanitizedSentence);
    }
}
