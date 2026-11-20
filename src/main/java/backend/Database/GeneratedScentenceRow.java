package backend.Database;
//Written by Ayush Kafley (axk220167) for CS4485.0W1, Sentence-Builder Project
//November 19. 2026
//record to hold all fields of the generated sentences table's rows
import java.time.LocalDateTime;

public record GeneratedScentenceRow(String sentence, String algorithm, LocalDateTime generatedAt) {}
