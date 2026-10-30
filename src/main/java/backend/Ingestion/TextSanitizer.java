/*
 * Text Sanitizer Class
 * This class consists of static methods that assist in sanitizing a string.
 * Only one method is public, that being the all-encompassing sanitize method which
 * performs all the necessary sanitizing steps and returns the final string. All other
 * methods are private and are used as helpers in the sanitization process.
 *
 * Written by Gabriel Goble (GGG220000) for CS4485.0W1, Sentence-Builder Project,
 * starting October 20, 2026
 */
package backend.Ingestion;
public class TextSanitizer {
    /**
     * Performs the following operations on the input string: removes punctuation,
     * converts to lowercase, and removes unnecessary whitespace.
     * @author Gabriel Goble (GGG220000)
     * @param text the text to sanitize.
     * @return the sanitized string.
     * @DateWritten 10/20/2026
     */
    public static String sanitize(String text){
        String sanitizedString = removePunctuation(text);
        sanitizedString = sanitizedString.toLowerCase();
        sanitizedString = sanitizedString.strip();
        sanitizedString = sanitizedString.replaceAll(" +", " ");
        return sanitizedString;
    }



    /**
     * Removes standard punctuation such as commas and quotations.
     * @author Gabriel Goble (GGG220000)
     * @param text the text to remove punctuation from.
     * @return the punctuation-less string
     * @DateWritten 10/20/2026
     */
    //NOTE: we will leave contractions as singular words to maintain tone.
    private static String removePunctuation(String text){
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < text.length(); i++){
            char c = text.charAt(i);
            if(Character.isWhitespace(c) || Character.isLetterOrDigit(c))
                sb.append(c);
        }
        return sb.toString();
    }
}
