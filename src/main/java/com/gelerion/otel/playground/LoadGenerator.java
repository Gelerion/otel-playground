package com.gelerion.otel.playground;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LoadGenerator {

    private static final List<String> NAMES = List.of("alpha", "beta", "gamma");
    private static final String BASE_URL = "http://localhost:8080/v1/hello";
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final Random random = new Random();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Starting load generator...");
        ExecutorService executor = Executors.newFixedThreadPool(3);

        while (true) {
            String name = NAMES.get(random.nextInt(NAMES.size()));
            String url = BASE_URL + "/" + name;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::statusCode)
                    .thenAccept(statusCode -> System.out.println("Sent request to " + url + ", got status code: " + statusCode))
                    .exceptionally(ex -> {
                        System.err.println("Failed to send request to " + url + ": " + ex.getMessage());
                        return null;
                    });

            executor.submit(future::join);

            // Wait for a random delay between 500ms and 1500ms
            TimeUnit.MILLISECONDS.sleep(500 + random.nextInt(1000));
        }
    }
}
