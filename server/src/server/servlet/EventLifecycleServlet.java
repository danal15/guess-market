package server.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import server.GmServlet;

import java.io.IOException;

/**
 * Opening and closing an event. Only its market maker may do either, which the
 * engine enforces; the acting user comes from the session, so a client cannot
 * ask to do it on somebody else's behalf.
 */
@WebServlet({"/event/open", "/event/close"})
public class EventLifecycleServlet extends GmServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean closing = request.getServletPath().endsWith("/close");
        respond(response, () -> {
            String user = currentUser(request);
            int eventId = requiredInt(request, "eventId");
            if (closing) {
                return engine().closeEvent(eventId, user, requiredInt(request, "winningOption"));
            }
            engine().openEvent(eventId, user);
            return engine().getEvent(eventId);
        });
    }
}
