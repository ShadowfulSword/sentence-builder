package backend.Database;
//Written by Ayush Kafley (axk220167) for CS4485.0W1, Sentence-Builder Project
//November 3, 2026

import java.time.LocalDateTime;

//one row of the imported files table in the stats tab
public record ImportedFileRow(String filename, int wordCount, LocalDateTime importDate) { }
