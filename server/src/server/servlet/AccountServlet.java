package server.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import server.GmServlet;

import java.io.IOException;

/** Money the user puts into their own account. */
@WebServlet("/funds")
public class AccountServlet extends GmServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(response, () -> engine().loadFunds(
                currentUser(request), requiredDouble(request, "amount")));
    }
}
