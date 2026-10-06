package controller;

import engine.api.dto.ChatMessageDTO;
import engine.api.dto.SnapshotDTO;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import net.MarketClient;
import util.Dialogs;

/**
 * Bonus: a conversation everyone logged in can see.
 *
 * The messages arrive with the ordinary poll, and only the ones this client has
 * not seen are sent, so following a long conversation costs no more than
 * following a short one.
 */
public class ChatController {

    @FXML private ListView<String> messageList;
    @FXML private TextField messageField;
    @FXML private Button sendButton;

    private final ObservableList<String> lines = FXCollections.observableArrayList();
    private MarketClient client;

    @FXML
    private void initialize() {
        messageList.setItems(lines);
        messageList.setPlaceholder(new javafx.scene.control.Label("Nothing said yet."));
    }

    public void start(MarketClient client) {
        this.client = client;
    }

    /** How many lines are already here, so the server sends only what is new. */
    public int messagesHeld() {
        return lines.size();
    }

    public void apply(SnapshotDTO snapshot) {
        // A server restart leaves this client holding more lines than exist, so
        // the list starts again rather than drifting out of step for ever.
        if (snapshot.getChatTotal() < lines.size()) {
            lines.clear();
        }
        if (snapshot.getNewChatMessages().isEmpty()) {
            return;
        }
        for (ChatMessageDTO message : snapshot.getNewChatMessages()) {
            lines.add("[" + message.getTime() + "]  " + message.getUserName()
                    + ":  " + message.getText());
        }
        // Always follows the conversation. There is no supported way to ask a
        // ListView whether the reader has scrolled up, and guessing it wrong
        // would be worse than this: a chat that quietly stops showing new lines.
        messageList.scrollTo(lines.size() - 1);
    }

    @FXML
    private void onSend() {
        String text = messageField.getText() == null ? "" : messageField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        messageField.clear();
        try {
            client.postChatMessage(client.getUserName(), text);
        } catch (RuntimeException e) {
            Dialogs.error("That was not sent", String.valueOf(e.getMessage()));
            messageField.setText(text);
        }
    }
}
