package net;

/**
 * The server is there and answering, but it does not know who we are. That
 * happens when it has been restarted - nothing is saved, so the user who logged
 * in no longer exists - or when a session has been left alone too long.
 *
 * It is kept apart from {@link ServerRefusedException} because the two call for
 * different things to be said: one means try again in a moment, the other means
 * log in again.
 */
public class NotLoggedInException extends RuntimeException {

    public NotLoggedInException(String message) {
        super(message);
    }
}
