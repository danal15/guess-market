package task;

import engine.api.GMEngine;
import javafx.concurrent.Task;

/**
 * Loads a market file off the interface thread. Parsing itself is instant, so
 * the task walks through a few steps with a short pause between them to give
 * the progress bar something real to show.
 */
public class LoadFileTask extends Task<Void> {

    private static final int STEPS = 10;
    private static final long STEP_PAUSE_MILLIS = 130;

    private final GMEngine engine;
    private final String path;

    public LoadFileTask(GMEngine engine, String path) {
        this.engine = engine;
        this.path = path;
    }

    @Override
    protected Void call() throws Exception {
        updateMessage("Reading the file...");
        for (int step = 1; step <= STEPS / 2; step++) {
            Thread.sleep(STEP_PAUSE_MILLIS);
            updateProgress(step, STEPS);
        }

        updateMessage("Checking the contents...");
        // Any problem with the file surfaces here and is carried to the failure handler.
        engine.loadMarketFile(path);

        for (int step = STEPS / 2 + 1; step <= STEPS; step++) {
            Thread.sleep(STEP_PAUSE_MILLIS);
            updateProgress(step, STEPS);
        }
        updateMessage("Done");
        return null;
    }
}
