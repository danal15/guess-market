package server.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import server.GmServlet;

import java.io.IOException;

/**
 * Takes a name and, if nobody else has it, admits its owner. There are no
 * passwords: the exercise asks for a name and nothing more.
 */
@WebServlet("/login")
public class LoginServlet extends GmServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(response, () -> {
            String name = required(request, "name");
            Object user = engine().login(name);
            // Only after the engine accepted the name, so a rejected attempt
            // leaves the connection logged in as nobody.
            remember(request, name.trim());
            return user;
        });
    }
}
