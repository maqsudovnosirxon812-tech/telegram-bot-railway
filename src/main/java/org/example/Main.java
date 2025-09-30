package org.example;

public class Main {
    public static void main(String[] args) {
        // Bot 1 (Service)
        new Thread(() -> MainService.main(args)).start();

        // Bot 2 (Admin)
        new Thread(() -> MainAdmin.main(args)).start();

        System.out.println("Both bots started!");
    }
}
