package dev.ledgerlogistics.shipment;

import dev.ledgerlogistics.storage.StoragePort;

import java.util.Objects;

public final class DeliveryEvidenceService {
    public enum Decision { UPLOAD_AUTHORIZED, EVIDENCE_ALREADY_RECORDED, COMPLIANCE_REVIEW }

    public record ShipmentEvent(
            String tenantId,
            String shipmentId,
            String eventId,
            String contentType,
            long contentLength,
            boolean deliveryException) {}

    public record EvidenceInstruction(Decision decision, String objectKey, String uploadUrl) {}

    private static final long MAX_POD_BYTES = 8L * 1024 * 1024;
    private final StoragePort storage;
    private final TenantBucketPolicy buckets;

    public DeliveryEvidenceService(StoragePort storage, TenantBucketPolicy buckets) {
        this.storage = Objects.requireNonNull(storage);
        this.buckets = Objects.requireNonNull(buckets);
    }

    public EvidenceInstruction register(ShipmentEvent event) {
        validate(event);
        String key = "shipments/" + event.shipmentId() + "/proof-of-delivery/" + event.eventId() + ".pdf";
        if (event.deliveryException()) {
            return new EvidenceInstruction(Decision.COMPLIANCE_REVIEW, key, null);
        }

        String bucket = buckets.bucketFor(event.tenantId());
        storage.createBucket(bucket);
        if (storage.objectExists(bucket, key)) {
            return new EvidenceInstruction(Decision.EVIDENCE_ALREADY_RECORDED, key, null);
        }
        String url = storage.presignPodUpload(
                bucket, key, event.contentType(), event.contentLength(), "pod-" + event.eventId());
        return new EvidenceInstruction(Decision.UPLOAD_AUTHORIZED, key, url);
    }

    private static void validate(ShipmentEvent event) {
        Objects.requireNonNull(event, "event");
        requireToken(event.shipmentId(), "shipmentId");
        requireToken(event.eventId(), "eventId");
        if (!"application/pdf".equals(event.contentType())) {
            throw new IllegalArgumentException("proof of delivery must be application/pdf");
        }
        if (event.contentLength() < 1 || event.contentLength() > MAX_POD_BYTES) {
            throw new IllegalArgumentException("proof of delivery must be between 1 byte and 8 MiB");
        }
    }

    private static void requireToken(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{3,80}")) {
            throw new IllegalArgumentException(field + " contains unsafe characters");
        }
    }
}
