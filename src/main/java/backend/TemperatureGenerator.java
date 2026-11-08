/*
 *  TemperatureGenerator class
 *
 * Extends the probabilistic generator class by overriding a class and changing how weights are
 * distributed.
 *
 *
 * Written by Gabriel Goble (GGG220000) for CS4488.0W1, Sentance-Builder Project,
 * November 5, 2026
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
    private float temperature = 1;
    public TemperatureGenerator() {
        super();
    }

    @Override
    protected ArrayList<TransitionData> getTransitions(WordData currentWord )
    {
        ArrayList<TransitionData> transitions =  db.getTransitions(currentWord.getWord());

        //if temperature is sufficently low, always pick the most frequent token
        if(temperature <= 1e-4)
        {
            transitions.getFirst().setFrequency(currentWord.getTotalFrequency());
            return transitions;
        }

        float invertedTemperature = 1f/max(temperature, 1e-5f);

        long frequencySum = 0;
        for(int i = 0; i < transitions.size(); i++)
        {
            int newFrequency = (int)(pow(((float)transitions.get(i).getFrequency()/currentWord.getTotalFrequency()), invertedTemperature)* currentWord.getTotalFrequency());
            transitions.get(i).setFrequency(newFrequency);
            frequencySum += newFrequency;
        }

        return transitions;
    }

    @Override
    public String getRandomNextWord(WordData currentWord)
    {
        if(db == null)
            return null;
        //get the list of possible next words
        ArrayList<TransitionData> transitions =  getTransitions(currentWord);

        int totalFrequency = 0;

        for(TransitionData transition : transitions)
            totalFrequency += transition.getFrequency();



        //get a random number that will be associated with a specific word
        int randomWord = random.nextInt(totalFrequency);
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

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }
}
