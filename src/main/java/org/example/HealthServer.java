package org.example;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;

public class HealthServer {
    public static void start() {
        try {
            String portStr = System.getenv("PORT");
            int port = (portStr != null) ? Integer.parseInt(portStr) : 8080;
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/health", exchange -> {
                String resp = "OK";
                exchange.sendResponseHeaders(200, resp.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp.getBytes());
                }
            });
            server.setExecutor(null);
            server.start();
            System.out.println("Health server started on port " + port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}