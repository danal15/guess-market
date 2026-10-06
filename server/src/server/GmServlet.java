package server;

import com.google.gson.Gson;
import engine.api.GMEngine;
import engine.api.exception.InvalidMarketFileException;
import engine.api.exception.TradingException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * What every endpoint in the application shares: reaching the engine, knowing
 * who is asking, writing JSON back, and turning a refusal from the engine into
 * an answer the client can show to a person.
 *
 * Subclasses write the one line that calls the engine and nothing else, which
 * keeps the servlets thin enough to be obviously correct and leaves all the
 * real behaviour in the engine where it can be tested without a server.
 */
public abstract class GmServlet extends HttpServlet {

    private static final String USER_ATTRIBUTE = "guess-market-user";
    private static final String JSON = "application/json;charset=UTF-8";

    private static final Gson GSON = new Gson();

    /** One call to the engine, with its answer serialised or its refusal reported. */
    protected interface Work {
        Object run() throws Exception;
    }

    /**
     * Runs the body and writes whatever it returns as JSON. A refusal the engine
     * raised on purpose becomes a 400 carrying its message, because those are
     * written for a person to read. Anything else is a fault of ours and becomes
     * a 500, so the two never get confused in the client.
     */
    protected void respond(HttpServletResponse response, Work work) throws IOException {
        Object answer;
        try {
            answer = work.run();
        } catch (TradingException | InvalidMarketFileException e) {
            refuse(response, HttpServletResponse.SC_BAD_REQUEST, e.getClass().getSimpleName(),
                    e.getMessage());
            return;
        } catch (IllegalArgumentException | IllegalStateException e) {
            refuse(response, HttpServletResponse.SC_BAD_REQUEST, e.getClass().getSimpleName(),
                    e.getMessage());
            return;
        } catch (NotLoggedIn e) {
            refuse(response, HttpServletResponse.SC_UNAUTHORIZED, "NotLoggedIn", e.getMessage());
            return;
        } catch (Exception e) {
            refuse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "ServerError",
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }
        // Serialising outside the engine call keeps the engine's lock held only
        // for as long as the work itself takes.
        write(response, HttpServletResponse.SC_OK, GSON.toJson(answer));
    }

    private void refuse(HttpServletResponse response, int status, String type, String message)
            throws IOException {
        write(response, status, GSON.toJson(new Refusal(type, message)));
    }

    private void write(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType(JSON);
        try (PrintWriter out = response.getWriter()) {
            out.print(body);
        }
    }

    protected GMEngine engine() {
        return EngineHolder.of(getServletContext());
    }

    protected Gson gson() {
        return GSON;
    }

    /** Logs the user in for this connection, so later requests need not name themselves. */
    protected void remember(HttpServletRequest request, String userName) {
        request.getSession(true).setAttribute(USER_ATTRIBUTE, userName);
    }

    /**
     * Who is asking, taken from the session rather than from the request. A
     * request that could name its own user would let any client act as anybody,
     * and exercise 3 says each user acts only as themselves.
     */
    protected String currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object name = session == null ? null : session.getAttribute(USER_ATTRIBUTE);
        if (name == null) {
            throw new NotLoggedIn("You are not logged in. Log in and try again.");
        }
        // A session can outlive the market it belonged to: the container may
        // bring sessions back when it restarts, but nothing here is saved, so
        // the user it names is gone. That is not logged in, whatever the cookie
        // says, and saying so sends the client back to the login screen instead
        // of leaving it arguing with a market that has never heard of it.
        String userName = (String) name;
        if (!engine().knowsUser(userName)) {
            session.invalidate();
            throw new NotLoggedIn("The server has been restarted, so everything was"
                    + " cleared. Log in again.");
        }
        return userName;
    }

    protected String required(HttpServletRequest request, String parameter) {
        String value = request.getParameter(parameter);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("The '" + parameter + "' value is missing.");
        }
        return value.trim();
    }

    protected int requiredInt(HttpServletRequest request, String parameter) {
        String value = required(request, parameter);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + parameter + "' must be a whole number, not '"
                    + value + "'.");
        }
    }

    protected long requiredLong(HttpServletRequest request, String parameter) {
        String value = required(request, parameter);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + parameter + "' must be a whole number, not '"
                    + value + "'.");
        }
    }

    protected double requiredDouble(HttpServletRequest request, String parameter) {
        String value = required(request, parameter);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + parameter + "' must be a number, not '"
                    + value + "'.");
        }
    }

    /** Raised when a request arrives without a logged in session behind it. */
    protected static final class NotLoggedIn extends RuntimeException {
        NotLoggedIn(String message) {
            super(message);
        }
    }

    /** The shape every refusal takes, so one client branch can handle them all. */
    private static final class Refusal {
        private final String type;
        private final String message;

        Refusal(String type, String message) {
            this.type = type;
            this.message = message;
        }
    }
}
