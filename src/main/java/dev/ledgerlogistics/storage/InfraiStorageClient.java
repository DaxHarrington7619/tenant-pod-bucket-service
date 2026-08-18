package dev.ledgerlogistics.storage;

import dev.ledgerlogistics.config.StorageConfig;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public final class InfraiStorageClient implements StoragePort {
    private final StorageConfig config;
    private final HttpClient http;

    public InfraiStorageClient(StorageConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build());
    }

    InfraiStorageClient(StorageConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public void createBucket(String name) {
        call("POST", "/v1/storage/bucket/create", "{\"name\":" + Json.string(name) + "}");
    }

    @Override
    public boolean objectExists(String bucket, String key) {
        Map<String, Object> data = call("GET", "/v1/storage/object/head/" + segment(bucket) + "/" + segment(key), null);
        return Boolean.TRUE.equals(data.get("found"));
    }

    @Override
    public String presignPodUpload(String bucket, String key, String contentType, long maxBytes, String idempotencyKey) {
        String body = "{\"op\":\"put\",\"expires_seconds\":600,\"content_type\":"
                + Json.string(contentType) + ",\"max_bytes\":" + maxBytes
                + ",\"idempotency_key\":" + Json.string(idempotencyKey) + "}";
        Map<String, Object> data = call("POST", "/v1/storage/object/presign/" + segment(bucket) + "/" + segment(key), body);
        Object url = data.get("url");
        if (!(url instanceof String signedUrl) || signedUrl.isBlank()) {
            throw new IllegalStateException("Successful presign response did not contain url");
        }
        return signedUrl;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String method, String path, String body) {
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(config.baseUri().resolve(path))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
            else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));

            HttpResponse<String> response;
            try {
                response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new IllegalStateException("Storage transport failed", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Storage request interrupted", e);
            }

            Object decoded = Json.parse(response.body());
            if (!(decoded instanceof Map<?, ?> rawEnvelope)) {
                throw new IllegalStateException("Storage response was not an envelope");
            }
            Map<String, Object> envelope = (Map<String, Object>) rawEnvelope;
            if (Boolean.TRUE.equals(envelope.get("ok"))) {
                Object data = envelope.get("data");
                return data instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            }
            if (response.statusCode() == 429 && attempt < config.maxAttempts()) {
                pause(retryDelay(response, attempt));
                continue;
            }
            Map<String, Object> error = envelope.get("error") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map : Map.of();
            String code = String.valueOf(error.getOrDefault("code", "INFRAI_REJECTED"));
            String message = String.valueOf(error.getOrDefault("message", code));
            throw new InfraiException(code, message, response.statusCode());
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        String header = response.headers().firstValue("Retry-After").orElse("");
        try {
            long seconds = Long.parseLong(header);
            return Duration.ofSeconds(Math.max(1, seconds));
        } catch (NumberFormatException ignored) {
            return Duration.ofMillis(250L * (1L << (attempt - 1)));
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", e);
        }
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
