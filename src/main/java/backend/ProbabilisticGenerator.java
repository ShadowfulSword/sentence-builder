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
    protected static final Random random = new Random();
    protected DatabaseManager db = null;

    public ProbabilisticGenerator() {
        try { //replace with your password
            this.db = new DatabaseManager(DatabaseManager.DB_URL, "root", "");
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Takes a word as input and returns a new word which is selected proportionally to how often
     * the new word appeared after the current word in the training corpora.
     * @param currentWord The word we want to act as the seed when selecting the next word
     * @return A random next word
     * @DateWritten 10/29/26 Updated: 11/5/2026
     * @author Gabriel Goble, Alex Chan
     */
    public String getRandomNextWord(WordData currentWord)
    {
        if(db == null)
            return null;
        //get the list of possible next words
        ArrayList<TransitionData> transitions =  getTransitions(currentWord);

        //get a random number that will be associated with a specific word
        int randomWord = random.nextInt(currentWord.getTotalFrequency());
        String nextWord = "";

        //until our random number is reduced to zero, repeatedly subtract word weights
        //from it, this ensures the probability a word is selected is proportional on its weight
        for(TransitionData data : transitions) {
            nextWord = data.getNextWord();
            randomWord -= data.getFrequency();
            if(randomWord <= 0) {
                break;
            }
        }

        //return the word
        return nextWord;
    }

    protected ArrayList<TransitionData> getTransitions(WordData currentWord)
    {
        return db.getTransitions(currentWord.getWord());
    }


    /**
     * Generates a sentence of length sentenceLength + 1 starting with your seed word.
     * @param seedWord Word you want the sentence to start with
     * @param sentenceLength How many additional words should be after the seed
     * @DateWritten 10/30/2026
     * @author Gabriel Goble (GGG220000)
     */
    public String generateSentence(String seedWord, int sentenceLength)
    {
        if(db == null)
            return null;

        StringBuilder sentence = new StringBuilder();

        String currentWord = seedWord;
        for (int i = 0; i < sentenceLength; i++) {
            WordData word = db.getWordData(currentWord);

            currentWord = getRandomNextWord(word);
            sentence.append(currentWord).append(" ");
        }

        return sentence.toString();
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
