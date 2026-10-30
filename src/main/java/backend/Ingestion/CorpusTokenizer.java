package backend.Ingestion;
import java.util.Iterator;

public class CorpusTokenizer implements Iterator<Token> {
    GutenbergIngestor corpus;
    String sentence;
    int index;
    int position;

    public CorpusTokenizer(GutenbergIngestor ingestor){
        this.corpus = ingestor;
        sentence = corpus.getSanitizedSentence();
        index = 0;
        position = 0;
    }

    /**
     * @return Returns whether another Token can be parsed from the corpus.
     * @author Gabriel Goble (ggg220000)
     * @DateWritten 10/20/2026
     */
    public boolean hasNext() {
        if(sentence.length() <= index)
        {
            sentence = corpus.getSanitizedSentence();
            index = 0;
            position = 0;
            return sentence != null;
        }
        return true;
    }

    /**
     * @return The next token from the corpus.
     * @author Gabriel Goble (ggg220000)
     * @DateWritten 10/20/2026
     */
    public Token next() {
        //Ensures there is a next token for safety
        if(!hasNext())
            return null;

        //find the end index of the next word in the string
        int endOfWord = sentence.indexOf(" ", index);
        if(endOfWord == -1) {
            endOfWord = sentence.length();
        }

        //build the token object and update relevant fields
        Token token = new Token(sentence.substring(index, endOfWord), position == 0, endOfWord == sentence.length(), position);
        index = endOfWord + 1;
        position++;
        return token;
    }
}
