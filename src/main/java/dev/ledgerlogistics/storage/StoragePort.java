package dev.ledgerlogistics.storage;

public interface StoragePort {
    void createBucket(String name);
    boolean objectExists(String bucket, String key);
    String presignPodUpload(String bucket, String key, String contentType, long maxBytes, String idempotencyKey);
}
