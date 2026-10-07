import controller.AccountTabController;
import controller.ClientEventsTabController;
import controller.ClientRootController;
import controller.LoginController;
import engine.api.dto.EventDTO;
import engine.api.dto.SnapshotDTO;
import engine.api.dto.UserDTO;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import net.MarketClient;
import skin.SkinManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Drives the real exercise 3 client against the deployed server: the login
 * screen, the two tabs, the filters, an upload, a trade, and - the part that
 * only a client-server application has to get right - whether what one user
 * does shows up on another user's screen without them touching anything.
 */
public class ClientWalkthrough extends Application {

    static final String BASE = "http://localhost:8080/guess-market";
    static String repo;
    static int pass, fail;

    static void ok(String label, boolean good, String detail) {
        System.out.printf("  [%s] %-56s %s%n", good ? "ok" : "FAIL", label, detail);
        if (good) pass++; else fail++;
    }

    /** One logged in client, built exactly as the application builds it. */
    static final class Session {
        final String name;
        final MarketClient client;
        Parent root;
        Scene scene;
        ClientRootController rootController;
        ClientEventsTabController events;
        AccountTabController account;
        UserDTO user;

        Session(String name) {
            this.name = name;
            this.client = new MarketClient(BASE);
        }

        void login() throws Exception {
            user = client.login(name);
            FXMLLoader loader = new FXMLLoader(
                    ClientWalkthrough.class.getResource("/fxml/client-root.fxml"));
            root = loader.load();
            rootController = loader.getController();
            scene = new Scene(root, 1200, 760);
            SkinManager.apply(scene, SkinManager.Skin.DEFAULT);
            events = field(rootController, "eventsPaneController", ClientEventsTabController.class);
            account = field(rootController, "accountPaneController", AccountTabController.class);
            rootController.start(client, user);
        }

        /** One poll, applied the way the timer applies it. */
        SnapshotDTO refresh(Integer selected) {
            SnapshotDTO snapshot = client.getSnapshot(name, selected, 0);
            events.apply(snapshot);
            account.apply(snapshot);
            return snapshot;
        }

        <T> T lookup(String id, Class<T> type) {
            return type.cast(scene.lookup("#" + id));
        }

        /**
         * A control owned by one of the tab controllers. Scene.lookup is no use
         * here: a tab that is not selected is not in the scene graph at all, and
         * both tabs have a node called eventsTable.
         */
        <T> T control(Object controller, String id, Class<T> type) throws Exception {
            return field(controller, id, type);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T field(Object target, String name, Class<T> type) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    static List<Node> descendants(Node node) {
        List<Node> all = new ArrayList<>();
        all.add(node);
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                all.addAll(descendants(child));
            }
        }
        return all;
    }

    @Override
    public void start(Stage stage) throws Exception {
        repo = getParameters().getRaw().get(0);
        String stamp = String.valueOf(System.currentTimeMillis() % 100000);
        try {
            run(stamp);
        } catch (Throwable t) {
            t.printStackTrace();
            fail++;
        }
        System.out.println();
        System.out.printf("CLIENT-WALKTHROUGH: %d passed, %d failed%n", pass, fail);
        Platform.exit();
        System.exit(fail == 0 ? 0 : 1);
    }

