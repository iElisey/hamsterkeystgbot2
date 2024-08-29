package org.elos.hamsterkeystgbot;

import org.elos.hamsterkeystgbot.config.KeyConfig;
import org.elos.hamsterkeystgbot.service.KeysService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class KeyGeneratorRunner implements CommandLineRunner {

    private final KeysService keyService;

    @Autowired
    public KeyGeneratorRunner(KeysService keyService) {
        this.keyService = keyService;
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            // Create a new HttpClient
            HttpClient client = HttpClient.newHttpClient();

            // Create a new HttpRequest
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("https://api.ipify.org"))
                    .GET()
                    .build();

            // Send the request and get the response
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Print the IP address
            System.out.println("Your Public IP Address: " + response.body());
        } catch (Exception e) {
            e.printStackTrace();
        }
        String enableKeyGeneration = System.getenv("ENABLE_KEY_GENERATION");
        System.out.println(enableKeyGeneration);
        if ("true".equalsIgnoreCase(enableKeyGeneration)) {
            String initialPrefix = "MERGE";
            while (true) {
                keyService.generateAndStoreKeys(initialPrefix);
                initialPrefix = getNextPrefix(initialPrefix); // Implement logic to get the next prefix
            }
        }
    }

    private static final List<String> PREFIXES = List.copyOf(KeyConfig.KEY_CONFIGS.keySet());
    private AtomicInteger currentIndex = new AtomicInteger(0);

    // Method to get the next prefix in sequence
    public String getNextPrefix(String currentPrefix) {
        if (currentPrefix == null || !KeyConfig.KEY_CONFIGS.containsKey(currentPrefix)) {
            // If currentPrefix is null or invalid, start from the first prefix
            currentIndex.set(0);
        } else {
            // Find the current index of the prefix and move to the next
            currentIndex.set(PREFIXES.indexOf(currentPrefix));
            currentIndex.set((currentIndex.get() + 1) % PREFIXES.size()); // Cycle through prefixes
        }
        return PREFIXES.get(currentIndex.get());
    }
}