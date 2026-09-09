package engine.core.xml;

import engine.api.exception.InvalidMarketFileException;
import engine.core.Market;
import engine.model.CommissionType;
import engine.model.Event;
import engine.model.LmsrEvent;
import engine.model.OrderBookEvent;
import engine.model.User;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads an exercise 2 market file and turns it into a validated Market.
 * Every structural and application level check lives here, so the rest of the
 * engine can trust whatever comes out.
 */
public final class MarketFileLoader {

    private MarketFileLoader() {
    }

    public static Market load(String rawPath) {
        File file = resolveFile(rawPath);
        Document document = parse(file);

        Element root = document.getDocumentElement();
        if (root == null || !"Guess-Market".equals(root.getTagName())) {
            throw new InvalidMarketFileException("The root element must be <Guess-Market>.");
        }

        // The schema uses xs:all, so the two sections may appear in either order.
        Element eventsElement = firstChildElement(root, "GM-events");
        if (eventsElement == null) {
            throw new InvalidMarketFileException("Missing required <GM-events> element.");
        }
        Element usersElement = firstChildElement(root, "GM-users");
        if (usersElement == null) {
            throw new InvalidMarketFileException("Missing required <GM-users> element.");
        }

        List<Element> eventElements = childElements(eventsElement, "GM-event");
        if (eventElements.isEmpty()) {
            throw new InvalidMarketFileException("The file contains no <GM-event> elements.");
        }
        List<Element> userElements = childElements(usersElement, "GM-user");
        if (userElements.isEmpty()) {
            throw new InvalidMarketFileException("The file contains no <GM-user> elements.");
        }

        Map<Integer, ParsedEvent> parsedEvents = parseEvents(eventElements);
        List<ParsedUser> parsedUsers = parseUsers(userElements);

        Map<Integer, String> marketMakerOf = crossValidate(parsedEvents, parsedUsers);

        List<Event> events = new ArrayList<>();
        for (ParsedEvent parsed : parsedEvents.values()) {
            events.add(parsed.build(marketMakerOf.get(parsed.id)));
        }

        List<User> users = new ArrayList<>();
        for (ParsedUser parsed : parsedUsers) {
            User user = new User(parsed.name, parsed.initialCash);
            for (int eventId : parsed.marketMakerEventIds) {
                user.addMarketMakerEvent(eventId);
            }
            users.add(user);
        }

        return new Market(events, users);
    }

    // ---------- file handling ----------

