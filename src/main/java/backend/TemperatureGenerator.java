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
    protected ArrayList<TransitionData> getTransitions(WordData currentWord)
    {
        ArrayList<TransitionData> transitions =  db.getTransitions(currentWord.getWord());

        //if temperature is sufficently low, always pick the most frequent token
        if(temperature <= 1e-4)
        {
            transitions.getFirst().setFrequency(currentWord.getTotalFrequency());
            return transitions;
        }

        float invertedTemperature = 1f/max(temperature, 1e-5f);

        for(int i = 0; i < transitions.size(); i++)
        {
            transitions.get(i).setFrequency((int)pow(((float)transitions.get(i).getFrequency()/currentWord.getTotalFrequency()), invertedTemperature)*currentWord.getTotalFrequency());
        }

        return transitions;

    }

    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }
}
