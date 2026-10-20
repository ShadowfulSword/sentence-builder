/*
 * Document Metadata Class
 * Stores information about a certain word, along with providing the getters
 * and setters for said information.
 *
 * Written by Gabriel Goble (GGG220000) for CS4485.0W1, Sentence-Builder Project,
 * starting October 16, 2026
 */


public class Token {
    String word;
    boolean isStart;
    boolean isEnd;
    int index;

    public Token(String word, boolean isStart, boolean isEnd, int index){
        this.word = word;
        this.isStart = isStart;
        this.isEnd = isEnd;
        this.index = index;
    }
    public Token(){}

    public String getWord() {
        return word;
    }
    public void setWord(String word) {
    this.word = word;
    }

    public boolean isStart() {
        return isStart;
    }
    public void setStart(boolean isStart) {
        this.isStart = isStart;
    }

    public boolean isEnd() {
        return isEnd;
    }
    public void setEnd(boolean isEnd) {
        this.isEnd = isEnd;
    }

    public int getIndex() {
        return index;
    }
    public void setIndex(int index) {
        this.index = index;
    }
}
