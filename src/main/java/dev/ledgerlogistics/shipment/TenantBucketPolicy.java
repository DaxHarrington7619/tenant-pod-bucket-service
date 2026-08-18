package dev.ledgerlogistics.shipment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class TenantBucketPolicy {
    public String bucketFor(String tenantId) {
        if (tenantId == null || !tenantId.matches("[A-Za-z0-9_-]{3,64}")) {
            throw new IllegalArgumentException("tenantId must contain 3-64 safe characters");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(tenantId.getBytes(StandardCharsets.UTF_8));
            return "logistics-" + HexFormat.of().formatHex(digest, 0, 10);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
