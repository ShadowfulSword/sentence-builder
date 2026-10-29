/*
*  WordData Class
*
* This class is the definition for the info a word needs for the database. One "word" is
* defined by the word itself, the amount of total occurance and the amount of start and
* end occurances.
*
* Written by Ayush Kafley (axk220167) for CS4488.0W1, Sentance-Builder Project,
* October 25, 2026
*/

package backend.Database;

public class WordData {

    private final String word;
    private int totalFrequency = 1;
    private int startFrequency;
    private int endFrequency;

    public WordData(String word){
        this.word = word;
    }

    public WordData(String word, boolean isStart, boolean isEnd){
        this.word = word;
        if(isStart)
            startFrequency++;
        if(isEnd)
            endFrequency++;
    }

    //a function to update a word's frequency
    //since it will only be called when a freq change happens, always increment total freq
    public void addOccurrence(boolean isStart, boolean isEnd){
        totalFrequency ++;
        if(isStart)
            startFrequency++;
        if(isEnd)
            endFrequency++;
    }

    //getter methods
    public String getWord(){return word;}
    public int getTotalFrequency(){return totalFrequency;}
    public int getStartFrequency(){return startFrequency;}
    public int getEndFrequency(){return endFrequency;}

}
