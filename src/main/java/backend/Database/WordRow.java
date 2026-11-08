package backend.Database;
//Written by Ayush Kafley (axk220167) for CS4485.0W1, Sentence-Builder Project
//November 3, 2026

//one row of the words table joined with how many unique words follow it
public record WordRow(String word, int total, int start, int end, int follows){}
