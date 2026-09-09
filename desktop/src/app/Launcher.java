package app;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plain entry point. The jar's Main-Class must not extend Application,
 * otherwise launching from a jar fails with "JavaFX runtime components are missing".
 */
public class Launcher {

    /**
     * Held so the level set below is not lost when the logger is collected.
     *
     * The JavaFX runtime travels inside this jar, which means it is loaded from
     * the class path rather than as a module. That works, but it makes JavaFX
     * announce an "unsupported configuration" before the window appears, which
     * reads like a fault and is not one. Only that one notice is turned off;
     * every other message the application or the runtime produces still shows.
     */
    private static final Logger JAVAFX_LOGGER = Logger.getLogger("javafx");

    public static void main(String[] args) {
        JAVAFX_LOGGER.setLevel(Level.OFF);
        MainApp.main(args);
    }
}
