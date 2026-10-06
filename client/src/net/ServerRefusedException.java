package net;

/**
 * The server could not be reached, or answered in a way that was not about the
 * market at all. A refusal the engine meant - "you cannot afford that" - comes
 * back as the engine's own exception instead, so the screens handle it exactly
 * as they did when the engine was in the same process.
 */
public class ServerRefusedException extends RuntimeException {

    public ServerRefusedException(String message) {
        super(message);
    }

    public ServerRefusedException(String message, Throwable cause) {
        super(message, cause);
    }
}
