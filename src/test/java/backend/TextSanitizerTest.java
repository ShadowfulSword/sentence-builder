//Oudom Pach OTP220001 10/26/2026
package backend;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TextSanitizerTest {
    //Tests the text sanitizer with different cases
    @Test
    public void testSanitizeRemovesPunctuationAndLowercases() {
        String input = "Hello, World! Welcome to 2026.";
        String expected = "hello world welcome to 2026";
        assertEquals(expected, TextSanitizer.sanitize(input), "Should remove punctuation and lowercase all letters.");
    }

    @Test
    public void testSanitizeTrimsAndCollapsesWhitespace() {
        String input = "   Too   many    spaces   here.   ";
        String expected = "too many spaces here";
        assertEquals(expected, TextSanitizer.sanitize(input), "Should trim edges and collapse multiple spaces into one.");
    }

    @Test
    public void testSanitizeHandlesContractions() {
        String input = "We don't want any error's.";
        String expected = "we dont want any errors";
        assertEquals(expected, TextSanitizer.sanitize(input), "Should remove apostrophes based on current isLetterOrDigit implementation.");
    }

    @Test
    public void testSanitizeEmptyOrBlankString() {
        assertEquals("", TextSanitizer.sanitize(""), "Empty string should return empty string.");
        assertEquals("", TextSanitizer.sanitize("     "), "Blank string should return empty string.");
    }
}