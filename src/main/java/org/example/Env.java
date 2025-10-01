package org.example;
public final class Env {
    private static final io.github.cdimascio.dotenv.Dotenv DOTENV =
            io.github.cdimascio.dotenv.Dotenv.configure().ignoreIfMissing().load();

    public static String get(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) v = DOTENV.get(key);
        return v;
    }
}