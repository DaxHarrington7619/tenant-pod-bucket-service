package dev.ledgerlogistics.shipment;

import dev.ledgerlogistics.shipment.DeliveryEvidenceService.Decision;
import dev.ledgerlogistics.shipment.DeliveryEvidenceService.ShipmentEvent;
import dev.ledgerlogistics.storage.StoragePort;

public final class DeliveryEvidenceServiceTest {
    public static void main(String[] args) {
        reviewPathDoesNotAuthorizeUpload();
        cleanDeliveryGetsTenantScopedUpload();
        existingEvidenceIsNotReissued();
        System.out.println("DeliveryEvidenceServiceTest: PASS");
    }

    private static void reviewPathDoesNotAuthorizeUpload() {
        FakeStorage storage = new FakeStorage(false);
        DeliveryEvidenceService service = new DeliveryEvidenceService(storage, new TenantBucketPolicy());
        var result = service.register(event(true));
        check(result.decision() == Decision.COMPLIANCE_REVIEW, "exception must enter review");
        check(storage.calls == 0, "review path must not create upload authority");
    }

    private static void cleanDeliveryGetsTenantScopedUpload() {
        FakeStorage storage = new FakeStorage(false);
        DeliveryEvidenceService service = new DeliveryEvidenceService(storage, new TenantBucketPolicy());
        var result = service.register(event(false));
        check(result.decision() == Decision.UPLOAD_AUTHORIZED, "clean delivery should authorize upload");
        check("https://signed.example/upload".equals(result.uploadUrl()), "signed URL should be returned");
        check(storage.bucket.startsWith("logistics-"), "tenant bucket should be selected");
        check(storage.calls == 3, "bucket, head, and presign boundaries should run");
    }

    private static void existingEvidenceIsNotReissued() {
        FakeStorage storage = new FakeStorage(true);
        DeliveryEvidenceService service = new DeliveryEvidenceService(storage, new TenantBucketPolicy());
        var result = service.register(event(false));
        check(result.decision() == Decision.EVIDENCE_ALREADY_RECORDED, "existing evidence should be stable");
        check(storage.calls == 2, "existing evidence should stop before presign");
    }

    private static ShipmentEvent event(boolean exception) {
        return new ShipmentEvent("tenant_acme", "SHP_2048", "EVT_901", "application/pdf", 240_000, exception);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeStorage implements StoragePort {
        private final boolean exists;
        private int calls;
        private String bucket;

        private FakeStorage(boolean exists) { this.exists = exists; }
        public void createBucket(String name) { bucket = name; calls++; }
        public boolean objectExists(String bucket, String key) { calls++; return exists; }
        public String presignPodUpload(String bucket, String key, String type, long bytes, String idempotencyKey) {
            calls++;
            return "https://signed.example/upload";
        }
    }
}
