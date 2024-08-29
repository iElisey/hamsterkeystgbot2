package org.elos.hamsterkeystgbot.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.elos.hamsterkeystgbot.config.KeyConfig;
import org.elos.hamsterkeystgbot.model.Keys;
import org.elos.hamsterkeystgbot.model.User;
import org.elos.hamsterkeystgbot.repository.KeysRepository;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class KeysService {
    private final KeysRepository keysRepository;
    private final String[] prefixes = {"BIKE", "CUBE", "TRAIN", "MERGE", "TWERK", "POLY", "RACE", "TRIM", "CAFE"};


    public void generateAndStoreKeys(String initialPrefix) {
        try {
            // Introduce a 15-second delay before starting the key generation
            System.out.println("Waiting for 15 seconds before starting key generation...");
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Thread was interrupted, failed to complete initialization");
        }
        List<String> proxies = readProxiesFromFile("src/main/resources/proxies.txt");
        List<Thread> proxyThreads = new ArrayList<>();
        AtomicInteger amountOfPromo = new AtomicInteger();

        for (String proxy : proxies) {
            Thread proxyThread = new Thread(() -> {
                // Create a fixed thread pool with 20 threads
                ExecutorService executorService = Executors.newFixedThreadPool(20);
                List<Future<String>> futures = new ArrayList<>();
                KeyConfig.KeyDetails keyDetails = KeyConfig.getKeyDetails(initialPrefix);
                String appToken = keyDetails.getAppToken();
                String promoId = keyDetails.getPromoId();

                // Submit 20 login tasks in parallel
                for (int i = 0; i < 20; i++) {
                    futures.add(executorService.submit(() -> login(proxy, appToken, promoId, keyDetails.getTimeout())));
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

                // Wait for all tasks to complete and process the results
                for (Future<String> future : futures) {
                    try {
                        String promoCode = future.get(); // Get the result of the login
                        if (promoCode != null) {
                            amountOfPromo.incrementAndGet();
                            System.out.println(amountOfPromo + " Promo Code: " + promoCode);

                            // Store the promo code in the database
                            Keys key = new Keys();
                            key.setPrefix(initialPrefix);
                            key.setKeyValue(promoCode);
                            keysRepository.save(key);
                        }
                    } catch (Exception e) {
                        System.out.println("Error while retrieving promo code: " + e.getMessage());
                    }
                }

                // Shut down the ExecutorService
                executorService.shutdown();
            });

            proxyThreads.add(proxyThread);
            proxyThread.start();
        }

        // Wait for all proxy threads to finish
        for (Thread thread : proxyThreads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                System.out.println("Thread interrupted: " + e.getMessage());
            }
        }

        System.out.println("Total promo codes generated: " + amountOfPromo);
    }



    private static List<String> readProxiesFromFile(String filePath) {
        List<String> proxies = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                proxies.add(line.trim()); // Добавляем прокси в список
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return proxies;
    }

    private static String getKey(String proxy, String clientToken, String promoId) {
        String[] proxyParts = proxy.replace("http://", "").split(":");

        // Хост и порт
        String host = proxyParts[0];
        int port = Integer.parseInt(proxyParts[1]);    // Подготовка JSON body
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("promoId", promoId);

        // Создание OkHttpClient с настройкой прокси
        OkHttpClient client = new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port))) // Замените на свои данные прокси
                .build();

        // Подготовка HTTP-запроса
        RequestBody body = RequestBody.create(
                jsonBody.toString(), MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url("https://api.gamepromo.io/promo/create-code")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + clientToken)

                .post(body)
                .build();

        // Выполнение запроса и обработка ответа
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();

                // Парсинг JSON ответа
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                return jsonResponse.get("promoCode").getAsString();

            } else {
                System.out.println("Ошибка: " + response.code());
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }

    public static String login(String proxy, String appToken, String promoId, int timeMillWait) {
        String[] proxyParts = proxy.replace("http://", "").split(":");

        // Хост и порт
        String host = proxyParts[0];
        int port = Integer.parseInt(proxyParts[1]);
        // Генерация clientId
        String clientId = generateClientID();

        // Подготовка JSON body
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("clientOrigin", "deviceid");
        jsonBody.addProperty("appToken", appToken);
        jsonBody.addProperty("clientId", clientId);

        // Создание OkHttpClient с настройкой прокси
        OkHttpClient client = new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port))) // Замените на свои данные прокси
                .build();

        // Подготовка HTTP-запроса
        RequestBody body = RequestBody.create(
                jsonBody.toString(), MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url("https://api.gamepromo.io/promo/login-client")
                .header("Host", "api.gamepromo.io")
                .header("Content-Type", "application/json; charset=utf-8")
                .post(body)
                .build();

        // Выполнение запроса и обработка ответа
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();

                // Парсинг JSON ответа
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                String clientToken = jsonResponse.get("clientToken").getAsString();
                System.out.println(clientToken);
                while (!register(proxy, clientToken, promoId, timeMillWait)) {
                    Thread.sleep(1000);
                    System.out.println(false);
                }
                return getKey(proxy, clientToken, promoId);

            } else {
                System.out.println("Ошибка: " + response.code());
            }
        } catch (IOException | InterruptedException e) {
            System.out.println(e.getMessage());
        }
        return null;
    }


    private static boolean register(String proxy, String clientToken, String promoId, int timeMillsWait) {
        String[] proxyParts = proxy.replace("http://", "").split(":");

        // Хост и порт
        String host = proxyParts[0];
        int port = Integer.parseInt(proxyParts[1]);
        try {
            Thread.sleep(timeMillsWait);
        } catch (InterruptedException e) {
            System.out.println(e.getMessage());
        }

        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("eventId", UUID.randomUUID().toString());
        jsonBody.addProperty("eventOrigin", "undefined");
        jsonBody.addProperty("promoId", promoId);

        // Создание OkHttpClient с настройкой прокси
        OkHttpClient client = new OkHttpClient.Builder()
                .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port))) // Замените на свои данные прокси
                .build();

        // Подготовка HTTP-запроса
        RequestBody body = RequestBody.create(
                jsonBody.toString(), MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url("https://api.gamepromo.io/promo/register-event")
                .header("Host", "api.gamepromo.io")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer " + clientToken)
                .post(body)
                .build();

        // Выполнение запроса и обработка ответа
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();

                // Парсинг JSON ответа
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                return jsonResponse.get("hasCode").getAsBoolean();

            } else {
                System.out.println("Error1: " + response.code());
                return false;
            }
        } catch (Exception e) {
            System.out.println("Error2: "+e.getMessage());
            return false;
        }
    }

    public static String generateClientID() {
        long timestamp = System.currentTimeMillis();
        StringBuilder randomNumbers = new StringBuilder();

        for (int i = 0; i < 19; i++) {
            // Извлечение первой цифры случайно сгенерированного UUID
            randomNumbers.append(UUID.randomUUID().toString().replace("-", "").charAt(0));
        }

        return timestamp + "-" + randomNumbers.toString();
    }

    public KeysService(KeysRepository keysRepository) {
        this.keysRepository = keysRepository;
    }


    public void deleteAll(List<Keys> keys) {
        keysRepository.deleteAll(keys);
    }

    public List<Keys> findTop4ByPrefixes(String... prefixes) {
        List<Keys> keys = new ArrayList<>();
        for (String prefix : prefixes) {
            List<Keys> keysByPrefix = keysRepository.findTop4ByPrefix(prefix);
            keys.addAll(keysByPrefix);
        }
        return keys;
    }

    public List<Keys> findTop8ByPrefix(String prefix) {
        return keysRepository.findTop8ByPrefix(prefix);
    }

    public long countByPrefix(String prefix) {
        return keysRepository.countByPrefix(prefix);
    }


    public boolean areKeysAvailable() {
        for (String prefix : prefixes) {
            long count = countByPrefix(prefix);
            if (count < 4) {
                return false;
            }
        }
        return true;
    }

    public List<Keys> getKeys() {
        List<Keys> keysAll = new ArrayList<>();
        for (String prefix : prefixes) {
            List<Keys> keys = findTop4ByPrefixes(prefix);
            keysAll.addAll(keys);
            deleteAll(keys);
        }
        return keysAll;
    }

    public String getKeys(User user) {
        StringBuilder keysString = new StringBuilder((Objects.equals(user.getLanguage(), "ru")
                ? "\uD83D\uDD11 Ваши ключи:"
                : "\uD83D\uDD11 Your keys:")
                + "\n\n");
        for (String prefix : prefixes) {
            List<Keys> keys = findTop4ByPrefixes(prefix);
            for (Keys key : keys) {
                keysString.append("<code>").append(prefix).append("-").append(key.getKeyValue()).append("</code>").append("\n");
            }
            keysString.append("\n");
            deleteAll(keys);
        }
        return keysString.toString();
    }
}
