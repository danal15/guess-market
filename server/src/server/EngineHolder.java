package server;

import engine.api.GMEngine;
import engine.core.GuessMarketEngine;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Creates the one engine the whole web application shares and puts it where
 * every servlet can reach it.
 *
 * It has to be exactly one. Two engines would mean two markets, and users who
 * logged in to one would be invisible to the other without anything appearing
 * to be wrong. Keeping it in the servlet context rather than in a static field
 * also means a redeploy starts genuinely afresh.
 *
 * Nothing is written to disk. The market lives as long as this web application
 * does and no longer, which is what exercise 3 asks for.
 */
@WebListener
public class EngineHolder implements ServletContextListener {

    private static final String ATTRIBUTE = "guess-market-engine";

    @Override
    public void contextInitialized(ServletContextEvent event) {
        event.getServletContext().setAttribute(ATTRIBUTE, GuessMarketEngine.startedEmpty());
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        event.getServletContext().removeAttribute(ATTRIBUTE);
    }

    public static GMEngine of(ServletContext context) {
        GMEngine engine = (GMEngine) context.getAttribute(ATTRIBUTE);
        if (engine == null) {
            // Only reachable if the listener did not run, which would mean the
            // war was built wrongly. Saying so beats a NullPointerException.
            throw new IllegalStateException("The market engine was not started."
                    + " The web application did not deploy correctly.");
        }
        return engine;
    }
}
