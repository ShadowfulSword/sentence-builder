package Database;

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


    public void addOccurrence(boolean isStart, boolean isEnd){
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
