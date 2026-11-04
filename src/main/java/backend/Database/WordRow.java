package backend.Database;

//one row of the words table joined with how many unique words follow it
public record WordRow(String word, int total, int start, int end, int follows){}