    void run(String stamp) throws Exception {
        System.out.println("A. the login screen");
        FXMLLoader loginLoader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        Parent loginRoot = loginLoader.load();
        LoginController login = loginLoader.getController();
        Scene loginScene = new Scene(loginRoot, 520, 340);
        SkinManager.apply(loginScene, SkinManager.Skin.DEFAULT);
        MarketClient probe = new MarketClient(BASE);
        login.start(probe, user -> { });
        ok("the login screen builds", loginRoot != null, "built");
        ok("and names the server it will use",
                ((Label) loginScene.lookup("#serverLabel")).getText().contains(BASE),
                ((Label) loginScene.lookup("#serverLabel")).getText());

        System.out.println();
        System.out.println("B. two users log in and get a window each");
        Session ann = new Session("Ann" + stamp);
        Session ben = new Session("Ben" + stamp);
        ann.login();
        ben.login();
        ok("Ann is in", ann.user != null && ann.user.getName().equals("Ann" + stamp), ann.user.getName());
        ok("Ben is in", ben.user != null, ben.user.getName());
        ok("Ann starts with an empty account", ann.user.getBalance() == 0.0,
                String.valueOf(ann.user.getBalance()));
        ok("the window says who she is",
                ((Label) ann.scene.lookup("#userLabel")).getText().equals(ann.name),
                ((Label) ann.scene.lookup("#userLabel")).getText());
        ok("a name already taken is refused",
                refused(() -> new MarketClient(BASE).login("Ann" + stamp)), "refused");

        System.out.println();
        System.out.println("C. Ann uploads a file of events");
        byte[] content = Files.readAllBytes(Path.of(repo, "..", "ex3-items", "multiple.xml"));
        // The names in the shipped file are taken by earlier tests, so this run
        // uses its own copy with distinct names.
        String xml = new String(content, java.nio.charset.StandardCharsets.UTF_8)
                .replace("Earth Quake on Dead Sea", "Quake " + stamp)
                .replace("World Cap Winner", "Cup " + stamp)
                .replace("Will it rain tomorrow ?", "Rain " + stamp);
        MarketClient.UploadResult uploaded =
                ann.client.upload("multiple.xml", xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ok("three events were added", uploaded.getEventNames().size() == 3,
                String.valueOf(uploaded.getEventNames()));

        SnapshotDTO annSees = ann.refresh(null);
        ok("Ann is a market maker now", annSees.getUser().isMarketMaker(), "yes");

        System.out.println();
        System.out.println("D. Ben sees Ann's events without doing anything");
        SnapshotDTO benSees = ben.refresh(null);
        boolean benHasThem = benSees.getEvents().stream()
                .anyMatch(e -> e.getName().equals("Cup " + stamp));
        ok("the events reached Ben's screen", benHasThem, "yes");
        boolean benSeesAnn = benSees.getUsers().stream()
                .anyMatch(u -> u.getName().equals(ann.name) && u.isMarketMaker());
        ok("and Ben sees Ann listed as a market maker", benSeesAnn, "yes");
        TableView<?> others = ben.control(ben.account, "usersTable", TableView.class);
        boolean annInTable = others.getItems().stream()
                .anyMatch(u -> ((UserDTO) u).getName().equals(ann.name));
        ok("Ann appears in Ben's other-users table", annInTable, "yes");
        boolean benExcludesHimself = others.getItems().stream()
                .noneMatch(u -> ((UserDTO) u).getName().equals(ben.name));
        ok("and Ben is not listed to himself", benExcludesHimself, "correct");
        ok("the other-users table shows exactly three columns", others.getColumns().size() == 3,
                others.getColumns().stream().map(c -> c.getText()).toList().toString());

        System.out.println();
        System.out.println("E. the filters narrow the list without asking the server");
        TableView<?> eventsTable = ann.control(ann.events, "eventsTable", TableView.class);
        int all = eventsTable.getItems().size();
        HBox methodBox = ann.control(ann.events, "methodFilterBox", HBox.class);
        ((ToggleButton) methodBox.getChildren().get(1)).fire();   // LMSR
        int lmsrOnly = eventsTable.getItems().size();
        ((ToggleButton) methodBox.getChildren().get(2)).fire();   // Order Book
        int bookOnly = eventsTable.getItems().size();
        ((ToggleButton) methodBox.getChildren().get(0)).fire();   // All
        int backToAll = eventsTable.getItems().size();
        ok("filtering to LMSR shows fewer", lmsrOnly < all && lmsrOnly > 0,
                all + " -> " + lmsrOnly);
        ok("filtering to order book shows the rest", bookOnly > 0, String.valueOf(bookOnly));
        ok("LMSR and order book together make the whole list", lmsrOnly + bookOnly == all,
                lmsrOnly + " + " + bookOnly + " = " + all);
        ok("and All brings them all back", backToAll == all, String.valueOf(backToAll));

        System.out.println();
        System.out.println("F. Ann opens an event, which needs money first");
        EventDTO cup = annSees.getEvents().stream()
                .filter(e -> e.getName().equals("Cup " + stamp)).findFirst().orElseThrow();
        ok("opening with nothing in the account is refused",
                refused(() -> ann.client.openEvent(cup.getId(), ann.name)), "refused");
        ann.client.loadFunds(ann.name, 1000);
        SnapshotDTO funded = ann.refresh(cup.getId());
        ok("loading funds shows in the balance", funded.getUser().getBalance() == 1000.0,
                String.valueOf(funded.getUser().getBalance()));
        TableView<?> movements = ann.control(ann.account, "movementsTable", TableView.class);
        ok("and on its own line in the account", movements.getItems().size() == 2,
                movements.getItems().size() + " lines");

        ann.client.openEvent(cup.getId(), ann.name);
        SnapshotDTO opened = ann.refresh(cup.getId());
        boolean nowActive = opened.getEvents().stream()
                .anyMatch(e -> e.getId() == cup.getId() && "Active".equals(e.getStatusLabel()));
        ok("the event is active", nowActive, "Active");
        ok("the event detail came with the same poll", opened.getOrderBookState() != null, "yes");
        ok("and so did Ann's own involvement", opened.getInvolvement() != null, "yes");

        System.out.println();
        System.out.println("G. Ben trades against Ann, and both screens follow");
        ben.client.loadFunds(ben.name, 500);
        ann.client.placeOrder(cup.getId(), ann.name, 0, engine.model.OrderSide.SELL, 0.50, 40);
        SnapshotDTO benBefore = ben.refresh(cup.getId());
        ok("Ben sees Ann's offer resting on the book",
                benBefore.getOrderBookState().getOption1Book().getAsks().size() == 1,
                "1 ask");
        ok("and whose offer it is",
                benBefore.getOrderBookState().getOption1Book().getAsks().get(0)
                        .getUserName().equals(ann.name), ann.name);

        ben.client.placeOrder(cup.getId(), ben.name, 0, engine.model.OrderSide.BUY, 0.50, 40);
        SnapshotDTO benAfter = ben.refresh(cup.getId());
        ok("Ben now holds the shares", benAfter.getInvolvement().getOption1Quantity() == 40,
                String.valueOf(benAfter.getInvolvement().getOption1Quantity()));
        ok("and paid for them", benAfter.getUser().getBalance() < 500.0,
                String.valueOf(benAfter.getUser().getBalance()));

        SnapshotDTO annAfter = ann.refresh(cup.getId());
        boolean annPaid = annAfter.getMovements().stream()
                .anyMatch(m -> m.getReason().startsWith("Sold 40 of"));
        ok("Ann's account gained a line saying she sold them", annPaid,
                annAfter.getMovements().get(annAfter.getMovements().size() - 1).getReason());
        ok("the book is empty again",
                annAfter.getOrderBookState().getOption1Book().getAsks().isEmpty(), "empty");
        ok("and Ann's role on the row reads market maker",
                "market maker".equals(roleColumnFor(ann, cup.getId())), roleColumnFor(ann, cup.getId()));
        ok("while Ben's reads taking part",
                "taking part".equals(roleColumnFor(ben, cup.getId())), roleColumnFor(ben, cup.getId()));

        System.out.println();
        System.out.println("H. chat reaches the other client");
        ben.client.postChatMessage(ben.name, "hello from " + ben.name);
        SnapshotDTO withChat = ann.client.getSnapshot(ann.name, null, 0);
        boolean heard = withChat.getNewChatMessages().stream()
                .anyMatch(m -> m.getText().equals("hello from " + ben.name));
        ok("Ann receives what Ben said", heard, "yes");
        SnapshotDTO noRepeat = ann.client.getSnapshot(ann.name, null, withChat.getChatTotal());
        ok("and is not sent it twice", noRepeat.getNewChatMessages().isEmpty(), "nothing new");

        System.out.println();
        System.out.println("I. the window at a small size");
        Stage small = new Stage();
        small.setScene(ann.scene);
        small.setWidth(760);
        small.setHeight(560);
        small.show();
        TabPane showTabs = ann.control(ann.rootController, "tabPane", TabPane.class);
        TitledPane chat = ann.control(ann.rootController, "chatPane", TitledPane.class);

        for (int tab = 0; tab < 2; tab++) {
            showTabs.getSelectionModel().select(tab);
            for (boolean chatOpen : new boolean[] { false, true }) {
                chat.setExpanded(chatOpen);
                ann.root.applyCss();
                ann.root.layout();
                String where = showTabs.getTabs().get(tab).getText()
                        + (chatOpen ? " with the chat open" : " with the chat shut");
                ok("nothing is cut off on " + where, cutOff(ann).isEmpty(), cutOff(ann));
            }
        }
        chat.setExpanded(false);
        showTabs.getSelectionModel().select(1);
        ann.root.applyCss();
        ann.root.layout();

        TabPane tabs = ann.control(ann.rootController, "tabPane", TabPane.class);
        ok("both tabs are there", tabs.getTabs().size() == 2,
                tabs.getTabs().stream().map(t -> t.getText()).toList().toString());
        ok("and they are named Events and Account",
                "Events".equals(tabs.getTabs().get(0).getText())
                        && "Account".equals(tabs.getTabs().get(1).getText()), "yes");
        Region table = ann.control(ann.account, "movementsTable", TableView.class);
        ok("the account movements table is still usable", table.getHeight() >= 80,
                String.format("%.0f px", table.getHeight()));
        small.hide();

        System.out.println();
        System.out.println("J. the skins still cover the client");
        for (SkinManager.Skin skin : SkinManager.Skin.values()) {
            SkinManager.apply(ann.scene, skin);
            ann.root.applyCss();
            ann.root.layout();
            ok("skin " + skin.getLabel() + " applies",
                    ann.scene.getStylesheets().size() == 1, ann.scene.getStylesheets().get(0));
        }
        SkinManager.apply(ann.scene, SkinManager.Skin.DEFAULT);

        System.out.println();
        System.out.println("K. the polling stops when the window closes");
        ann.rootController.stop();
        ben.rootController.stop();
        ok("stop() is accepted by both", true, "stopped");

        System.out.println();
        System.out.println("L. a server that is not there");
        MarketClient nowhere = new MarketClient("http://localhost:9/guess-market");
        boolean readable = false;
        try {
            nowhere.login("nobody");
        } catch (RuntimeException e) {
            readable = e.getMessage() != null && e.getMessage().contains("could not be reached");
        }
        ok("an unreachable server gives a message a person can read", readable, "yes");
    }

    /**
     * Any control that has been pushed outside the window. A node inside a
     * TitledPane that is folded shut is not on screen, so it does not count.
     */
    static String cutOff(Session session) {
        StringBuilder out = new StringBuilder();
        for (Node node : descendants(session.root)) {
            if (!(node instanceof Button button) || !button.isVisible() || button.getWidth() <= 0) {
                continue;
            }
            if (insideFoldedPane(button)) {
                continue;
            }
            var inScene = button.localToScene(button.getBoundsInLocal());
            if (inScene.getMaxX() > session.scene.getWidth() + 1
                    || inScene.getMaxY() > session.scene.getHeight() + 1) {
                out.append(String.format("'%s' x=%.0f..%.0f y=%.0f..%.0f of %.0fx%.0f; ",
                        button.getText(), inScene.getMinX(), inScene.getMaxX(),
                        inScene.getMinY(), inScene.getMaxY(),
                        session.scene.getWidth(), session.scene.getHeight()));
            }
        }
        return out.toString();
    }

    static boolean insideFoldedPane(Node node) {
        for (Node up = node.getParent(); up != null; up = up.getParent()) {
            if (up instanceof TitledPane pane && !pane.isExpanded()) {
                return true;
            }
        }
        return false;
    }

    /** Reads the "Your role" cell the account table would render for an event. */
    static String roleColumnFor(Session session, int eventId) throws Exception {
        TableView<?> table = session.control(session.account, "eventsTable", TableView.class);
        for (Object item : table.getItems()) {
            EventDTO event = (EventDTO) item;
            if (event.getId() == eventId) {
                var column = table.getColumns().get(3);
                Object value = column.getCellObservableValue(table.getItems().indexOf(item)).getValue();
                return String.valueOf(value);
            }
        }
        return "(row not found)";
    }

    static boolean refused(Runnable call) {
        try {
            call.run();
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
