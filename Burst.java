import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

/**
 * Burst script for Seat Reservation Service.
 * Usage: java Burst.java <BASE_URL>
 */
public class Burst {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java Burst.java <BASE_URL>");
            System.exit(1);
        }
        String baseUrl = args[0];
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .executor(Executors.newFixedThreadPool(100))
                .build();

        System.out.println("--- Starting Setup ---");
        // 1. Get Admin Token
        String adminToken = getAdminToken(client, baseUrl);

        // 2. Create a large show
        String showId = createShow(client, baseUrl, adminToken);
        System.out.println("Show created: " + showId);

        System.out.println("\n--- PHASE 1: Hot-Seat Storm (500 users vs A12) ---");
        runHotSeatStorm(client, baseUrl, adminToken, showId);

        System.out.println("\n--- PHASE 2: Stampede (20k requests) ---");
        runStampede(client, baseUrl, adminToken, showId);

        System.out.println("\n--- Final Reconciliation ---");
        verifyInvariant(client, baseUrl, showId);
    }

    private static String getAdminToken(HttpClient client, String baseUrl) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/token"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"user_id\":\"admin\", \"X-Admin-Secret\":\"admin-super-secret-key\"}"))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        // Simple JSON parse for token
        return res.body().split("\"token\":\"")[1].split("\"")[0];
    }

    private static String createShow(HttpClient client, String baseUrl, String token) throws Exception {
        // create 2000 seats
        StringBuilder seatsJson = new StringBuilder("[");
        for(int i=1; i<=2000; i++) seatsJson.append("\"S").append(i).append("\"").append(i == 2000 ? "" : ",");
        seatsJson.append("]");

        String body = String.format("{\"name\":\"Burst Show\",\"price_paise\":25000,\"seats\":%s}", seatsJson);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/shows"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        return res.body().split("\"show_id\":\"")[1].split("\"")[0];
    }

    private static void runHotSeatStorm(HttpClient client, String baseUrl, String token, String showId) throws Exception {
        int users = 500;
        ExecutorService executor = Executors.newFixedThreadPool(50);
        AtomicInteger winners = new AtomicInteger(0);
        AtomicInteger losers = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        for (int i = 0; i < users; i++) {
            final String uid = "user_" + i;
            executor.submit(() -> {
                try {
                    latch.await();
                    String userToken = getUserToken(client, baseUrl, uid);
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/shows/" + showId + "/reserve"))
                            .header("Authorization", "Bearer " + userToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"seats\":[\"S1\"], \"idempotency_key\":\"" + UUID.randomUUID() + "\"}"))
                            .build();
                    HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                    if (res.statusCode() == 201) winners.incrementAndGet();
                    else if (res.statusCode() == 409) losers.incrementAndGet();
                } catch (Exception e) { e.printStackTrace(); }
                return null;
            });
        }
        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
        System.out.println("Winners: " + winners.get() + " | Losers: " + losers.get());
    }

    private static void runStampede(HttpClient client, String baseUrl, String token, String showId) throws Exception {
        int totalRequests = 20000;
        ExecutorService executor = Executors.newFixedThreadPool(100);
        AtomicInteger s201 = new AtomicInteger(0);
        AtomicInteger s409 = new AtomicInteger(0);
        AtomicInteger s5xx = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    String uid = "u_" + (idx % 5000);
                    String userToken = getUserToken(client, baseUrl, uid);
                    String seat = "S" + (1 + (int)(Math.random() * 20)); // Target hot seats S1-S20
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/shows/" + showId + "/reserve"))
                            .header("Authorization", "Bearer " + userToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"seats\":[\"" + seat + "\"], \"idempotency_key\":\"" + UUID.randomUUID() + "\"}"))
                            .build();
                    HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                    if (res.statusCode() == 201) s201.incrementAndGet();
                    else if (res.statusCode() == 409) s409.incrementAndGet();
                    else if (res.statusCode() >= 500) s5xx.incrementAndGet();
                } catch (Exception e) { e.printStackTrace(); }
                return null;
            });
        }
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);
        System.out.println("201s: " + s201.get() + " | 409s: " + s409.get() + " | 5xx: " + s5xx.get());
    }

    private static String getUserToken(HttpClient client, String baseUrl, String uid) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/auth/token"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"user_id\":\"" + uid + "\"}"))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        return res.body().split("\"token\":\"")[1].split("\"")[0];
    }

    private static void verifyInvariant(HttpClient client, String baseUrl, String showId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/shows/" + showId))
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("Final State: " + res.body());
    }
}
