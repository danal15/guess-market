package controller;

import anim.AnimationManager;
import engine.api.dto.SnapshotDTO;
import engine.api.dto.UserDTO;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
import net.MarketClient;
import skin.SkinManager;
import util.Format;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Holds the two screens together and keeps them current.
 *
 * Everything another user does - a file uploaded, an order filled, an event
 * closed - reaches this client by asking the server for a fresh picture once a
 * second. One request brings back the whole of what is on screen, so the two
 * tabs are always showing the same moment in the market.
 */
public class ClientRootController {

    /** Comfortably inside the two seconds the exercise allows, without chattering. */
    private static final Duration PULL_INTERVAL = Duration.seconds(1);

    @FXML private TabPane tabPane;
    @FXML private ChoiceBox<SkinManager.Skin> skinChoice;
    @FXML private CheckBox animationsCheck;
    @FXML private Label userLabel;
    @FXML private Label balanceLabel;
    @FXML private Label blockedLabel;
    @FXML private Label statusLabel;
    @FXML private TitledPane chatPane;

    // Injected by FXMLLoader from the fx:id of each fx:include, plus "Controller".
    @FXML private ClientEventsTabController eventsPaneController;
    @FXML private AccountTabController accountPaneController;
    @FXML private ChatController chatBoxController;

    private MarketClient client;
    private Timeline pull;

    /** One request at a time: a slow answer must not pile the next one on top of it. */
    private final AtomicBoolean polling = new AtomicBoolean();

    /** So a server that has gone away is reported once, not once a second. */
    private boolean offlineReported;

    @FXML
    private void initialize() {
        skinChoice.getItems().addAll(SkinManager.Skin.values());
        skinChoice.getSelectionModel().select(SkinManager.Skin.DEFAULT);
        skinChoice.setTooltip(new Tooltip("Change the colours and fonts of the whole window."));
        skinChoice.setOnAction(event -> applySkin());

        animationsCheck.setSelected(false);
        AnimationManager.setEnabled(false);
        animationsCheck.setTooltip(new Tooltip("Short animations when panels change. Off by default."));
        animationsCheck.setOnAction(event -> AnimationManager.setEnabled(animationsCheck.isSelected()));

        blockedLabel.setVisible(false);
        blockedLabel.managedProperty().bind(blockedLabel.visibleProperty());

        // Said before the first poll answers, so the chip is never an empty
        // coloured blob sitting in the top bar.
        balanceLabel.setText("Balance: -");
    }

    public void start(MarketClient client, UserDTO user) {
        this.client = client;
        userLabel.setText(user.getName());
        userLabel.setTooltip(new Tooltip("You are acting as " + user.getName()
                + ". Every action here is yours."));

        eventsPaneController.start(client);
        accountPaneController.start(client, this::refreshNow);
        chatBoxController.start(client);

        refreshNow();

        pull = new Timeline(new KeyFrame(PULL_INTERVAL, event -> poll()));
        pull.setCycleCount(Animation.INDEFINITE);
        pull.play();
    }

    /** Stops the polling, so closing the window really ends the process. */
    public void stop() {
        if (pull != null) {
            pull.stop();
        }
    }

    /** Asks for a fresh picture at once, after this user has done something. */
    public void refreshNow() {
        poll();
    }

    private void poll() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        Integer selected = selectedEventId();
        int chatFrom = chatBoxController.messagesHeld();

        Thread worker = new Thread(() -> {
            SnapshotDTO snapshot = null;
            String failure = null;
            try {
                snapshot = client.getSnapshot(client.getUserName(), selected, chatFrom);
            } catch (RuntimeException e) {
                failure = String.valueOf(e.getMessage());
            }
            SnapshotDTO received = snapshot;
            String problem = failure;
            Platform.runLater(() -> {
                try {
                    if (received != null) {
                        apply(received);
                    } else {
                        reportOffline(problem);
                    }
                } finally {
                    polling.set(false);
                }
            });
        }, "guess-market-pull");
        worker.setDaemon(true);
        worker.start();
    }

    /** Whichever screen the user is looking at decides which event detail is worth fetching. */
    private Integer selectedEventId() {
        boolean onAccountTab = tabPane.getSelectionModel().getSelectedIndex() == 1;
        return onAccountTab
                ? accountPaneController.getSelectedEventId()
                : eventsPaneController.getSelectedEventId();
    }

    private void apply(SnapshotDTO snapshot) {
        offlineReported = false;
        statusLabel.setText("");

        UserDTO me = snapshot.getUser();
        balanceLabel.setText("Balance: " + Format.money(me.getBalance()));
        blockedLabel.setText(me.isBlocked() ? "Blocked" : "");
        blockedLabel.setVisible(me.isBlocked());
        blockedLabel.setTooltip(me.isBlocked()
                ? new Tooltip("This account went below zero. Load funds to lift the block.")
                : null);

        eventsPaneController.apply(snapshot);
        accountPaneController.apply(snapshot);
        chatBoxController.apply(snapshot);
        if (!chatPane.isExpanded() && !snapshot.getNewChatMessages().isEmpty()) {
            chatPane.setText("Chat (" + snapshot.getChatTotal() + ")");
        } else if (chatPane.isExpanded()) {
            chatPane.setText("Chat");
        }
    }

    /**
     * A server that cannot be reached is said once, in the top bar. Popping a
     * dialog on every failed poll would bury the window in them.
     */
    private void reportOffline(String problem) {
        statusLabel.setText("Not connected to the server - retrying");
        statusLabel.setTooltip(new Tooltip(problem == null ? "" : problem));
        if (!offlineReported) {
            offlineReported = true;
            System.err.println("Pull failed: " + problem);
        }
    }

    private void applySkin() {
        if (skinChoice.getScene() != null) {
            SkinManager.apply(skinChoice.getScene(), skinChoice.getValue());
        }
    }
}
