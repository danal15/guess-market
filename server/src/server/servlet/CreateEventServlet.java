package server.servlet;

import engine.api.dto.NewEventRequestDTO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import server.GmServlet;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Creating an event from nothing, which makes its creator the market maker.
 *
 * The request carries the whole description as JSON rather than a dozen
 * parameters, because the fields differ by trading method and the client
 * already holds exactly this object.
 */
@WebServlet("/event/new")
public class CreateEventServlet extends GmServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(response, () -> {
            String user = currentUser(request);
            NewEventRequestDTO wanted = readBody(request);
            if (wanted == null) {
                throw new IllegalArgumentException("The request carried no event to create.");
            }
            return engine().createEvent(wanted, user);
        });
    }

    private NewEventRequestDTO readBody(HttpServletRequest request) throws IOException {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return gson().fromJson(body.toString(), NewEventRequestDTO.class);
    }
}
