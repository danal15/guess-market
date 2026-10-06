package engine.model;

import java.io.Serializable;

/** One line somebody said in the chat. */
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String userName;
    private final String text;
    private final long timestamp;

    public ChatMessage(String userName, String text, long timestamp) {
        this.userName = userName;
        this.text = text;
        this.timestamp = timestamp;
    }

    public String getUserName() {
        return userName;
    }

    public String getText() {
        return text;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
