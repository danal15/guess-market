package engine.api.dto;

public final class UserDTO {

    private final String name;
    private final double balance;
    private final boolean blocked;
    private final boolean marketMaker;

    public UserDTO(String name, double balance, boolean blocked, boolean marketMaker) {
        this.name = name;
        this.balance = balance;
        this.blocked = blocked;
        this.marketMaker = marketMaker;
    }

    public String getName() {
        return name;
    }

    public double getBalance() {
        return balance;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public boolean isMarketMaker() {
        return marketMaker;
    }
}
