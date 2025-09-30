package org.example;

public class Main {
    public static void main(String[] args) {
        HealthServer.start(); // agar HealthServer qo'shgan bo'lsangiz
        new Thread(() -> MainService.main(args)).start();
        new Thread(() -> MainAdmin.main(args)).start();
        System.out.println("Both bots started!");    }
}
