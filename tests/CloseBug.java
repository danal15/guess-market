import engine.api.GMEngine;
import engine.api.dto.*;
import engine.core.GuessMarketEngine;
import engine.model.OrderSide;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * The close that used to throw.
 *
 * A mint splits the base value between two prices, and in binary the two halves
 * do not always add back to the whole. The event account was then a fraction of
 * a fraction of a cent below zero, and returning "what is left" to the market
 * maker tried to withdraw a negative amount, which the account refuses. The
 * event had already paid its winners by then and was still marked active, so
 * the market maker could close it again and pay them a second time.
 */
public class CloseBug {

    static int pass, fail;

    static void ok(String label, boolean good, String detail) {
        System.out.printf("  [%s] %-56s %s%n", good ? "ok" : "FAIL", label, detail);
        if (good) pass++; else fail++;
    }

    static GMEngine market(int commission, boolean mint) {
        GMEngine e = GuessMarketEngine.startedEmpty();
        e.login("Mm");
        e.login("A");
        e.login("B");
        e.loadFunds("Mm", 1000);
        e.loadFunds("A", 1000);
        e.loadFunds("B", 1000);
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Guess-Market><GM-events><GM-event name=\"Mint\">"
                + "<description>d</description>"
                + "<commission type=\"on-close\">" + commission + "</commission>"
                + "<GM-options><GM-option>HOME</GM-option><GM-option>AWAY</GM-option></GM-options>"
                + "<GM-method><GM-order-book allow-mint=\"" + mint + "\" initial=\"0\" d=\"1\"/>"
                + "</GM-method></GM-event></GM-events></Guess-Market>";
        e.uploadMarketFile(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "Mm");
        return e;
    }

    static int eventId(GMEngine e) {
        return e.getEvents(EventFilterDTO.all()).get(0).getId();
    }

    public static void main(String[] args) {
        System.out.println("A. the exact case that used to throw: 0.01 and 0.99, three shares");
        GMEngine e = market(0, true);
        int id = eventId(e);
        e.openEvent(id, "Mm");
        e.placeOrder(id, "A", 1, OrderSide.BUY, 0.01, 3);
        OrderResultDTO minted = e.placeOrder(id, "B", 0, OrderSide.BUY, 0.99, 3);
        ok("the mint happened", minted.getMintedQuantity() == 3,
                String.valueOf(minted.getMintedQuantity()));

        double account = e.getOrderBookEventState(id).getAccountBalance();
        System.out.printf("     the event account holds %.20f%n", account);

        double before = e.getUser("B").getBalance();
        CloseResultDTO closed = null;
        String refusal = null;
        try {
            closed = e.closeEvent(id, "Mm", 0);
        } catch (RuntimeException ex) {
            refusal = ex.getMessage();
        }
        ok("closing it no longer throws", refusal == null, refusal == null ? "closed" : refusal);
        ok("the winner was paid once", closed != null && closed.getWinnersPaid() == 1,
                closed == null ? "-" : String.valueOf(closed.getWinnersPaid()));
        ok("and paid 3 x $1", Math.abs(e.getUser("B").getBalance() - (before + 3.0)) < 0.005,
                String.format("%.4f -> %.4f", before, e.getUser("B").getBalance()));
        ok("the event really is closed",
                "Closed".equals(e.getEvent(id).getStatusLabel()), e.getEvent(id).getStatusLabel());
        ok("the account was levelled, not left in the red",
                Math.abs(e.getOrderBookEventState(id).getAccountBalance()) < 0.005,
                String.format("%.20f", e.getOrderBookEventState(id).getAccountBalance()));

        double afterFirst = e.getUser("B").getBalance();
        String second = null;
        try {
            e.closeEvent(id, "Mm", 0);
        } catch (RuntimeException ex) {
            second = ex.getMessage();
        }
        ok("closing a second time is refused", second != null, String.valueOf(second));
        ok("and nobody was paid twice", e.getUser("B").getBalance() == afterFirst,
                String.format("%.4f", e.getUser("B").getBalance()));

        System.out.println();
        System.out.println("B. a sweep of every whole-cent price, at several quantities");
        int checked = 0, threw = 0, doublePaid = 0, stuckActive = 0;
        for (int cents = 1; cents <= 99; cents++) {
            for (long qty : new long[] { 1, 3, 5, 7, 13 }) {
                GMEngine m = market(0, true);
                int ev = eventId(m);
                m.openEvent(ev, "Mm");
                double price = cents / 100.0;
                m.placeOrder(ev, "A", 1, OrderSide.BUY, price, qty);
                m.placeOrder(ev, "B", 0, OrderSide.BUY, round2(1.0 - price), qty);
                double paidBefore = m.getUser("B").getBalance();
                checked++;
                try {
                    m.closeEvent(ev, "Mm", 0);
                } catch (RuntimeException ex) {
                    threw++;
                    System.out.printf("     threw at price %.2f x %d: %s%n", price, qty, ex.getMessage());
                    if (!"Closed".equals(m.getEvent(ev).getStatusLabel())) {
                        stuckActive++;
                    }
                    try {
                        m.closeEvent(ev, "Mm", 0);
                        if (m.getUser("B").getBalance() > paidBefore + qty + 0.005) {
                            doublePaid++;
                        }
                    } catch (RuntimeException ignored) {
                        // refused, which is what should happen
                    }
                }
            }
        }
        ok("no price and quantity makes the close throw", threw == 0,
                threw + " of " + checked + " threw");
        ok("none is left active after a failed close", stuckActive == 0, String.valueOf(stuckActive));
        ok("and nobody is ever paid twice", doublePaid == 0, String.valueOf(doublePaid));

        System.out.println();
        System.out.println("C. money is still conserved through a mint and a close");
        GMEngine c = market(10, true);
        int cid = eventId(c);
        double startTotal = c.getUser("Mm").getBalance() + c.getUser("A").getBalance()
                + c.getUser("B").getBalance();
        c.openEvent(cid, "Mm");
        c.placeOrder(cid, "A", 1, OrderSide.BUY, 0.37, 11);
        c.placeOrder(cid, "B", 0, OrderSide.BUY, 0.63, 11);
        c.closeEvent(cid, "Mm", 0);
        double endTotal = c.getUser("Mm").getBalance() + c.getUser("A").getBalance()
                + c.getUser("B").getBalance()
                + c.getOrderBookEventState(cid).getAccountBalance();
        ok("not a cent appeared or vanished", Math.abs(startTotal - endTotal) < 0.005,
                String.format("%.6f -> %.6f", startTotal, endTotal));

        System.out.println();
        System.out.printf("CLOSE-BUG: %d passed, %d failed%n", pass, fail);
        System.exit(fail == 0 ? 0 : 1);
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
