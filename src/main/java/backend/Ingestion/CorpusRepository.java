package backend.Ingestion;
import backend.Database.DatabaseManager;
import backend.Database.TransitionData;
import backend.Database.WordData;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class CorpusRepository{
    private static final String DB_URL = "jdbc:mysql://localhost:3306/sentence_builder?rewriteBatchedStatements=true"; //past the ? allows the DB to rewrite the batch calls together rather than as individual transactions when pased through
    public static void main(String[] args){
        long metaStart = System.nanoTime();

        GutenbergIngestor ing = new GutenbergIngestor();
        boolean success = ing.openFile("Gutenberg\\testcorpus.txt");
        if(!success) {
            System.out.println("File not found");
            return;
        }

        success = ing.extractMetadata();
        if (!success) {
            System.out.println("Metadata extraction failed");
            return;
        }
        System.out.println("Metadata took " + (System.nanoTime() - metaStart)/1_000_000 + "ms to exec");

        DocumentMetadata metadata = ing.getMetadata();
        System.out.println(metadata.getTitle());
        System.out.println(metadata.getAuthor());
        System.out.println(metadata.getDate());
        System.out.println(metadata.getBookNumber());
        System.out.println(metadata.getSizeBytes());

        //two maps fo the word and transistion classes to keep track of the incoming words and transitions
        Map<String, WordData> words = new HashMap<>();
        Map<String, TransitionData> transitions = new HashMap<>();

        long dbStart = System.nanoTime();
        try (DatabaseManager db = new DatabaseManager(DB_URL, "root", "")) { //replace with your password
            //don't import the file if it is already in the DB
            if (db.hasImportedFile(metadata.getTitle())) {
                System.out.println("File " + metadata.getTitle() + " already imported to DB");
                return;
            }

//        for(int i = 0; i < 10; i++)
//            System.out.println(ing.getSanitizedSentence() + "\n");

            CorpusTokenizer tokenizer = new CorpusTokenizer(ing);
            int totalWords = 0;
            Token prev = null;
            while (tokenizer.hasNext()) {
                Token token = tokenizer.next();
                String word = token.getWord();
                totalWords++;

                //check that if the incoming word is new or needs to have its count updated
                WordData exists = words.get(word);
                if(exists == null)
                    words.put(word, new WordData(word, token.isStart(), token.isEnd()));
                else
                    exists.addOccurrence(token.isStart(), token.isEnd());

                //for transitiosn check that it's in the same sentances (new sentances have new chains)
                if(prev != null && !prev.isEnd()){
                    //check that this pair exists
                    //all pairs are to be stored as word + nullSeperator + word so that it's easy to diffrenciate
                    String key = prev.getWord() + "\0" + word;  //build the key for the table that is the path of words taken prev -> current
                    TransitionData t = transitions.get(key);
                    //if this is a new transition not in the list
                    if(t == null)
                        transitions.put(key, new TransitionData(prev.getWord(), word));
                    else    //the transition exists
                        t.addFrequency();

                }
                prev = token;
                //bound the memory by using a set size that the maps can be; we dont need a forever growing list if we add them to DB
                //avoid memory leaks when importing tons of text

                //System.out.println(token.getWord() + " " + token.isStart() + " " + token.isEnd());
            }
            db.batchAdd(words.values(), transitions.values());
            db.addImportedFile(metadata.getTitle(), totalWords);
            System.out.println("total words: " + totalWords);
        } catch (SQLException e) {
            System.out.println("Can't connect to DB error: " + e.getMessage());
        } finally {
            System.out.println("DB took " + (System.nanoTime() - dbStart) / 1_000_000 + "ms to exec");
        }
    }
}