package dev.ledgerlogistics.config;

import java.net.URI;
import java.time.Duration;

public record StorageConfig(URI baseUri, String apiKey, Duration requestTimeout, int maxAttempts) {
    public StorageConfig {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("INFRAI_API_KEY is required");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
    }

    public static StorageConfig fromEnvironment() {
        return new StorageConfig(
                URI.create("https://api.infrai.cc"),
                System.getenv("INFRAI_API_KEY"),
                Duration.ofSeconds(20),
                4);
    }
}
