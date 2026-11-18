package backend.Ingestion;
import backend.Database.DatabaseManager;
import backend.Database.TransitionData;
import backend.Database.WordData;

import java.sql.SQLException;
import java.util.*;

import static backend.Database.DatabaseManager.DB_URL;

public class CorpusRepository{

    //ingest a full file at the filepath (open, metadata, tokenize, save, mark imported)
    public static void ingestFile(DatabaseManager db, String path) throws SQLException{
        GutenbergIngestor ing = new GutenbergIngestor();
        boolean success = ing.openFile((path));
        if(!success) {
            System.out.println("File not found");
            return;
        }
        success = ing.extractMetadata();
        if(!success){
            System.out.println("Metadata extraction failed");
            return;
        }

        DocumentMetadata metadata = ing.getMetadata();
        System.out.println(metadata.getTitle());
        System.out.println(metadata.getAuthor());
        System.out.println(metadata.getDate());
        System.out.println(metadata.getBookNumber());
        System.out.println(metadata.getSizeBytes());
        //don't import the file if it is already in the DB
        if(db.hasImportedFile(metadata.getTitle())){
            System.out.println("File " + metadata.getTitle() + " already imported to DB");
            return;
        }

        //two maps fo the word and transistion classes to keep track of the incoming words and transitions
        Map<String, WordData> words = new HashMap<>();
        Map<String, TransitionData> transitions = new HashMap<>();
        int totalWord = getTokens(new CorpusTokenizer(ing), words, transitions);

        db.batchAdd(words.values(), transitions.values());
        db.addImportedFile(metadata.getTitle(), totalWord);
        System.out.println("total words: " + totalWord);
    }

    //method to get the total words and to populate the words and transitions
    public static int getTokens(Iterator<Token> tokenizer, Map<String, WordData> words, Map<String, TransitionData> transitions){
        int totalWords = 0;
        Token prev = null;
        while(tokenizer.hasNext()){
            Token token = tokenizer.next();
            String word = token.getWord();
            //avoid adding empty tokens into a blank row
            if(word == null || word.isEmpty())
                continue;
            totalWords++;


            //check that if the incoming word is new or needs to have its count updated
            WordData exists = words.get(word);
            if(exists == null)
                words.put(word, new WordData(word, token.isStart(), token.isEnd()));
            else
                exists.addOccurrence(token.isStart(), token.isEnd());

            //for transitions check that it's in the same sentances (new sentances have new chains)
            if(prev != null && !prev.isEnd()){
                //check that this pair exists
                //all pairs are to be stored as word + nullSeperator + word so that it's easy to idifferenciate
                String key = prev.getWord() + "\0" + word;
                TransitionData t = transitions.get(key);
                //if this is a new transition not in the list
                if(t == null)
                    transitions.put(key, new TransitionData(prev.getWord(), word));
                else //the transition exists
                    t.addFrequency();
            }
            prev = token;
        }

        return totalWords;
    }

    //ingest sentances (sanitize, tokenize, count and save words)
    public static void ingestSentences(DatabaseManager db, List<String> sentences) throws SQLException {
        Map<String, WordData> words = new HashMap<>();
        Map<String, TransitionData> transitions = new HashMap<>();

        int totalWords = 0;
        for(String sentence : sentences)
            totalWords += getTokens(new CorpusTokenizer(sentence), words, transitions);
        if(totalWords > 0)
            db.batchAdd(words.values(), transitions.values());
    }

    public static void main(String[] args){
        long dbStart = System.nanoTime();
        try(DatabaseManager db = new DatabaseManager(DB_URL, "root2", "password0")){ //replace with your user + password
            ingestFile(db, "documents\\project_gutenberg\\a-farewell-to-arms.txt");
        } catch(SQLException e){
            System.out.println("Can't connect to DB error: " + e.getMessage());
        } finally {
            System.out.println("DB took " + (System.nanoTime() - dbStart) / 1_000_000 + "ms to exec");
        }

    }
}