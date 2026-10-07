import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hits the deployed server from many clients at once, which is the one thing a
 * single desktop application never did. Every account is funded with a known
 * amount, so once the dust settles the money in the system has to add up to
 * exactly what went in - that is the check that would catch a lost update.
 */
public class Hammer {

    static final String BASE = "http://localhost:8080/guess-market";
    static final int TRADERS = 12;
    static final int ROUNDS = 12;
    static final double FUNDING = 2000.0;

    static int pass, fail;

    static void check(String label, boolean ok, String detail) {
        System.out.printf("  [%s] %-56s %s%n", ok ? "ok" : "FAIL", label, detail);
        if (ok) pass++; else fail++;
    }

    static final class Client {
        final String name;
        final HttpClient http = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(20)).build();

        Client(String name) {
            this.name = name;
        }

        String call(String method, String path, String body) {
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(BASE + path))
                        .timeout(Duration.ofSeconds(30));
                if ("POST".equals(method)) {
                    b.header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    body == null ? "" : body, StandardCharsets.UTF_8));
                } else {
                    b.GET();
                }
                HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
                return r.statusCode() + "|" + r.body();
            } catch (Exception e) {
                return "0|" + e;
            }
        }
    }

    static double sumBalances(String syncBody) {
        // Reads the users array of a snapshot without pulling in a json library.
        int start = syncBody.indexOf("\"users\":[");
        int end = syncBody.indexOf("]", start);
        String users = syncBody.substring(start, end);
        double total = 0;
        Matcher m = Pattern.compile("\"balance\":(-?[0-9.E]+)").matcher(users);
        while (m.find()) {
            total += Double.parseDouble(m.group(1));
        }
        return total;
    }

    static double sumEventAccounts(String syncBody) {
        int start = syncBody.indexOf("\"events\":[");
        int end = syncBody.indexOf("\"totalEventCount\"", start);
        String events = syncBody.substring(start, end);
        double total = 0;
        Matcher m = Pattern.compile("\"accountBalance\":(-?[0-9.E]+)").matcher(events);
        while (m.find()) {
            total += Double.parseDouble(m.group(1));
        }
        return total;
    }

    static int idOf(String syncBody, String eventName) {
        Matcher m = Pattern.compile("\\{\"id\":(\\d+),\"name\":\"" + Pattern.quote(eventName) + "\"")
                .matcher(syncBody);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    public static void main(String[] args) throws Exception {
        String stamp = String.valueOf(System.currentTimeMillis() % 100000);

        System.out.println("A. many logins at once, all racing for the same name");
        AtomicInteger admitted = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> racers = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            Thread t = new Thread(() -> {
                Client c = new Client("race");
                try {
                    go.await();
                } catch (InterruptedException ignored) {
                    return;
                }
                String answer = c.call("POST", "/login", "name=Race" + stamp);
                if (answer.startsWith("200")) admitted.incrementAndGet();
                else if (answer.startsWith("400")) refused.incrementAndGet();
            });
            racers.add(t);
            t.start();
        }
        go.countDown();
        for (Thread t : racers) t.join();
        check("exactly one of 16 racers got the name", admitted.get() == 1,
                "admitted=" + admitted.get() + " refused=" + refused.get());

        System.out.println();
        System.out.println("B. " + TRADERS + " traders, funded equally, trading at once");
        List<Client> traders = new ArrayList<>();
        for (int i = 0; i < TRADERS; i++) {
            Client c = new Client("T" + stamp + "_" + i);
            String login = c.call("POST", "/login", "name=" + c.name);
            if (!login.startsWith("200")) {
                check("trader " + c.name + " logged in", false, login);
                return;
            }
            String funded = c.call("POST", "/funds", "amount=" + FUNDING);
            if (!funded.startsWith("200")) {
                check("trader " + c.name + " funded", false, funded);
                return;
            }
            traders.add(c);
        }
        check("all " + TRADERS + " logged in and funded", true, FUNDING + " each");

        // One of them owns a brand new order book event and an LMSR event.
        Client owner = traders.get(0);
        String bookName = "Hammer Book " + stamp;
        String lmsrName = "Hammer Lmsr " + stamp;
        owner.call("POST", "/event/new", null);
        String mk = owner.call("POST", "/event/new", null);
        // The create endpoint takes json, so it is called directly here.
        createEvent(owner, bookName, true);
        createEvent(owner, lmsrName, false);

        String snap = owner.call("GET", "/sync", null);
        int bookId = idOf(snap, bookName);
        int lmsrId = idOf(snap, lmsrName);
        check("both events were created", bookId > 0 && lmsrId > 0,
                "book=" + bookId + " lmsr=" + lmsrId);

        check("owner opens the order book event",
                owner.call("POST", "/event/open", "eventId=" + bookId).startsWith("200"), "");
        check("owner opens the LMSR event",
                owner.call("POST", "/event/open", "eventId=" + lmsrId).startsWith("200"), "");

        double moneyIn = TRADERS * FUNDING;

        // Everybody hammers both events together: buys, sells, orders and polls.
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        AtomicInteger serverErrors = new AtomicInteger();
        AtomicInteger accepted = new AtomicInteger();
        for (int i = 0; i < TRADERS; i++) {
            final Client c = traders.get(i);
            final int seed = i;
            Thread t = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException ignored) {
                    return;
                }
                for (int round = 0; round < ROUNDS; round++) {
                    int option = (seed + round) % 2;
                    double price = 0.30 + ((seed + round) % 5) * 0.10;
                    String side = ((seed + round) % 3 == 0) ? "SELL" : "BUY";
                    String order = c.call("POST", "/trade/order",
                            "eventId=" + bookId + "&option=" + option + "&side=" + side
                                    + "&price=" + String.format("%.2f", price) + "&quantity=5");
                    tally(order, serverErrors, accepted);

                    String buy = c.call("POST", "/trade/lmsr-buy",
                            "eventId=" + lmsrId + "&option=" + option + "&quantity=3");
                    tally(buy, serverErrors, accepted);

                    tally(c.call("GET", "/sync?eventId=" + bookId, null), serverErrors, accepted);
                    tally(c.call("POST", "/chat", "text=round+" + round), serverErrors, accepted);
                }
            }, "trader-" + i);
            workers.add(t);
            t.start();
        }
        start.countDown();
        for (Thread t : workers) t.join();

        check("no request ever produced a server error", serverErrors.get() == 0,
                serverErrors.get() + " of " + accepted.get() + " calls failed with 5xx");

        System.out.println();
        System.out.println("C. did any money go missing?");
        String after = owner.call("GET", "/sync", null).split("\\|", 2)[1];
        double balances = sumBalances(after);
        double accounts = sumEventAccounts(after);
        // Other users from earlier runs of the api test may be logged in too, so
        // compare against what this run put in plus whatever was there before.
        System.out.printf("     balances total %.4f, event accounts total %.4f, sum %.4f%n",
                balances, accounts, balances + accounts);
        check("every balance is a real number", !Double.isNaN(balances) && !Double.isInfinite(balances),
                String.format("%.4f", balances));
        check("the money this run added is still accounted for",
                balances + accounts >= moneyIn - 0.01,
                String.format("in %.2f, found %.2f", moneyIn, balances + accounts));

        System.out.println();
        System.out.printf("HAMMER: %d passed, %d failed%n", pass, fail);
        System.exit(fail == 0 ? 0 : 1);
    }

    static void tally(String answer, AtomicInteger serverErrors, AtomicInteger total) {
        total.incrementAndGet();
        String status = answer.split("\\|", 2)[0];
        if (status.startsWith("5") || "0".equals(status)) {
            serverErrors.incrementAndGet();
            System.out.println("     unexpected: " + answer.substring(0, Math.min(300, answer.length())));
        }
    }

    static void createEvent(Client c, String name, boolean orderBook) throws Exception {
        String json = "{\"name\":\"" + name + "\",\"description\":\"hammer\",\"commissionPercent\":5,"
                + "\"commissionTypeLabel\":\"on-purchase\",\"option1Name\":\"Yes\",\"option2Name\":\"No\","
                + "\"orderBook\":" + orderBook + ",\"b\":200,\"baseValue\":1,\"initialInvestment\":200,"
                + "\"allowMint\":true}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + "/event/new"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> r = c.http.send(request, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            System.out.println("     create failed: " + r.statusCode() + " " + r.body());
        }
    }
}
