package server.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import server.GmServlet;

import java.io.IOException;

/**
 * Bonus: saying something to everyone. Only sending needs an endpoint of its
 * own; the messages themselves arrive with every poll, so a reader never has to
 * ask for them separately.
 */
@WebServlet("/chat")
public class ChatServlet extends GmServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(response, () -> engine().postChatMessage(
                currentUser(request), required(request, "text")));
    }
}
