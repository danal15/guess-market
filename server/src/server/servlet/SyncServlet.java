package server.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import server.GmServlet;

import java.io.IOException;

/**
 * The one endpoint every client polls. It answers with the whole of what that
 * user's screen shows, gathered in a single pass over the market.
 *
 * Everything on screen comes back together, so a client never shows a balance
 * from before a trade beside holdings from after it. Chat is the exception and
 * is sent as a delta, since it only ever grows.
 *
 * The whole list of events comes back and the client applies its own filters to
 * it. That keeps the three filter dimensions out of the query string, where an
 * absent value and a false one look alike and would quietly hide half the market.
 */
@WebServlet("/sync")
public class SyncServlet extends GmServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(response, () -> engine().getSnapshot(
                currentUser(request),
                selectedEvent(request),
                chatFrom(request)));
    }

    /** Null when no event is open on the screen, so no detail is gathered. */
    private Integer selectedEvent(HttpServletRequest request) {
        String value = request.getParameter("eventId");
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'eventId' must be a whole number, not '" + value + "'.");
        }
    }

    private int chatFrom(HttpServletRequest request) {
        String value = request.getParameter("chatFrom");
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