    private static File resolveFile(String rawPath) {
        String path = stripQuotes(rawPath == null ? "" : rawPath.trim());
        if (path.isEmpty()) {
            throw new InvalidMarketFileException("No file path was entered.");
        }
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            throw new InvalidMarketFileException("No file exists at: " + path);
        }
        if (!path.toLowerCase().endsWith(".xml")) {
            throw new InvalidMarketFileException("The file must have an .xml extension: " + path);
        }
        return file;
    }

    private static Document parse(File file) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setIgnoringElementContentWhitespace(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(file);
        } catch (Exception e) {
            throw new InvalidMarketFileException("The file is not a well-formed XML file: " + e.getMessage());
        }
    }

    // ---------- events ----------

    private static Map<Integer, ParsedEvent> parseEvents(List<Element> eventElements) {
        Map<Integer, ParsedEvent> byId = new LinkedHashMap<>();
        for (Element element : eventElements) {
            ParsedEvent parsed = parseEvent(element);
            if (byId.containsKey(parsed.id)) {
                throw new InvalidMarketFileException("Duplicate event id " + parsed.id + ": used by '"
                        + byId.get(parsed.id).name + "' and '" + parsed.name + "'.");
            }
            byId.put(parsed.id, parsed);
        }
        return byId;
    }

    private static ParsedEvent parseEvent(Element element) {
        ParsedEvent parsed = new ParsedEvent();

        parsed.name = requiredAttribute(element, "name", "An event");
        parsed.id = parseIntElement(element, "id", parsed.name);
        parsed.description = textOfChild(element, "description", parsed.name);

        Element commissionElement = firstChildElement(element, "commission");
        if (commissionElement == null) {
            throw new InvalidMarketFileException(
                    "Event '" + parsed.name + "' is missing the required <commission> element.");
        }
        try {
            parsed.commissionPercent = Integer.parseInt(commissionElement.getTextContent().trim());
        } catch (NumberFormatException e) {
            throw new InvalidMarketFileException("Event '" + parsed.name + "' has a non-numeric commission value.");
        }
        if (parsed.commissionPercent < 0 || parsed.commissionPercent > 90) {
            throw new InvalidMarketFileException("Event '" + parsed.name + "' (id " + parsed.id
                    + ") has commission " + parsed.commissionPercent + " - allowed range is 0 to 90.");
        }
        String typeValue = commissionElement.getAttribute("type");
        parsed.commissionType = CommissionType.fromXmlValue(typeValue);
        if (parsed.commissionType == null) {
            throw new InvalidMarketFileException(
                    "Event '" + parsed.name + "' has an unknown commission type: '" + typeValue + "'.");
        }

        Element optionsElement = firstChildElement(element, "GM-options");
        if (optionsElement == null) {
            throw new InvalidMarketFileException(
                    "Event '" + parsed.name + "' is missing the required <GM-options> element.");
        }
        List<Element> optionElements = childElements(optionsElement, "GM-option");
        if (optionElements.size() != 2) {
            throw new InvalidMarketFileException("Event '" + parsed.name + "' must have exactly 2 options, found "
                    + optionElements.size() + ".");
        }
        parsed.option1 = optionElements.get(0).getTextContent().trim();
        parsed.option2 = optionElements.get(1).getTextContent().trim();
        if (parsed.option1.isEmpty() || parsed.option2.isEmpty()) {
            throw new InvalidMarketFileException("Event '" + parsed.name + "' has an empty option name.");
        }

        parseMethod(element, parsed);
        return parsed;
    }

    private static void parseMethod(Element eventElement, ParsedEvent parsed) {
        Element methodElement = firstChildElement(eventElement, "GM-method");
        if (methodElement == null) {
            throw new InvalidMarketFileException(
                    "Event '" + parsed.name + "' is missing the required <GM-method> element.");
        }
        Element lmsr = firstChildElement(methodElement, "GM-LMSR");
        Element orderBook = firstChildElement(methodElement, "GM-order-book");

        if (lmsr != null && orderBook != null) {
            throw new InvalidMarketFileException("Event '" + parsed.name
                    + "' defines both an LMSR and an order book method - it must define exactly one.");
        }
        if (lmsr == null && orderBook == null) {
            throw new InvalidMarketFileException("Event '" + parsed.name
                    + "' does not define a trading method (expected <GM-LMSR> or <GM-order-book>).");
        }

        if (lmsr != null) {
            parsed.isLmsr = true;
            parsed.b = parseIntElement(lmsr, "b", parsed.name);
            if (parsed.b <= 0) {
                throw new InvalidMarketFileException("Event '" + parsed.name + "' has a liquidity value b="
                        + parsed.b + " - it must be a positive integer.");
            }
            return;
        }

        parsed.isLmsr = false;
        parsed.d = parseIntAttribute(orderBook, "d", parsed.name);
        if (parsed.d <= 0) {
            throw new InvalidMarketFileException("Event '" + parsed.name + "' has a base value d=" + parsed.d
                    + " - it must be a positive integer.");
        }
        parsed.initial = parseIntAttribute(orderBook, "initial", parsed.name);
        if (parsed.initial < 0) {
            throw new InvalidMarketFileException("Event '" + parsed.name + "' has a negative initial investment ("
                    + parsed.initial + ").");
        }
        String mint = requiredAttribute(orderBook, "allow-mint", "Event '" + parsed.name + "'");
        if (!"true".equalsIgnoreCase(mint) && !"false".equalsIgnoreCase(mint)) {
            throw new InvalidMarketFileException("Event '" + parsed.name + "' has allow-mint='" + mint
                    + "' - it must be true or false.");
        }
        parsed.allowMint = Boolean.parseBoolean(mint.toLowerCase());
    }

    // ---------- users ----------

    private static List<ParsedUser> parseUsers(List<Element> userElements) {
        List<ParsedUser> users = new ArrayList<>();
        Map<String, Boolean> seenNames = new HashMap<>();

        for (Element element : userElements) {
            ParsedUser parsed = new ParsedUser();
            parsed.name = requiredAttribute(element, "name", "A user");
            if (seenNames.containsKey(parsed.name)) {
                throw new InvalidMarketFileException("Duplicate user name '" + parsed.name + "'.");
            }
            seenNames.put(parsed.name, Boolean.TRUE);

            parsed.initialCash = parseIntElement(element, "initial-cash", parsed.name);
            if (parsed.initialCash <= 0) {
                throw new InvalidMarketFileException("User '" + parsed.name + "' has initial cash "
                        + parsed.initialCash + " - it must be greater than 0.");
            }

            Element mmElement = firstChildElement(element, "GM-market-maker");
            if (mmElement != null) {
                for (Element eventRef : childElements(mmElement, "event")) {
                    String idValue = requiredAttribute(eventRef, "id", "User '" + parsed.name + "'");
                    try {
                        parsed.marketMakerEventIds.add(Integer.parseInt(idValue.trim()));
                    } catch (NumberFormatException e) {
                        throw new InvalidMarketFileException("User '" + parsed.name
                                + "' references a non-numeric event id '" + idValue + "'.");
                    }
                }
            }
            users.add(parsed);
        }
        return users;
    }

    /** Checks every market maker reference and that each event has exactly one. */
    private static Map<Integer, String> crossValidate(Map<Integer, ParsedEvent> events, List<ParsedUser> users) {
        Map<Integer, String> marketMakerOf = new LinkedHashMap<>();

        for (ParsedUser user : users) {
            for (int eventId : user.marketMakerEventIds) {
                ParsedEvent event = events.get(eventId);
                if (event == null) {
                    throw new InvalidMarketFileException("User '" + user.name
                            + "' is defined as market maker of event id " + eventId + ", which does not exist.");
                }
                String existing = marketMakerOf.get(eventId);
                if (existing != null) {
                    throw new InvalidMarketFileException("Event '" + event.name + "' (id " + eventId
                            + ") has more than one market maker: " + existing + " and " + user.name + ".");
                }
                marketMakerOf.put(eventId, user.name);
            }
        }

        for (ParsedEvent event : events.values()) {
            if (!marketMakerOf.containsKey(event.id)) {
                throw new InvalidMarketFileException("Event '" + event.name + "' (id " + event.id
                        + ") has no market maker assigned to it.");
            }
        }
        return marketMakerOf;
    }

    // ---------- small parsed carriers ----------

    private static final class ParsedEvent {
        int id;
        String name;
        String description;
        int commissionPercent;
        CommissionType commissionType;
        String option1;
        String option2;
        boolean isLmsr;
        int b;
        int d;
        int initial;
        boolean allowMint;

        Event build(String marketMakerName) {
            if (isLmsr) {
                return new LmsrEvent(id, name, description, commissionPercent, commissionType,
                        marketMakerName, option1, option2, b);
            }
            return new OrderBookEvent(id, name, description, commissionPercent, commissionType,
                    marketMakerName, option1, option2, d, allowMint, initial);
        }
    }

    private static final class ParsedUser {
        String name;
        int initialCash;
        final List<Integer> marketMakerEventIds = new ArrayList<>();
    }

    // ---------- DOM helpers ----------

    private static String requiredAttribute(Element element, String attribute, String owner) {
        String value = element.getAttribute(attribute);
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidMarketFileException(owner + " is missing the required '" + attribute + "' attribute.");
        }
        return value.trim();
    }

    private static int parseIntAttribute(Element element, String attribute, String eventName) {
        String value = requiredAttribute(element, attribute, "Event '" + eventName + "'");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new InvalidMarketFileException("Event '" + eventName + "' has a non-numeric '" + attribute
                    + "' value: '" + value + "'.");
        }
    }

    private static int parseIntElement(Element parent, String childTag, String ownerName) {
        String text = textOfChild(parent, childTag, ownerName);
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new InvalidMarketFileException("'" + ownerName + "' has a non-numeric value for <" + childTag
                    + ">: '" + text + "'.");
        }
    }

    private static String textOfChild(Element parent, String childTag, String ownerName) {
        Element child = firstChildElement(parent, childTag);
        if (child == null) {
            throw new InvalidMarketFileException("'" + ownerName + "' is missing the required <" + childTag
                    + "> element.");
        }
        return child.getTextContent().trim();
    }

    private static Element firstChildElement(Element parent, String tag) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tag.equals(node.getNodeName())) {
                return (Element) node;
            }
        }
        return null;
    }

    private static List<Element> childElements(Element parent, String tag) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && tag.equals(node.getNodeName())) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }
}
