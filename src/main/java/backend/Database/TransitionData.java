/*
 *  TransitionData Class
 *
 * This class is the definition for the info a word, nextWord pair or a transition needs. Each Transition object
 * will keep track of the current and next word and how frequency it happens.
 *
 * Written by Ayush Kafley (axk220167) for CS4488.0W1, Sentance-Builder Project,
 * October 25, 2026
 */

package backend.Database;

public class TransitionData {
    private final String word;
    private final String nextWord;
    private int frequency = 1;

    public TransitionData(String word, String nextWord){
        this.word = word;
        this.nextWord = nextWord;
    }

    public TransitionData(String word, String nextWord, int frequency){
        this.word = word;
        this.nextWord = nextWord;
        this.frequency = frequency;
    }

    public void addFrequency(){frequency++;} //always increment beacuse method will only  be called on needed update

    //getter methods
    public String getWord(){return word;}
    public String getNextWord(){return nextWord;}
    public int getFrequency(){return frequency;}
}
