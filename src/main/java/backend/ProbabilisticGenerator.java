/*
 *  ProbabilisticGenerator class
 *
 * Interfaces with the database to generate next words based on their
 * relative weighting withing the data set.
 *
 * Written by Gabriel Goble (GGG220000) for CS4488.0W1, Sentance-Builder Project,
 * October 30, 2026
 */


package backend;
import backend.Database.DatabaseManager;
import backend.Database.TransitionData;
import backend.Database.WordData;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


//markov chain sentence generator that picks each word by its percentage
public class ProbabilisticGenerator {
    public static final String END = "";
    private static final Random random = new Random();



    private static String getRandomNextWord(WordData currentWord, ArrayList<TransitionData> transitions)
    {
        int randomWord = random.nextInt(currentWord.getTotalFrequency());
        String nextWord = "";
        for(TransitionData data : transitions) {
            nextWord = data.getNextWord();
            randomWord -= data.getFrequency();
            if(randomWord <= 0) {
                break;
            }
        }

        return nextWord;
    }

    /**
     * Generates a sentence of length sentenceLength + 1 starting with your seed word.
     * @param seedWord Word you want the sentence to start with
     * @param sentenceLength How many additional words should be after the seed
     * @param db A DatabaseManager object
     * @DateWritten 10/30/2026
     * @author Gabriel Goble (GGG220000)
     */
    private static void generateSentence(String seedWord, int sentenceLength, DatabaseManager db)
    {
        String currentWord = seedWord;
        System.out.print(currentWord + " ");
        for (int i = 0; i < sentenceLength; i++) {
            ArrayList<TransitionData> transitions = db.getTransitions(currentWord);
            WordData word = db.getWordData(currentWord);

            currentWord = getRandomNextWord(word, transitions);
            System.out.print(currentWord + " ");
        }
        System.out.println();
    }
    public static void main(String[] args) {
        try (DatabaseManager db = new DatabaseManager(DatabaseManager.DB_URL, "root", "")) { //replace with your password
            generateSentence("the", 15, db);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }


    }

    private static void printWeights(Map<String, Integer> weights) { //prints the weights of the words
        int total = 0;
        for (int weight : weights.values()) {
            total += Math.max(weight, 0); //adds the weight to the total
        }
        List<String> keys = new ArrayList<>(weights.keySet());
        Collections.sort(keys); //sorts the keys alphabetically
        for (String key : keys) {
            int weight = weights.get(key); //gets the weight of the word
            String label = key.isEmpty() ? "(end)" : key; //if the word is empty, it prints "(end)" otherwise it prints the word
            System.out.printf("  %s  %d/%d%n", label, weight, total);
        }
    }
}
