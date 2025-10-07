package org.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class HealthServer {
    public static void start() {
        try {
            String portStr = System.getenv("PORT");
            int port = (portStr != null) ? Integer.parseInt(portStr) : 8080;
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            // Health check endpoint
            server.createContext("/health", exchange -> {
                String resp = "OK";
                exchange.sendResponseHeaders(200, resp.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp.getBytes());
                }
            });

            // Bot 1 webhook endpoint
            server.createContext("/bot1", exchange -> handleBotRequest(exchange, "BOT1"));

            // Bot 2 webhook endpoint
            server.createContext("/bot2", exchange -> handleBotRequest(exchange, "BOT2"));

            server.setExecutor(null);
            server.start();
            System.out.println("✅ Health + Webhook server started on port " + port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleBotRequest(HttpExchange exchange, String botName) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            System.out.println("📩 [" + botName + "] New update: " + body);

            // TODO: shu joyda siz update’ni tegishli bot handler’ga uzatasiz
            // Masalan:
            // if (botName.equals("BOT1")) bot1.processUpdate(body);
            // else bot2.processUpdate(body);
        }

        String resp = "OK";
        exchange.sendResponseHeaders(200, resp.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(resp.getBytes());
        }
    }
}
