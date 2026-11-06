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
 *
 * Edited by Yaseen Hafid (yxh190036) on 11/06/2026
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

// #18: callers can close the reader explicitly, including during test cleanup (yaseen)
public class GutenbergIngestor implements AutoCloseable {
    final private DocumentMetadata metadata;
    private BufferedReader bufferedReader;
    private String currentParagraph;
    private boolean eof = false;
    private long bytesRead = 0;
    // #18: stop reading the body when the Gutenberg footer begins (yaseen)
    private static final String END_MARKER = "*** END OF THE PROJECT GUTENBERG EBOOK";

    //Regex patterns used to ensure paragraphs are valid in grabSentence() method
    private final Pattern punctuationPattern = Pattern.compile("[.!?;]");

    private static final Set<String> ABBREVIATIONS = new HashSet<>(Arrays.asList(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr",
            "vs", "etc", "ie", "eg", "inc", "ltd", "co"
    ));

    public GutenbergIngestor(){
        metadata = new DocumentMetadata();
        currentParagraph = "";
    }         // #18: sentences are now sanitized on demand, so i removed the old sentence buffer (yaseen)

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

     // #18: Validate the required header fields before storing the metadata (yaseen)
    public boolean extractMetadata() {
        String title = findLine("Title:");
        String author = findLine("Author:");
        String release = findLine("Release date:");
        if (title == null || author == null || release == null) {
            return false;
        }

        int dateStart = release.indexOf(':') + 1;
        int dateEnd = release.indexOf('[', dateStart);
        int numberStart = release.indexOf('#', dateEnd);
        int numberEnd = release.indexOf(']', numberStart);
        if (dateEnd < 0 || numberStart < 0 || numberEnd < 0) {
            return false;
        }

        int bookNumber;
        try {
            bookNumber = Integer.parseInt(release.substring(numberStart + 1, numberEnd).strip());
        } catch (NumberFormatException e) {
            return false;
        }
        if (findLine("*** START OF THE PROJECT GUTENBERG EBOOK") == null) {
            return false;
        }

        metadata.setTitle(title.substring(title.indexOf(':') + 1).strip());
        metadata.setAuthor(author.substring(author.indexOf(':') + 1).strip());
        metadata.setDate(release.substring(dateStart, dateEnd).strip());
        metadata.setBookNumber(bookNumber);
        return true;
    }

    // #18: share the header search and return null if the marker is missing (yaseen)
    private String findLine(String marker) {
        String line;
        while ((line = getNextLine()) != null) {
            if (line.contains(marker)) {
                return line;
            }
        }
        return null;
    }

    /**
     * Grabs the metadata.
     * @return Stored metadata object.
     */
    public DocumentMetadata getMetadata(){
        return metadata;
    }

    // #18: find the sentence boundary first, then extract the text once (yaseen)
    private String grabSentence() {
        Matcher matcher = punctuationPattern.matcher(currentParagraph);
        int end = currentParagraph.length();
        while (matcher.find()) {
            int punctuation = matcher.start();
            if (currentParagraph.charAt(punctuation) == '.' && isAbbreviation(punctuation + 1)) {
                continue;
            }
            end = punctuation + 1;
            break;
        }
        String sentence = currentParagraph.substring(0, end);
        currentParagraph = currentParagraph.substring(end);
        return sentence;
    }

    // #18: retain text collected before the footer or the physical end of the file (Yaseen)
    private void grabParagraph() {
        while (!eof) {
            String line = getNextLine();
            while (line != null && line.isBlank()) {
                line = getNextLine();
            }

            StringBuilder paragraph = new StringBuilder();
            while (line != null && !line.isBlank() && !line.contains(END_MARKER)) {
                paragraph.append(' ').append(line);
                line = getNextLine();
            }
            eof = line == null || line.contains(END_MARKER);
            if (punctuationPattern.matcher(paragraph).find()) {
                currentParagraph = paragraph.toString();
                return;
            }
        }
        currentParagraph = "";
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

    // #18: return the next usable sentence, including any text buffered at end of file (yaseen)
    public String getSanitizedSentence() {
        while (true) {
            if (currentParagraph.isBlank()) {
                grabParagraph();
            }
            if (currentParagraph.isBlank()) {
                return null;
            }

            String sentence = TextSanitizer.sanitize(grabSentence());
            if (!sentence.isBlank()) {
                return sentence;
            }
        }
    }

    // #18: Release the file so JUnit can remove its temporary fixture (yaseen)
    @Override
    public void close() throws IOException {
        if (bufferedReader != null) {
            bufferedReader.close();
        }
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
            // #18: readLine() returns null at EOF, so count only lines that exist
            if (line != null) {
                bytesRead += line.length();
            }
            return line;
        } catch (IOException e) {
            return null;
        }
    }
}