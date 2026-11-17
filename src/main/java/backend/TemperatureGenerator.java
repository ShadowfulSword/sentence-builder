/*
 *  TemperatureGenerator class
 *
 * Extends the probabilistic generator class by overriding a class and changing how weights are
 * distributed.
 *
 *
 * Written by Gabriel Goble (GGG220000) for CS4488.0W1, Sentance-Builder Project,
 * November 5, 2026
 *
 * Updated by Andrew Park (AXP220253) for CS 4485.0W1, Sentence-Buiilder Project,
 * November 16, 2026
 */


package backend;

import backend.Database.DatabaseManager;
import backend.Database.TransitionData;
import backend.Database.WordData;

import java.sql.SQLException;
import java.util.*;

import static java.lang.Math.max;
import static java.lang.Math.pow;

//markov chain sentence generator that picks each word by its percentage
public class TemperatureGenerator extends ProbabilisticGenerator{
    private float temperature = 1.0f;

    public TemperatureGenerator() {
        super();
    }

    public TemperatureGenerator(DatabaseManager db) {
        super(db);
    }

    @Override
    protected ArrayList<TransitionData> getTransitions(WordData currentWord) {
        ArrayList<TransitionData> transitions =  db.getTransitions(currentWord.getWord());
        if (transitions == null || transitions.isEmpty()) {
            return transitions;
        }

        //if temperature is sufficently low, always pick the most frequent token
        if(temperature <= 1e-4) {
            ArrayList<TransitionData> greedy = new ArrayList<>();
            TransitionData first = transitions.get(0);
            first.setFrequency(max(1, currentWord.getTotalFrequency()));
            greedy.add(first);
            return greedy;
        }

        float invertedTemperature = 1f/max(temperature, 1e-5f);
        int baseTotal = max(1, currentWord.getTotalFrequency());

        for(TransitionData transition : transitions) {
            double scaled = pow((double)transition.getFrequency() / baseTotal, invertedTemperature) * 10000.0;
            int newFrequency = max(1, (int)Math.round(scaled));
            transition.setFrequency(newFrequency);
        }

        return transitions;
    }

//    @Override
//    public String getRandomNextWord(WordData currentWord)
//    {
//        if(db == null)
//            return null;
//        //get the list of possible next words
//        ArrayList<TransitionData> transitions =  getTransitions(currentWord);
//
//        int totalFrequency = 0;
//
//        for(TransitionData transition : transitions)
//            totalFrequency += transition.getFrequency();
//
//
//
//        //get a random number that will be associated with a specific word
//        int randomWord = random.nextInt(totalFrequency);
//        String nextWord = "";
//
//        //until our random number is reduced to zero, repeatedly subtract word weights
//        //from it, this ensures the probability a word is selected is proportional on its weight
//        for(TransitionData data : transitions) {
//            nextWord = data.getNextWord();
//            randomWord -= data.getFrequency();
//            if(randomWord <= 0) {
//                break;
//            }
//        }
//
//        //return the word
//        return nextWord;
//    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }

    public float getTemperature() {
        return this.temperature;
    }
}
