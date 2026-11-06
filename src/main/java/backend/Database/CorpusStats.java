package backend.Database;
//Written by Ayush Kafley (axk220167) for CS4485.0W1, Sentence-Builder Project
//November 3, 2026

//record to hold the thre top line numbers in the report page
//on empty import topStartWord = null
public record CorpusStats(int words, int transitions, String topStartWord){}
