package Database;

public class WordData {

    private final String word;
    private int totalFrequency;
    private int startFrequency;
    private int endFrequency;

    private WordData(String word){
        this.word = word;
    }

    public void addOccurance(boolean isStart, boolean isEnd){
        totalFrequency ++;
        if(isStart)
            startFrequency++;
        if(isEnd)
            endFrequency++;
    }

    public String getWord(){return word;}
    public int getTotalFrequency(){return totalFrequency;}
    public int getStartFrequency(){return startFrequency;}
    public int getEndFrequency(){return endFrequency;}

}
