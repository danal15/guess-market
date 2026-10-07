package net;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import engine.api.exception.InvalidMarketFileException;
import engine.api.exception.TradingException;

import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Carries one call to the server and brings its answer back.
 *
 * Two things here matter to the rest of the client. The first is the cookie
 * store: the server remembers who logged in against the session, so every later
 * request has to travel on the same conversation, and that is what the cookie
 * handler is for. The second is {@link #raise}, which turns a refusal from the
 * server back into the very exception the engine threw. That is what lets the
 * screens written for exercise 2 keep catching what they always caught.
 *
 * The HTTP client that comes with Java does everything needed here, so the
 * client folder carries no networking library of its own.
 */
public class Transport {

    /**
     * Short on purpose. The dialogs ask the server what something would cost
     * while the user is still typing, and that happens on the interface thread,
     * so a server that has stopped answering must give up quickly rather than
     * hold the window still. Everything here talks to a machine that is almost
     * always the same one, so a few seconds is already generous.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /** An upload carries a whole file, so it is given longer. */
    private static final Duration UPLOAD_TIMEOUT = Duration.ofSeconds(30);
    private static final Gson GSON = new Gson();

    private final String baseUrl;
    private final HttpClient http;

    public Transport(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        // HTTP/1.1 on purpose. The default is HTTP/2, which over plain http
        // first asks Tomcat to upgrade the connection; Tomcat answers a POST
        // that asks this with 400 and the call then times out.
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public <T> T get(String path, Class<T> type, Object... parameters) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path + query(parameters)))
                .timeout(TIMEOUT)
                .GET()
                .build();
        return send(request, type);
    }

    public <T> T post(String path, Class<T> type, Object... parameters) {
        // Form encoded rather than in the URL, so a long value cannot be cut off
        // by a limit on the length of a request line.
        String body = query(parameters);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(
                        body.isEmpty() ? "" : body.substring(1), StandardCharsets.UTF_8))
                .build();
        return send(request, type);
    }

    public <T> T postJson(String path, Object payload, Class<T> type) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8))
                .build();
        return send(request, type);
    }

    /**
     * Sends a file the way a browser form would, which is the shape the server
     * example in class reads. The content is held in memory on both sides; a
     * market file is far too small to be worth doing anything cleverer.
     */
    public <T> T upload(String path, String fileName, byte[] content, Class<T> type) {
        String boundary = "GuessMarket" + Long.toHexString(new Random().nextLong());
        byte[] body = multipart(boundary, fileName, content);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(UPLOAD_TIMEOUT)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return send(request, type);
    }

    private byte[] multipart(String boundary, String fileName, byte[] content) {
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\""
                + headerSafe(fileName) + "\"\r\n"
                + "Content-Type: application/xml\r\n\r\n";
        String tail = "\r\n--" + boundary + "--\r\n";
        byte[] headBytes = head.getBytes(StandardCharsets.UTF_8);
        byte[] tailBytes = tail.getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[headBytes.length + content.length + tailBytes.length];
        System.arraycopy(headBytes, 0, body, 0, headBytes.length);
        System.arraycopy(content, 0, body, headBytes.length, content.length);
        System.arraycopy(tailBytes, 0, body, headBytes.length + content.length, tailBytes.length);
        return body;
    }

    /**
     * A name fit to sit inside a header: quotes would end the field early, and
     * anything outside plain ASCII arrives at the other end as mojibake. Only
     * the name travels through here - the file itself is sent as bytes and is
     * untouched by this.
     */
    private String headerSafe(String fileName) {
        StringBuilder safe = new StringBuilder();
        for (char c : fileName.toCharArray()) {
            safe.append(c >= 32 && c < 127 && c != '"' && c != '\\' ? c : '_');
        }
        return safe.isEmpty() ? "upload.xml" : safe.toString();
    }

    private <T> T send(HttpRequest request, Class<T> type) {
        HttpResponse<InputStream> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new ServerRefusedException("The server at " + baseUrl + " could not be reached."
                    + " Make sure it is running, then try again.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServerRefusedException("The request was interrupted.", e);
        }

        String body = read(response);
        if (response.statusCode() >= 400) {
            raise(response.statusCode(), body);
        }
        if (type == Void.class || body.isEmpty()) {
            return null;
        }
        try {
            // Always into the declared type: reading into a loose map would turn
            // every share count into a double and lose the big ones.
            return GSON.fromJson(body, type);
        } catch (JsonSyntaxException e) {
            throw new ServerRefusedException("The server sent back something unexpected.", e);
        }
    }

    private String read(HttpResponse<InputStream> response) {
        try (InputStream in = response.body()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ServerRefusedException("The answer from the server could not be read.", e);
        }
    }

    /**
     * Rebuilds the exception the engine threw, so a refusal reads the same to the
     * screens as it did when the engine was in this very process.
     */
    private void raise(int status, String body) {
        Refusal refusal = null;
        try {
            refusal = GSON.fromJson(body, Refusal.class);
        } catch (JsonSyntaxException ignored) {
            // Not one of ours - most likely the container's own error page.
        }
        if (refusal == null || refusal.message == null) {
            throw new ServerRefusedException("The server answered with status " + status + ".");
        }
        switch (refusal.type == null ? "" : refusal.type) {
            case "TradingException" -> throw new TradingException(refusal.message);
            case "InvalidMarketFileException" -> throw new InvalidMarketFileException(refusal.message);
            case "IllegalArgumentException" -> throw new IllegalArgumentException(refusal.message);
            case "IllegalStateException" -> throw new IllegalStateException(refusal.message);
            // Not a refusal about the market - the server does not know us any
            // more, which is a different thing from the server being unreachable.
            case "NotLoggedIn" -> throw new NotLoggedInException(refusal.message);
            default -> throw new ServerRefusedException(refusal.message);
        }
    }

    /** Builds "?a=1&b=2" from alternating names and values, skipping absent ones. */
    private String query(Object... parameters) {
        List<String> pairs = new ArrayList<>();
        for (int i = 0; i + 1 < parameters.length; i += 2) {
            Object value = parameters[i + 1];
            // A null means "not sent", which the server reads as "no preference".
            // Sending it as the text "null" would turn a missing filter into one.
            if (value == null) {
                continue;
            }
            pairs.add(encode(String.valueOf(parameters[i])) + "=" + encode(String.valueOf(value)));
        }
        return pairs.isEmpty() ? "" : "?" + String.join("&", pairs);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** The shape every refusal from the server takes. */
    private static final class Refusal {
        private String type;
        private String message;
    }
}
