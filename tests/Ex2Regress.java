import engine.api.GMEngine;
import engine.api.dto.*;
import engine.core.GuessMarketEngine;
import engine.model.OrderSide;

import java.util.List;

/**
 * Proves exercise 2 still behaves after exercise 3 changed code they share.
 *
 * It deliberately leans on the parts that were touched: the balance history now
 * derived from a movement list, the exercise 2 file format with its ids and its
 * users section, the event filter rule moved onto the DTO, the save and reload
 * of a whole market, and the money arithmetic the official simulations pin down.
 */
public class Ex2Regress {

    static String repo;
    static String scratch;
    static int pass, fail;

    static void ok(String label, boolean good, String detail) {
        System.out.printf("  [%s] %-58s %s%n", good ? "ok" : "FAIL", label, detail);
        if (good) pass++; else fail++;
    }

    static void money(String label, double actual, double expected) {
        boolean good = Math.abs(actual - expected) < 0.005;
        System.out.printf("  [%s] %-58s got %12.4f want %12.4f%n",
                good ? "ok" : "FAIL", label, actual, expected);
        if (good) pass++; else fail++;
    }

    static void count(String label, long actual, long expected) {
        boolean good = actual == expected;
        System.out.printf("  [%s] %-58s got %12d want %12d%n",
                good ? "ok" : "FAIL", label, actual, expected);
        if (good) pass++; else fail++;
    }

