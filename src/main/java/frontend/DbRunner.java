/*
* Runs database calls specfically for the tabs on the frontend
* Every call happens on a seperate thread so that the app isnt frozen while it executes
* Post execution, the result/err is handed back to the thread running JavaFX
* All tabs are to use this cor database connections to centeralize threadding and error handling
*
* Written by Ayush Kafley (axk220167) for CS4485.0W1, Sentence-Builder Project,
* November 5, 2026
* */

package frontend;

import backend.Database.DatabaseConfig;
import backend.Database.DatabaseManager;
import frontend.state.AppState;
import javafx.concurrent.Task;

import java.util.function.Consumer;

public class DbRunner {
    private final AppState state;

    public DbRunner(AppState state){
        this.state = state;
    }

    //a db call that is handed an openmanager and sends result back
    @FunctionalInterface
    public interface DbQuery<T>{
        T run(DatabaseManager db) throws Exception;
    }

    //get th eoriginal eception so the status can show where fail happned
    //mneeded it for debug
    private static String rootMessage(Throwable t){
        while(t.getCause() != null)
            t = t.getCause();
        return t.getMessage();
    }
    //Run the query in a background thread to not freeze the window (so we can keep showing things like progress bar)
    //Then bring it back to the JavaFX thread.
    //return result or error based on success
    //All calls must pass through her so threads and error handeling is centeralized
    //making use of standard javafx transaction template
    public <T> void rundb(DbQuery<T> query, Consumer<T> onSuccess, String err){
        Task<T> task = new Task<T>(){
            @Override
            protected T call() throws Exception{ //run this on a background thread
                try(DatabaseManager db = DatabaseConfig.open()){
                    return query.run(db);
                }
            }
        };
        //tjese are on the JavaFx thread and are allowed to touch the UI withpout freeze
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e-> state.setStatus(err + rootMessage(task.getException())));

        Thread thread = new Thread(task);
        thread.setDaemon(true); //set config to let app close even on leftover query
        thread.start();
    }

}
