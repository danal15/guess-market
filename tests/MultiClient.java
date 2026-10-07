import controller.AccountTabController;
import controller.ChatController;
import controller.ClientEventsTabController;
import controller.ClientRootController;
import engine.api.dto.*;
import engine.model.OrderSide;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import net.MarketClient;
import skin.SkinManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Several real clients, side by side, through one scenario after another.
 *
 * Each client is the whole application: its own session with the server, its
 * own window built from the same FXML, its own controllers. A scenario is
 * driven by one client and then checked on the others' screens, because that is
 * the thing a client-server application has to get right and a single client
 * can never show.
 */
public class MultiClient extends Application {

    static final String BASE = "http://localhost:8080/guess-market";
    static String run;
    static int pass, fail;
    static final List<String> failures = new ArrayList<>();

    static void ok(String label, boolean good, String detail) {
        System.out.printf("  [%s] %-56s %s%n", good ? "ok" : "FAIL", label, detail);
        if (good) {
            pass++;
        } else {
            fail++;
            failures.add(label + "  (" + detail + ")");
        }
    }

    static void money(String label, double actual, double expected) {
        ok(label, Math.abs(actual - expected) < 0.005,
                String.format("%.4f want %.4f", actual, expected));
    }

    @SuppressWarnings("unchecked")
    static <T> T field(Object target, String name) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    /** One whole client: session, window, controllers. */
    static final class Client {
        final String name;
        final MarketClient api = new MarketClient(BASE);
        Parent root;
        Scene scene;
        ClientRootController rootController;
        ClientEventsTabController events;
        AccountTabController account;
        ChatController chat;
        UserDTO me;
        SnapshotDTO last;
        int chatSeen;

        Client(String name) {
            this.name = name;
        }

        void open() throws Exception {
            me = api.login(name);
            FXMLLoader loader = new FXMLLoader(MultiClient.class.getResource("/fxml/client-root.fxml"));
            root = loader.load();
            rootController = loader.getController();
            scene = new Scene(root, 1200, 780);
            SkinManager.apply(scene, SkinManager.Skin.DEFAULT);
            events = field(rootController, "eventsPaneController");
            account = field(rootController, "accountPaneController");
            chat = field(rootController, "chatBoxController");
            rootController.start(api, me);
            // start() kicks off a background poll; this test drives it by hand.
            rootController.stop();
        }

        /**
         * Exactly what one tick of the poll timer does, the root controller's
         * own header included - it is private, because nothing but the timer
         * should call it, so the test reaches it the same way the timer does.
         */
        SnapshotDTO refresh(Integer selected) {
            last = api.getSnapshot(name, selected, chatSeen);
            chatSeen = last.getChatTotal();
            try {
                java.lang.reflect.Method apply =
                        ClientRootController.class.getDeclaredMethod("apply", SnapshotDTO.class);
                apply.setAccessible(true);
                apply.invoke(rootController, last);
            } catch (Exception e) {
                throw new IllegalStateException("could not apply the snapshot", e);
            }
            return last;
        }

        double balance() {
            return last.getUser().getBalance();
        }

        EventDTO event(String eventName) {
            return last.getEvents().stream()
                    .filter(e -> e.getName().equals(eventName)).findFirst().orElseThrow();
        }

        boolean sees(String eventName) {
            return last.getEvents().stream().anyMatch(e -> e.getName().equals(eventName));
        }

        /** What the Account tab's "other users" table actually holds. */
        List<String> otherUsersOnScreen() throws Exception {
            TableView<UserDTO> table = field(account, "usersTable");
            List<String> names = new ArrayList<>();
            for (UserDTO u : table.getItems()) {
                names.add(u.getName());
            }
            return names;
        }

        List<String> movementsOnScreen() throws Exception {
            TableView<MovementDTO> table = field(account, "movementsTable");
            List<String> rows = new ArrayList<>();
            for (MovementDTO m : table.getItems()) {
                rows.add(m.getReason());
            }
            return rows;
        }

        int eventRowsOnScreen() throws Exception {
            TableView<EventDTO> table = field(events, "eventsTable");
            return table.getItems().size();
        }

        List<String> chatOnScreen() throws Exception {
            ListView<String> list = field(chat, "messageList");
            return new ArrayList<>(list.getItems());
        }

