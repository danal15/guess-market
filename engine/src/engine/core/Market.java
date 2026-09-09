package engine.core;

import engine.api.exception.TradingException;
import engine.model.Event;
import engine.model.User;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The single root object holding everything loaded from one market file:
 * the events and the users, plus the market wide counter that gives orders
 * and trades a deterministic ordering.
 */
public class Market implements Serializable {

    private static final long serialVersionUID = 2L;

    private final List<Event> events;
    private final List<User> users;
    private long sequence;

    public Market(List<Event> events, List<User> users) {
        this.events = new ArrayList<>(events);
        this.users = new ArrayList<>(users);
    }

    public long nextSequence() {
        return ++sequence;
    }

    public List<Event> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public List<User> getUsers() {
        return Collections.unmodifiableList(users);
    }

    public Optional<Event> findById(int id) {
        for (Event event : events) {
            if (event.getId() == id) {
                return Optional.of(event);
            }
        }
        return Optional.empty();
    }

    public Optional<User> findUserByName(String name) {
        for (User user : users) {
            if (user.getName().equals(name)) {
                return Optional.of(user);
            }
        }
        return Optional.empty();
    }

    public Event requireEvent(int id) {
        return findById(id).orElseThrow(
                () -> new IllegalArgumentException("No event with id " + id + " exists."));
    }

    public User requireUser(String name) {
        return findUserByName(name).orElseThrow(
                () -> new TradingException("No user named '" + name + "' exists."));
    }

    /** The market maker of an event, resolved from the name stored on it. */
    public User marketMakerOf(Event event) {
        return requireUser(event.getMarketMakerName());
    }

    public int nextEventId() {
        int max = 0;
        for (Event event : events) {
            max = Math.max(max, event.getId());
        }
        return max + 1;
    }

    public boolean hasEventNamed(String name) {
        for (Event event : events) {
            if (event.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public void addEvent(Event event) {
        events.add(event);
    }
}