    static String refusal(Runnable call) {
        try {
            call.run();
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    static GMEngine loaded(String file) {
        GMEngine e = new GuessMarketEngine();
        e.loadMarketFile(repo + "/test-files/ex2/" + file);
        return e;
    }

    public static void main(String[] args) {
        repo = args[0];
        scratch = args[1];

        formatAndValidation();
        filters();
        balanceHistory();
        lmsrMoney();
        orderBookSimulation();
        saveAndReload();
        closedInvolvement();

        System.out.println();
        System.out.printf("EX2-REGRESS: %d passed, %d failed%n", pass, fail);
        System.exit(fail == 0 ? 0 : 1);
    }

    // The exercise 2 file still parses: it has ids and a users section, both of
    // which the exercise 3 reader refuses.
    static void formatAndValidation() {
        System.out.println("A. the exercise 2 file format still loads and still validates");
        GMEngine e = loaded("multiple.xml");
        count("multiple.xml event count", e.getEvents(EventFilterDTO.all()).size(), 4);
        count("multiple.xml user count", e.getUsers().size(), 3);
        ok("a user's cash came from the file", e.getUser("Tikva").getBalance() == 10000.0,
                String.valueOf(e.getUser("Tikva").getBalance()));
        ok("the market maker came from the file",
                "Avrum".equals(e.getEvent(2).getMarketMakerName()), e.getEvent(2).getMarketMakerName());
        count("small.xml event count", loaded("small.xml").getEvents(EventFilterDTO.all()).size(), 2);

        ok("error-2.xml is still rejected",
                refusal(() -> loaded("error-2.xml")) != null, "rejected");
        ok("error-3.xml is still rejected",
                refusal(() -> loaded("error-3.xml")) != null, "rejected");
        String why = refusal(() -> loaded("error-2.xml"));
        ok("and the reason still names the problem",
                why != null && why.contains("initial cash"), String.valueOf(why));

        GMEngine keeper = loaded("multiple.xml");
        refusal(() -> keeper.loadMarketFile(repo + "/test-files/ex2/error-2.xml"));
        count("a rejected load leaves the old market alone",
                keeper.getEvents(EventFilterDTO.all()).size(), 4);
        keeper.loadMarketFile(repo + "/test-files/ex2/small.xml");
        count("and a good load replaces it", keeper.getEvents(EventFilterDTO.all()).size(), 2);
    }

    // The filter rule moved onto EventFilterDTO; both engines now share it.
    static void filters() {
        System.out.println();
        System.out.println("B. the event filters");
        GMEngine e = loaded("multiple.xml");
        count("all", e.getEvents(EventFilterDTO.all()).size(), 4);
        count("LMSR only", e.getEvents(new EventFilterDTO(false, null, null)).size(), 2);
        count("order book only", e.getEvents(new EventFilterDTO(true, null, null)).size(), 2);
        count("not started only", e.getEvents(new EventFilterDTO(null, "Not started", null)).size(), 4);
        count("active only", e.getEvents(new EventFilterDTO(null, "Active", null)).size(), 0);
        count("on-close only", e.getEvents(new EventFilterDTO(null, null, "on-close")).size(), 2);
        count("on-purchase only", e.getEvents(new EventFilterDTO(null, null, "on-purchase")).size(), 2);
        count("LMSR and on-close together",
                e.getEvents(new EventFilterDTO(false, null, "on-close")).size(), 1);
        count("a filter that matches nothing",
                e.getEvents(new EventFilterDTO(true, "Closed", "on-close")).size(), 0);

        // A null field must mean "all", not "false".
        EventFilterDTO all = EventFilterDTO.all();
        EventDTO book = e.getEvent(2);
        ok("a null method filter matches an order book event", all.matches(book), "matched");
        ok("and matches an LMSR one too", all.matches(e.getEvent(1)), "matched");
    }

    // getBalanceHistory is now derived from the movement list rather than kept
    // as its own list, and the chart still reads it.
    static void balanceHistory() {
        System.out.println();
        System.out.println("C. the balance history, now derived from the movements");
        GMEngine e = loaded("multiple.xml");
        List<Double> atStart = e.getUserBalanceHistory("Menash");
        count("it starts with one point", atStart.size(), 1);
        money("which is the opening balance", atStart.get(0), 100.0);

        e.openEvent(1, "Tikva");
        e.buyLmsrShares(1, "Menash", 0, 20);
        List<Double> after = e.getUserBalanceHistory("Menash");
        count("a purchase adds a point", after.size(), 2);
        money("and the last point is the balance now", after.get(after.size() - 1),
                e.getUser("Menash").getBalance());

        List<MovementDTO> lines = e.getAccountMovements("Menash");
        count("the account has the same number of lines", lines.size(), after.size());
        ok("the first line is the opening balance",
                "Opening balance".equals(lines.get(0).getReason()), lines.get(0).getReason());
        ok("the purchase line says what was bought",
                lines.get(1).getReason().startsWith("Bought 20 of '"), lines.get(1).getReason());
        ok("and names the event it belonged to",
                "Mujtaba is Dead".equals(lines.get(1).getEventName()), lines.get(1).getEventName());
        ok("money going out is negative", lines.get(1).getAmount() < 0,
                String.valueOf(lines.get(1).getAmount()));
        count("the lines are numbered from one", lines.get(0).getIndex(), 1);

        // The market maker is paid a commission by somebody else's action.
        List<MovementDTO> mm = e.getAccountMovements("Tikva");
        boolean sawCommission = mm.stream().anyMatch(m -> m.getReason().contains("Commission received"));
        ok("the market maker's account says a commission arrived", sawCommission,
                mm.get(mm.size() - 1).getReason());
        boolean sawSubsidy = mm.stream().anyMatch(m -> m.getReason().contains("Subsidy paid"));
        ok("and that the subsidy went out", sawSubsidy, "found");
    }

    static void lmsrMoney() {
        System.out.println();
        System.out.println("D. LMSR money, to the cent");
        GMEngine e = loaded("multiple.xml");
        LmsrEventStateDTO before = e.getLmsrEventState(1);
        money("prices start even", before.getOption1State().getPrice(), 0.50);

        ok("a non market maker cannot open",
                refusal(() -> e.openEvent(1, "Menash")) != null, "refused");
        e.openEvent(1, "Tikva");
        money("the subsidy is b*ln2", e.getUser("Tikva").getBalance(), 9930.6853);
        money("and it is in the event account", e.getLmsrEventState(1).getAccountBalance(), 69.3147);

        PurchaseQuoteDTO quote = e.quoteLmsrPurchase(1, "Menash", 0, 20);
        money("20 shares cost", quote.getSharesCost(), 10.4992);
        money("commission at 5%", quote.getCommission(), 0.5250);
        BuyResultDTO bought = e.buyLmsrShares(1, "Menash", 0, 20);
        money("and the purchase charged exactly the quote", bought.getTotalPaid(), quote.getTotalCost());
        money("the buyer's balance", e.getUser("Menash").getBalance(), 88.9759);
        money("the price moved up", e.getLmsrEventState(1).getOption1State().getPrice(), 0.5498);
        money("the market maker got the commission", e.getUser("Tikva").getBalance(), 9931.2103);

        CloseResultDTO closed = e.closeEvent(1, "Tikva", 0);
        money("the winner was paid 20 x 1", e.getUser("Menash").getBalance(), 108.9759);
        money("the event account emptied", e.getLmsrEventState(1).getAccountBalance(), 0.0);
        ok("the winning option is recorded",
                "Hell Yea !".equals(closed.getWinningOptionName()), closed.getWinningOptionName());
        ok("a purchase under a cent is still refused",
                refusal(() -> e.buyLmsrShares(1, "Menash", 0, 1)) != null, "refused");
    }

    // The official order book simulation, replayed through the engine.
    static void orderBookSimulation() {
        System.out.println();
        System.out.println("E. the order book simulation");
        // Event 2 is "World Cap Winner": Avrum's, initial 100, minting allowed.
        // Event 3 is "Earth Quake on Dead Sea": Tikva's, initial 1000, no minting.
        GMEngine e = loaded("multiple.xml");
        e.openEvent(3, "Tikva");
        OrderBookEventStateDTO state = e.getOrderBookEventState(3);
        money("the market maker paid the initial investment",
                e.getUser("Tikva").getBalance(), 9000.0);
        money("which is in the event account", state.getAccountBalance(), 1000.0);
        count("and bought a pair of shares per base value",
                state.getOption1Book().getSharesOutstanding(), 1000);

        GMEngine f = loaded("multiple.xml");
        f.openEvent(2, "Avrum");
        money("a smaller event's initial stock", f.getUser("Avrum").getBalance(), 900.0);
        count("gave 100 of each option",
                f.getOrderBookEventState(2).getOption1Book().getSharesOutstanding(), 100);

        OrderResultDTO sell = f.placeOrder(2, "Avrum", 0, OrderSide.SELL, 0.50, 30);
        count("an offer with no taker rests", sell.getRestingQuantity(), 30);
        OrderBookStatsDTO stats = f.getOrderBookEventState(2).getOption1Book().getStats();
        money("and shows as the best ask", stats.getBestAsk(), 0.50);
        ok("with no last trade yet", stats.getLastTradePrice() == null, "null");
        ok("and no bid", stats.getBestBid() == null, "null");

        OrderResultDTO buy = f.placeOrder(2, "Tikva", 0, OrderSide.BUY, 0.50, 30);
        count("a matching bid fills it", buy.getFills().size(), 1);
        money("the buyer paid 30 x 0.50", buy.getCashPaid(), 15.0);
        count("and holds the shares", f.getUserInvolvement("Tikva", 2).getOption1Quantity(), 30);
        money("the last trade price is set",
                f.getOrderBookEventState(2).getOption1Book().getStats().getLastTradePrice(), 0.50);

        ok("a price at or above d is refused",
                refusal(() -> f.placeOrder(2, "Tikva", 0, OrderSide.BUY, 1.00, 1)) != null, "refused");
        ok("selling shares not held is refused",
                refusal(() -> f.placeOrder(2, "Menash", 0, OrderSide.SELL, 0.50, 1)) != null, "refused");

        // Minting: two buyers of opposite options whose prices reach d.
        GMEngine g = loaded("multiple.xml");
        g.openEvent(2, "Avrum");
        g.placeOrder(2, "Tikva", 1, OrderSide.BUY, 0.42, 35);
        OrderResultDTO minted = g.placeOrder(2, "Menash", 0, OrderSide.BUY, 0.60, 35);
        count("minting creates the pair", minted.getMintedQuantity(), 35);
        money("the incoming order pays the complement to d", minted.getCashPaid(), 35 * 0.58);
        count("and both options grew",
                g.getOrderBookEventState(2).getOption2Book().getSharesOutstanding(), 135);
        List<MovementDTO> lines = g.getAccountMovements("Menash");
        ok("the account line says it was a mint",
                lines.get(lines.size() - 1).getReason().contains("Minted")
                        || lines.stream().anyMatch(m -> m.getReason().contains("Minted")),
                lines.get(lines.size() - 1).getReason());
    }

    static void saveAndReload() {
        System.out.println();
        System.out.println("F. saving and reloading a whole market");
        String file = scratch + "/ex2-regress-state";
        GMEngine a = loaded("multiple.xml");
        a.openEvent(1, "Tikva");
        a.buyLmsrShares(1, "Menash", 0, 15);
        a.openEvent(2, "Avrum");
        a.placeOrder(2, "Avrum", 0, OrderSide.SELL, 0.55, 20);
        double menashBefore = a.getUser("Menash").getBalance();
        int movementsBefore = a.getAccountMovements("Menash").size();

        a.saveState(file);
        GMEngine b = new GuessMarketEngine();
        b.loadState(file);
        ok("it reloaded", b.isLoaded(), "loaded");
        count("every event came back", b.getEvents(EventFilterDTO.all()).size(), 4);
        count("every user came back", b.getUsers().size(), 3);
        money("with the same balance", b.getUser("Menash").getBalance(), menashBefore);
        count("and the same account lines", b.getAccountMovements("Menash").size(), movementsBefore);
        ok("the statuses survived", "Active".equals(b.getEvent(1).getStatusLabel()),
                b.getEvent(1).getStatusLabel());
        count("the resting order survived",
                b.getOrderBookEventState(2).getOption1Book().getAsks().size(), 1);
        count("the trade survived", b.getLmsrEventState(1).getTradesNewestFirst().size(), 1);
        b.buyLmsrShares(1, "Menash", 1, 5);
        ok("and the restored market can still be traded in", true, "traded");
    }

    static void closedInvolvement() {
        System.out.println();
        System.out.println("G. a closed event, from the user's own point of view");
        GMEngine e = loaded("multiple.xml");
        e.openEvent(1, "Tikva");
        e.buyLmsrShares(1, "Menash", 0, 20);
        e.closeEvent(1, "Tikva", 0);

        UserEventInvolvementDTO winner = e.getUserInvolvement("Menash", 1);
        ok("the winner is named", "Hell Yea !".equals(winner.getWinningOptionName()),
                winner.getWinningOptionName());
        ok("a result is reported", winner.getProfitOrLoss() != null,
                String.valueOf(winner.getProfitOrLoss()));
        count("the shares bought per option are there", winner.getOption1Quantity(), 20);

        UserEventInvolvementDTO bystander = e.getUserInvolvement("Avrum", 1);
        ok("somebody who never took part has no result",
                bystander.getProfitOrLoss() == null, "null, not an exception");
    }
}
