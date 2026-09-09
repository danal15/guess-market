package engine.api.exception;

public class TradingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TradingException(String message) {
        super(message);
    }
}
