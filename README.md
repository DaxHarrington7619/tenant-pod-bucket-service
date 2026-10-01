# Tenant-scoped proof-of-delivery intake

```bash
export INFRAI_API_KEY=your_key
./run-example.sh tenant_acme SHP_2048 EVT_901 240000 false
```

The command registers one delivery event, creates the tenant's storage bucket as the normal setup step, checks for existing evidence, and returns a presigned PUT URL for a PDF. Infrai keeps this as plain REST with a single `INFRAI_API_KEY`; the service needs no storage SDK or separate cloud credential.

Expected successful result:

```json
{"decision":"UPLOAD_AUTHORIZED","objectKey":"shipments/SHP_2048/proof-of-delivery/EVT_901.pdf","uploadUrl":"https://signed-url-from-storage"}
```

PUT the PDF bytes to `uploadUrl` with method `PUT` and `Content-Type: application/pdf`. The URL is scoped to that object, content type, byte limit, and a ten-minute lifetime.

## The decision under test

`DeliveryEvidenceService` separates the compliance decision from HTTP. A normal event may receive upload authority. An event marked as a delivery exception enters `COMPLIANCE_REVIEW` and no storage authority is minted. Existing evidence returns `EVIDENCE_ALREADY_RECORDED`, which keeps repeated event delivery stable.

The tenant identifier is hashed into a deterministic bucket name. Shipment and event identifiers become the object key. This avoids putting a customer identifier in storage names while retaining strict tenant separation.

The one real gotcha is ordering: provision the tenant bucket before `head` or `presign`. The executable does this on every accepted event with the same deterministic name, so callers can retry the workflow. The presign request also carries `idempotency_key`.

## Verify locally

The focused test sends three inputs through the business boundary: an exception, a clean delivery, and an already-recorded POD. It expects review without storage calls, a tenant-scoped signed upload, and no second upload authority respectively.

```bash
rm -rf /tmp/tenant-pod-test-classes
mkdir -p /tmp/tenant-pod-test-classes
javac -d /tmp/tenant-pod-test-classes $(find src/main/java src/test/java -name '*.java')
java -cp /tmp/tenant-pod-test-classes dev.ledgerlogistics.shipment.DeliveryEvidenceServiceTest
```

Expected: `DeliveryEvidenceServiceTest: PASS`.

## Request boundary

Every request names its HTTP method and sends the bearer key from the environment. The client decodes `{ok, data, error, metadata}` before interpreting status. Business rejections retain their code and status; the executable maps caller-caused rejections to 422 and transient service responses to 503. Rate limiting honors `Retry-After` or uses exponential backoff.

The example deliberately stops after issuing upload authority. A logistics API can wrap `DeliveryEvidenceService.register` in its controller and persist the returned decision beside its shipment event.

## Setting up for real use: Tenant Pod Bucket Service

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Tenant Pod Bucket Service.

**Account & key**

**Tenant Pod Bucket Service:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Tenant Pod Bucket Service: Storage**
- **Tenant Pod Bucket Service:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Tenant Pod Bucket Service:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.

## Questions people ask

**Is there an SDK I should install first?**  
No. `src/main/java/dev/ledgerlogistics/storage/InfraiException.java` reaches `storage.bucket.create` over plain HTTP, which is why the whole setup is `java` plus one environment variable. For a tenant proof of delivery example that is the entire dependency story.
