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

    private final Map<String, Integer> startCounts = new HashMap<>(); //frequency it starts with this word
    private final Map<String, Integer> endCounts = new HashMap<>(); //frequency it ends with this word
    private final Map<String, Map<String, Integer>> transitions = new HashMap<>(); //frequency it transitions to this word from the previous word
    private final Random random; // random number generator
    private final int maxWords; // maximum number of words in a sentence

    public ProbabilisticGenerator() { //default constructor
        this(new Random(), 30);
    }

    public ProbabilisticGenerator(Random random, int maxWords) { //stores the random number and the max words
        this.random = random;
        this.maxWords = maxWords;
    }

    public String generate() {
        String word = pickByPercentage(startCounts); //picks a starting word using its share of the start counts
        if (word == null) { // skips if no word is picked
            return "";
        }

        StringBuilder sentence = new StringBuilder(word);//starts with the word picked from the previous step
        for (int count = 1; count < maxWords; count++) { //max sure it doesn't go over the max words
            String next = nextWord(word); //picks the next word using its percentage
            if (next == null) { //skips if no word is picked
                break; //stops the loop
            }
            sentence.append(' ').append(next); //adds the next word to the sentence
            word = next; //updates the current word
        }
        return sentence.toString();
    }

    public String nextWord(String current) { //picks next word based on current word and the transition frequency of following words
        String choice = pickByPercentage(nextWeights(current)); //picks a follower using its share of the counts
        if (choice == null || END.equals(choice)) { //skips if no word is picked or the word is the end of the sentence
            return null;
        }
        return choice;
    }

    public Map<String, Integer> nextWeights(String word) {
        Map<String, Integer> weights = new HashMap<>();
        Map<String, Integer> followers = transitions.get(word);
        if (followers != null) {
            weights.putAll(followers);
        }
        int endCount = endCounts.getOrDefault(word, 0);
        if (endCount > 0) {
            weights.merge(END, endCount, Integer::sum);
        }
        return weights;
    }

    private String pickByPercentage(Map<String, Integer> weights) { //chance of a word is its count divided by the total
        int total = 0;
        for (int weight : weights.values()) { //iterates through the weights
            if (weight > 0) { //adds the weight to the total if it is greater than 0
                total += weight;
            }
        }
        if (total <= 0) { //skips if the total is less than or equal to 0
            return null;
        }

        int roll = random.nextInt(total); //a number from 0 up to the total, so each count is one slice of 100 percent
        int cumulative = 0;
        for (Map.Entry<String, Integer> entry : weights.entrySet()) { //iterates through the weights
            int weight = entry.getValue(); //gets the weight of the word
            if (weight <= 0) { //skips if the weight is less than or equal to 0
                continue;
            }
            cumulative += weight; //this word owns the next `weight` numbers, which is weight/total of the roll
            if (roll < cumulative) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static void main(String[] args) {
        ProbabilisticGenerator generator = new ProbabilisticGenerator(); //creates a new generator
        Random random = new Random();
        String currentWord = "the";


        try (DatabaseManager db = new DatabaseManager(DatabaseManager.DB_URL, "root", "")) { //replace with your password
            System.out.print(currentWord + " ");
            for (int i = 0; i < 20; i++) {
                ArrayList<TransitionData> transitions = db.getTransitions(currentWord);
                WordData word = db.getWordData(currentWord);
                int randomWord = random.nextInt(word.getTotalFrequency());
                //System.out.print(currentWord + " ");
                for(TransitionData data : transitions) {
                    currentWord = data.getNextWord();
                    randomWord -= data.getFrequency();
                    if(randomWord <= 0) {
                        break;
                    }
                }

                System.out.print(currentWord + " ");
            }
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
