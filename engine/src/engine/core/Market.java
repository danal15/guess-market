package engine.core;

import engine.api.exception.TradingException;
import engine.model.ChatMessage;
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
    private final List<ChatMessage> chat = new ArrayList<>();
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

    /** Names are matched the way login compares them: trimmed, ignoring case. */
    public Optional<User> findUserByName(String name) {
        String wanted = normalised(name);
        for (User user : users) {
            if (normalised(user.getName()).equals(wanted)) {
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

    /**
     * Event names are the identity that survives across files: exercise 3 files
     * carry no id, and two people may upload at any time, so the name is what
     * says whether an event is already here. Compared without regard to case or
     * surrounding space, so "World Cup" cannot arrive twice wearing a hat.
     */
    public boolean hasEventNamed(String name) {
        return findEventByName(name).isPresent();
    }

    public Optional<Event> findEventByName(String name) {
        String wanted = normalised(name);
        for (Event event : events) {
            if (normalised(event.getName()).equals(wanted)) {
                return Optional.of(event);
            }
        }
        return Optional.empty();
    }

    public void addEvent(Event event) {
        events.add(event);
    }

    // ---------- users, which in exercise 3 arrive by logging in ----------

    public boolean hasUserNamed(String name) {
        return findUserByName(name).isPresent();
    }

    public void addUser(User user) {
        users.add(user);
    }

    // ---------- chat, which lives with the market because it outlives any one client ----------

    public List<ChatMessage> getChat() {
        return Collections.unmodifiableList(chat);
    }

    public void addChatMessage(ChatMessage message) {
        chat.add(message);
    }

    private static String normalised(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }
}