        String headerBalance() {
            return ((Label) scene.lookup("#balanceLabel")).getText();
        }
    }

    static String eventsXml(String... names) {
        StringBuilder xml = new StringBuilder(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Guess-Market><GM-events>");
        for (String n : names) {
            boolean book = n.startsWith("Book");
            xml.append("<GM-event name=\"").append(n).append("\">")
               .append("<description>scenario event</description>")
               .append("<commission type=\"on-purchase\">5</commission>")
               .append("<GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>")
               .append("<GM-method>")
               .append(book
                       ? "<GM-order-book allow-mint=\"true\" initial=\"100\" d=\"1\"/>"
                       : "<GM-LMSR><b>100</b></GM-LMSR>")
               .append("</GM-method></GM-event>");
        }
        return xml.append("</GM-events></Guess-Market>").toString();
    }

    static void refreshAll(List<Client> clients, Integer selected) {
        for (Client c : clients) {
            c.refresh(selected);
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        run = String.valueOf(System.currentTimeMillis() % 100000);
        try {
            scenarios();
        } catch (Throwable t) {
            t.printStackTrace();
            fail++;
            failures.add("threw: " + t);
        }
        System.out.println();
        System.out.println("========================================");
        System.out.printf("MULTI-CLIENT: %d passed, %d failed%n", pass, fail);
        for (String f : failures) {
            System.out.println("   FAILED: " + f);
        }
        System.out.println("========================================");
        Platform.exit();
        System.exit(fail == 0 ? 0 : 1);
    }

    void scenarios() throws Exception {
        String book = "Book " + run;
        String lmsr = "Lmsr " + run;
        String spare = "Lmsr2 " + run;
        // A mint only happens when nobody is selling the option outright, and an
        // order only rests when nothing matches it. Both need a book of their
        // own, or the orders left lying around by an earlier scenario answer
        // them first - which is correct behaviour, and not what is under test.
        String mintBook = "Book3 " + run;
        String debtBook = "Book4 " + run;

        // ---------------------------------------------------------- 1
        System.out.println("1. four clients open, each its own window and session");
        List<Client> all = new ArrayList<>();
        for (String n : new String[] { "Ann", "Ben", "Cal", "Dot" }) {
            Client c = new Client(n + run);
            c.open();
            all.add(c);
        }
        Client ann = all.get(0), ben = all.get(1), cal = all.get(2), dot = all.get(3);
        refreshAll(all, null);
        ok("all four are logged in", all.stream().allMatch(c -> c.me != null), "4 sessions");
        ok("each starts with an empty account",
                all.stream().allMatch(c -> c.balance() == 0.0), "all $0.00");
        ok("each sees the other three of this run listed",
                ann.otherUsersOnScreen().containsAll(List.of(ben.name, cal.name, dot.name))
                        && dot.otherUsersOnScreen().containsAll(List.of(ann.name, ben.name, cal.name)),
                "Ann sees " + ann.otherUsersOnScreen().stream()
                        .filter(n -> n.endsWith(run)).toList());
        ok("and nobody lists themselves",
                !ann.otherUsersOnScreen().contains(ann.name), "correct");
        ok("the header shows each one's own balance",
                ann.headerBalance().equals("Balance: $0.00"), ann.headerBalance());
        ok("a fifth client cannot take a name already in use",
                refused(() -> new MarketClient(BASE).login("Ann" + run)), "refused");

        // ---------------------------------------------------------- 2
        System.out.println();
        System.out.println("2. Ann uploads; the other three see it without asking");
        int before = ben.eventRowsOnScreen();
        ann.api.upload("scenario.xml",
                eventsXml(book, lmsr, spare, mintBook, debtBook).getBytes(StandardCharsets.UTF_8));
        refreshAll(all, null);
        ok("Ann's upload added five events",
                ann.sees(book) && ann.sees(lmsr) && ann.sees(spare)
                        && ann.sees(mintBook) && ann.sees(debtBook), "5 events");
        ok("they reached Ben's screen", ben.sees(book) && ben.sees(lmsr), "yes");
        ok("and Cal's and Dot's", cal.sees(book) && dot.sees(book), "yes");
        ok("Ben's events table grew by five",
                ben.eventRowsOnScreen() == before + 5,
                before + " -> " + ben.eventRowsOnScreen());
        ok("Ann is market maker of all three",
                ann.event(book).getMarketMakerName().equals(ann.name)
                        && ann.event(lmsr).getMarketMakerName().equals(ann.name), ann.name);
        ok("and the others see her as a market maker",
                ben.last.getUsers().stream()
                        .anyMatch(u -> u.getName().equals(ann.name) && u.isMarketMaker()), "yes");
        ok("while Ben is still not one",
                !ben.last.getUser().isMarketMaker(), "correct");

        // ---------------------------------------------------------- 3
        System.out.println();
        System.out.println("3. a second uploader, and a name that is already taken");
        String benBook = "Book2 " + run;
        ben.api.upload("bens.xml", eventsXml(benBook).getBytes(StandardCharsets.UTF_8));
        refreshAll(all, null);
        ok("Ben can upload his own file too", ann.sees(benBook), "yes");
        ok("and he is market maker of his, not Ann's",
                ann.event(benBook).getMarketMakerName().equals(ben.name)
                        && ann.event(book).getMarketMakerName().equals(ann.name), "correct");
        String clash = refusalFor(() -> cal.api.upload("clash.xml",
                eventsXml(book).getBytes(StandardCharsets.UTF_8)));
        ok("Cal cannot upload an event name somebody else used", clash != null, String.valueOf(clash));
        ok("and the refusal says so", clash != null && clash.contains("already in the system"), "yes");
        int countBefore = ann.eventRowsOnScreen();
        refreshAll(all, null);
        ok("nothing was added by the rejected file",
                ann.eventRowsOnScreen() == countBefore, String.valueOf(countBefore));

        // ---------------------------------------------------------- 4
        System.out.println();
        System.out.println("4. opening needs money, and only the market maker may");
        int bookId = ann.event(book).getId();
        ok("Ann cannot open her event with an empty account",
                refused(() -> ann.api.openEvent(bookId, ann.name)), "refused");
        ok("and Ben cannot open it at all",
                refused(() -> ben.api.openEvent(bookId, ben.name)), "refused");
        for (Client c : all) {
            c.api.loadFunds(c.name, 1000);
        }
        refreshAll(all, null);
        ok("everybody loaded funds", all.stream().allMatch(c -> c.balance() == 1000.0), "$1000 each");
        ok("the movement is on Ann's own account",
                ann.movementsOnScreen().contains("Funds loaded"), ann.movementsOnScreen().toString());
        ok("Ben's account does not show Ann's deposit",
                ben.movementsOnScreen().stream().filter(r -> r.equals("Funds loaded")).count() == 1,
                "only his own");
        ann.api.openEvent(bookId, ann.name);
        refreshAll(all, bookId);
        ok("Ann opened it", "Active".equals(ann.event(book).getStatusLabel()),
                ann.event(book).getStatusLabel());
        ok("and everybody sees it active", "Active".equals(dot.event(book).getStatusLabel()), "yes");
        money("Ann paid the initial investment", ann.balance(), 900.0);
        ok("the others' balances did not move",
                ben.balance() == 1000.0 && cal.balance() == 1000.0, "untouched");

        // ---------------------------------------------------------- 5
        System.out.println();
        System.out.println("5. an order book with three traders at once");
        ann.api.placeOrder(bookId, ann.name, 0, OrderSide.SELL, 0.60, 40);
        ann.api.placeOrder(bookId, ann.name, 0, OrderSide.SELL, 0.70, 40);
        refreshAll(all, bookId);
        OrderBookEventStateDTO seen = ben.last.getOrderBookState();
        ok("Ben sees both of Ann's offers", seen.getOption1Book().getAsks().size() == 2,
                seen.getOption1Book().getAsks().size() + " asks");
        money("the best ask is the cheaper one", seen.getOption1Book().getStats().getBestAsk(), 0.60);
        ok("and they are named as Ann's",
                seen.getOption1Book().getAsks().get(0).getUserName().equals(ann.name), ann.name);

        OrderQuoteDTO quote = ben.api.quoteOrder(bookId, ben.name, 0, OrderSide.BUY, 0.60, 50);
        ok("Ben is told only 40 are available at that price", quote.getAvailableNow() == 40,
                String.valueOf(quote.getAvailableNow()));
        OrderResultDTO filled = ben.api.placeOrder(bookId, ben.name, 0, OrderSide.BUY, 0.65, 50);
        ok("his order eats the cheap one and rests the remainder",
                filled.getFills().size() == 1 && filled.getRestingQuantity() == 10,
                filled.getFills().size() + " fills, " + filled.getRestingQuantity() + " resting");
        money("he paid 40 x 0.60", filled.getCashPaid(), 24.0);

        refreshAll(all, bookId);
        money("Ann received it", ann.balance(), 900.0 + 24.0 + 1.20);
        ok("Ann's account says she sold them",
                ann.movementsOnScreen().stream().anyMatch(r -> r.startsWith("Sold 40")),
                "found");
        ok("and that a commission came in",
                ann.movementsOnScreen().stream().anyMatch(r -> r.equals("Commission received")),
                "found");
        ok("Cal, who did nothing, sees the trade on the book",
                cal.last.getOrderBookState().getOption1Book().getStats().getLastTradePrice() != null,
                "last trade " + cal.last.getOrderBookState().getOption1Book().getStats().getLastTradePrice());
        ok("and sees Ben among the participants",
                cal.last.getOrderBookState().getParticipants().stream()
                        .anyMatch(p -> p.getUserName().equals(ben.name)), "yes");
        ok("Ben's own row reads 'taking part'",
                "taking part".equals(roleOf(ben, bookId)), roleOf(ben, bookId));
        ok("Ann's reads 'market maker'",
                "market maker".equals(roleOf(ann, bookId)), roleOf(ann, bookId));

        // ---------------------------------------------------------- 6
        System.out.println();
        System.out.println("6. minting between two different clients, on a book nobody is selling in");
        int mintId = ann.event(mintBook).getId();
        ann.api.openEvent(mintId, ann.name);
        refreshAll(all, mintId);
        long mintedBefore = cal.last.getOrderBookState().getOption1Book().getSharesOutstanding();

        // A resale would be cheaper, so the engine looks for one first. With no
        // ask on the book there is none, and the two opposite bids reach d.
        cal.api.placeOrder(mintId, cal.name, 1, OrderSide.BUY, 0.45, 20);
        OrderResultDTO mint = dot.api.placeOrder(mintId, dot.name, 0, OrderSide.BUY, 0.80, 20);
        refreshAll(all, mintId);
        ok("Dot's order mints against Cal's", mint.getMintedQuantity() == 20,
                String.valueOf(mint.getMintedQuantity()));
        money("Dot paid the complement to d, not his own price", mint.getCashPaid(), 20 * 0.55);
        ok("Cal paid the price he asked for",
                cal.movementsOnScreen().stream().anyMatch(r -> r.startsWith("Minted 20")), "found");
        ok("both now hold 20",
                cal.last.getInvolvement().getOption2Quantity() == 20
                        && dot.last.getInvolvement().getOption1Quantity() == 20, "yes");
        ok("the shares outstanding grew on both options",
                ann.last.getOrderBookState().getOption1Book().getSharesOutstanding() == mintedBefore + 20
                        && ann.last.getOrderBookState().getOption2Book().getSharesOutstanding()
                                == mintedBefore + 20, "+20 each");

        // And the other way round: with an ask on the book, a resale wins.
        ann.api.placeOrder(mintId, ann.name, 0, OrderSide.SELL, 0.50, 10);
        cal.api.placeOrder(mintId, cal.name, 1, OrderSide.BUY, 0.90, 10);
        OrderResultDTO resale = dot.api.placeOrder(mintId, dot.name, 0, OrderSide.BUY, 0.95, 10);
        ok("a cheaper resale is taken instead of a mint",
                resale.getMintedQuantity() == 0 && resale.getFills().size() == 1,
                resale.getMintedQuantity() + " minted, " + resale.getFills().size() + " filled");
        money("and it costs the resting ask, not the complement", resale.getCashPaid(), 10 * 0.50);

        // ---------------------------------------------------------- 7
        System.out.println();
        System.out.println("7. an LMSR event, three buyers, commission to the market maker");
        int lmsrId = ann.event(lmsr).getId();
        double annBefore = ann.balance();
        ann.api.openEvent(lmsrId, ann.name);
        for (Client c : List.of(ben, cal, dot)) {
            c.api.buyLmsrShares(lmsrId, c.name, 0, 10);
        }
        refreshAll(all, lmsrId);
        LmsrEventStateDTO state = dot.last.getLmsrState();
        ok("three trades are recorded", state.getTradesNewestFirst().size() == 3,
                String.valueOf(state.getTradesNewestFirst().size()));
        ok("the price moved up", state.getOption1State().getPrice() > 0.50,
                String.format("%.4f", state.getOption1State().getPrice()));
        ok("each buyer paid a little more than the one before",
                state.getTradesNewestFirst().get(0).getSharesCost()
                        > state.getTradesNewestFirst().get(2).getSharesCost(), "rising");
        ok("Ann collected three commissions",
                ann.movementsOnScreen().stream()
                        .filter(r -> r.equals("Commission received")).count() >= 4, "yes");
        ok("and every client sees the same price",
                Math.abs(ben.last.getLmsrState().getOption1State().getPrice()
                        - cal.last.getLmsrState().getOption1State().getPrice()) < 1e-9, "identical");

        // ---------------------------------------------------------- 8
        System.out.println();
        System.out.println("8. going below zero, and coming back");
        Client poor = new Client("Poor" + run);
        poor.open();
        all.add(poor);
        int debtId = ann.event(debtBook).getId();
        ann.api.openEvent(debtId, ann.name);
        poor.api.loadFunds(poor.name, 30);

        // Each order is affordable on its own, and neither takes any money yet
        // because nobody is selling at that price - they rest. That is the only
        // way an account can go below zero, and the exercise says it must be
        // allowed to happen.
        poor.api.placeOrder(debtId, poor.name, 0, OrderSide.BUY, 0.75, 20);
        poor.api.placeOrder(debtId, poor.name, 0, OrderSide.BUY, 0.75, 20);
        poor.refresh(debtId);
        ok("both orders are resting and nothing has been paid", poor.balance() == 30.0,
                String.format("%.2f", poor.balance()));

        // Now the market maker sells into both of them at once.
        ann.api.placeOrder(debtId, ann.name, 0, OrderSide.SELL, 0.70, 40);
        poor.refresh(debtId);
        ok("the account went below zero", poor.balance() < 0,
                String.format("%.2f", poor.balance()));
        ok("and is blocked", poor.last.getUser().isBlocked(), "blocked");
        ok("the window says so", poor.headerBalance().startsWith("Balance: -"), poor.headerBalance());
        ok("a blocked user cannot trade",
                refused(() -> poor.api.placeOrder(debtId, poor.name, 0, OrderSide.BUY, 0.10, 1)),
                "refused");
        ok("nor upload a file",
                refused(() -> poor.api.upload("x.xml",
                        eventsXml("Blocked " + run).getBytes(StandardCharsets.UTF_8))), "refused");
        ok("nor chat", refused(() -> poor.api.postChatMessage(poor.name, "hello")), "refused");
        refreshAll(all, null);
        ok("the others can see that he is there",
                ann.otherUsersOnScreen().contains(poor.name), "listed");
        poor.api.loadFunds(poor.name, 200);
        poor.refresh(debtId);
        ok("loading funds lifts the block", !poor.last.getUser().isBlocked(), "free again");
        ok("and he can act once more",
                !refused(() -> poor.api.postChatMessage(poor.name, "back")), "allowed");

        // ---------------------------------------------------------- 9
        System.out.println();
        System.out.println("9. chat between all five");
        ann.api.postChatMessage(ann.name, "market is open");
        ben.api.postChatMessage(ben.name, "bought some");
        refreshAll(all, null);
        for (Client c : all) {
            ok(c.name + " sees both lines on screen",
                    c.chatOnScreen().stream().anyMatch(l -> l.contains("market is open"))
                            && c.chatOnScreen().stream().anyMatch(l -> l.contains("bought some")),
                    c.chatOnScreen().size() + " lines");
        }
        int held = ann.chatOnScreen().size();
        refreshAll(all, null);
        ok("a refresh with nothing new repeats nothing",
                ann.chatOnScreen().size() == held, String.valueOf(ann.chatOnScreen().size()));

        // ---------------------------------------------------------- 10
        System.out.println();
        System.out.println("10. everybody acting at the same instant");
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger errors = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Client c = all.get(i);
            int seed = i;
            Thread t = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    return;
                }
                for (int r = 0; r < 8; r++) {
                    try {
                        c.api.placeOrder(bookId, c.name, (seed + r) % 2, OrderSide.BUY,
                                0.10 + ((seed + r) % 4) * 0.10, 2);
                    } catch (RuntimeException expected) {
                        // A refusal is fine; a crash is not.
                    }
                    try {
                        c.api.getSnapshot(c.name, bookId, 0);
                    } catch (RuntimeException e) {
                        errors.incrementAndGet();
                    }
                }
            });
            threads.add(t);
            t.start();
        }
        go.countDown();
        for (Thread t : threads) {
            t.join();
        }
        ok("no client's refresh ever failed while all five acted", errors.get() == 0,
                errors.get() + " failures");
        refreshAll(all, bookId);
        ok("and every client still shows the same book",
                ann.last.getOrderBookState().getOption1Book().getBids().size()
                        == dot.last.getOrderBookState().getOption1Book().getBids().size(),
                ann.last.getOrderBookState().getOption1Book().getBids().size() + " bids everywhere");

        // ---------------------------------------------------------- 11
        System.out.println();
        System.out.println("11. closing, and what the losers and winners each see");
        ann.api.closeEvent(bookId, ann.name, 0);
        refreshAll(all, bookId);
        ok("the event is closed for everybody",
                all.stream().allMatch(c -> "Closed".equals(c.event(book).getStatusLabel())), "all see Closed");
        ok("the event account emptied",
                Math.abs(ann.last.getOrderBookState().getAccountBalance()) < 0.005,
                String.format("%.6f", ann.last.getOrderBookState().getAccountBalance()));
        ok("a holder of the winning option was paid",
                ben.movementsOnScreen().contains("Winnings")
                        || dot.movementsOnScreen().contains("Winnings"), "paid");
        ok("somebody who only held the losing one was not",
                cal.last.getInvolvement() != null, "has a result line");
        ok("closing it again is refused",
                refused(() -> ann.api.closeEvent(bookId, ann.name, 0)), "refused");
        ok("and nobody may trade in it now",
                refused(() -> ben.api.placeOrder(bookId, ben.name, 0, OrderSide.BUY, 0.5, 1)),
                "refused");

        // ---------------------------------------------------------- 12
        System.out.println();
        System.out.println("12. the money adds up");
        refreshAll(all, null);
        double cash = 0;
        for (Client c : all) {
            cash += c.balance();
        }
        // Only the events this run created: earlier runs leave their own behind,
        // and their money was never part of what this run put in.
        double accounts = 0;
        for (EventDTO e : ann.last.getEvents()) {
            if (e.getName().endsWith(run)) {
                accounts += e.getAccountBalance();
            }
        }
        double loaded = 1000 * 4 + 30 + 200;
        ok("every balance is a real number",
                !Double.isNaN(cash) && !Double.isInfinite(cash), String.format("%.4f", cash));
        ok("what people hold plus what the events hold is what was put in",
                Math.abs((cash + accounts) - loaded) < 0.01,
                String.format("loaded %.2f, found %.2f", loaded, cash + accounts));

        System.out.println();
        System.out.println("13. every client's window is still sane");
        for (Client c : all) {
            c.root.applyCss();
            c.root.layout();
        }
        ok("all five still render", all.stream().allMatch(c -> c.root.getScene() != null), "5 windows");
        ok("and each shows its own balance in its own header",
                ann.headerBalance().contains(String.format("%.2f", ann.balance())),
                ann.headerBalance());
    }

    static String roleOf(Client c, int eventId) throws Exception {
        TableView<EventDTO> table = field(c.account, "eventsTable");
        for (int i = 0; i < table.getItems().size(); i++) {
            if (table.getItems().get(i).getId() == eventId) {
                return String.valueOf(table.getColumns().get(3).getCellObservableValue(i).getValue());
            }
        }
        return "(not found)";
    }

    static boolean refused(Runnable call) {
        return refusalFor(call) != null;
    }

    static String refusalFor(Runnable call) {
        try {
            call.run();
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
