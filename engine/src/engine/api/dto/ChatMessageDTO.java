package engine.api.dto;

/** One chat line, with the time formatted so every client shows it the same way. */
public final class ChatMessageDTO {

    private final String userName;
    private final String text;
    private final String time;

    public ChatMessageDTO(String userName, String text, String time) {
        this.userName = userName;
        this.text = text;
        this.time = time;
    }

    public String getUserName() {
        return userName;
    }

    public String getText() {
        return text;
    }

    /** Wall clock time of the message, as HH:mm:ss. */
    public String getTime() {
        return time;
    }
}
