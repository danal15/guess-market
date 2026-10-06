package server.servlet;

import engine.model.OrderSide;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import server.GmServlet;

import java.io.IOException;

/**
 * Taking part in an event: asking what something would cost, and then doing it.
 *
 * A quote is a GET because it changes nothing, and the real thing is a POST.
 * Both LMSR purchases and order book orders live here because they are the same
 * action wearing two shapes, and the engine already keeps them apart.
 */
@WebServlet({"/trade/lmsr-quote", "/trade/lmsr-buy", "/trade/order-quote", "/trade/order"})
public class TradeServlet extends GmServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getServletPath();
        respond(response, () -> {
            String user = currentUser(request);
            int eventId = requiredInt(request, "eventId");
            int option = requiredInt(request, "option");
            long quantity = requiredLong(request, "quantity");
            if (path.endsWith("lmsr-quote")) {
                return engine().quoteLmsrPurchase(eventId, user, option, quantity);
            }
            return engine().quoteOrder(eventId, user, option, side(request),
                    requiredDouble(request, "price"), quantity);
        });
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getServletPath();
        respond(response, () -> {
            String user = currentUser(request);
            int eventId = requiredInt(request, "eventId");
            int option = requiredInt(request, "option");
            long quantity = requiredLong(request, "quantity");
            if (path.endsWith("lmsr-buy")) {
                return engine().buyLmsrShares(eventId, user, option, quantity);
            }
            return engine().placeOrder(eventId, user, option, side(request),
                    requiredDouble(request, "price"), quantity);
        });
    }

    /**
     * Sent as the enum's own name rather than its label, so the wire carries one
     * fixed spelling and the labels stay what they are: text for people.
     */
    private OrderSide side(HttpServletRequest request) {
        String value = required(request, "side");
        try {
            return OrderSide.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'side' must be BUY or SELL, not '" + value + "'.");
        }
    }
}
