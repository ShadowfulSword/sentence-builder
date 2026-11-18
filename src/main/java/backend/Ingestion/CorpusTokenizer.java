package backend.Ingestion;
import java.util.Iterator;
import java.util.NoSuchElementException;

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

    //Ayush Kafley (axk220167) 11/17/2026: Added new constructor for using one sentance. used to self ingest generated sentances into the DB
    public CorpusTokenizer(String sentence){
        this.corpus = null;
        this.sentence = TextSanitizer.sanitize(sentence);
        this.index = 0;
        this.position = 0;
    }

    /**
     * @return Returns whether another Token can be parsed from the corpus.
     * @author Gabriel Goble (ggg220000)
     * @DateWritten 10/20/2026
     *
     * edited by Yaseen Hafid (yxh190038) 11/06/2026 issue #18
     * edited by Ayush Kafley (axk220167) 11/17/2026 added a check to differenciate if has next is use in single sentance ingestion
     */
    public boolean hasNext() {
        // #18: An empty or exhausted corpus has no sentence to measure (yaseen)
        if (sentence == null) {
            return false;
        }

        if(sentence.length() <= index)
        {
            if(corpus == null) return false; //check for if it's in senctance. corpus will only be null if created for single sentance
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
        // #18: follow the Iterator contract when no tokens remain (yaseen)
        if (!hasNext()) {
            throw new NoSuchElementException("No more tokens in the corpus");
        }
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
