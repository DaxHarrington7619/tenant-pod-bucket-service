package dev.ledgerlogistics;

import dev.ledgerlogistics.config.StorageConfig;
import dev.ledgerlogistics.shipment.DeliveryEvidenceService;
import dev.ledgerlogistics.shipment.DeliveryEvidenceService.EvidenceInstruction;
import dev.ledgerlogistics.shipment.DeliveryEvidenceService.ShipmentEvent;
import dev.ledgerlogistics.shipment.TenantBucketPolicy;
import dev.ledgerlogistics.storage.InfraiException;
import dev.ledgerlogistics.storage.InfraiStorageClient;

public final class PodIntake {
    private PodIntake() {}

    public static void main(String[] args) {
        if (args.length != 5) {
            System.err.println("usage: PodIntake TENANT SHIPMENT EVENT BYTES EXCEPTION");
            System.exit(2);
        }
        try {
            DeliveryEvidenceService service = new DeliveryEvidenceService(
                    new InfraiStorageClient(StorageConfig.fromEnvironment()), new TenantBucketPolicy());
            EvidenceInstruction result = service.register(new ShipmentEvent(
                    args[0], args[1], args[2], "application/pdf", Long.parseLong(args[3]), Boolean.parseBoolean(args[4])));
            System.out.printf("{\"decision\":\"%s\",\"objectKey\":\"%s\",\"uploadUrl\":%s}%n",
                    result.decision(), result.objectKey(),
                    result.uploadUrl() == null ? "null" : "\"" + result.uploadUrl() + "\"");
        } catch (InfraiException e) {
            int callerStatus = e.isClientRejection() ? 422 : 503;
            System.err.printf("storage request rejected: status=%d code=%s%n", callerStatus, e.code());
            System.exit(1);
        }
    }
}
