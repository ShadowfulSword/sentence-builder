package Database;

public class TransitionData {
    private final String word;
    private final String nextWord;
    private int frequency = 1;

    public TransitionData(String word, String nextWord){
        this.word = word;
        this.nextWord = nextWord;
    }

    public void addFrequency(){frequency++;}
    public String getWord(){return word;}
    public String getNextWord(){return nextWord;}
    public int getFrequency(){return frequency;}
}
