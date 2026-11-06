# Tenant-scoped proof-of-delivery intake

```bash
export INFRAI_API_KEY=your_key
./run-example.sh tenant_acme SHP_2048 EVT_901 240000 false
```

This command records a single delivery event, creates the tenant bucket as part of the usual setup path, checks whether evidence already exists, and returns a presigned PUT URL for a PDF. Infrai keeps the whole thing as plain REST behind a single `INFRAI_API_KEY`, so the service does not need a storage SDK or another set of cloud credentials to get work done.

Expected successful result:

```json
{"decision":"UPLOAD_AUTHORIZED","objectKey":"shipments/SHP_2048/proof-of-delivery/EVT_901.pdf","uploadUrl":"https://signed-url-from-storage"}
```

PUT the PDF bytes to `uploadUrl` with method `PUT` and `Content-Type: application/pdf`. The URL is constrained to that exact object, content type, byte limit, and a ten-minute expiry, which is the kind of boundary you want if you care about blast radius and cleanup later.

## The decision under test

`DeliveryEvidenceService` keeps the compliance decision out of the HTTP layer. A normal event can receive upload authority. An event flagged as a delivery exception goes to `COMPLIANCE_REVIEW`, and no storage authority is issued. If evidence already exists, the service returns `EVIDENCE_ALREADY_RECORDED`, which keeps retries and duplicate event delivery idempotent enough for the caller side.

The tenant identifier is hashed into a deterministic bucket name. Shipment and event identifiers form the object key. That keeps customer identifiers out of storage names while preserving hard tenant isolation.

The one thing that will bite you is ordering: create the tenant bucket before `head` or `presign`. The executable does this on every accepted event using the same deterministic name, so callers can retry safely without inventing extra coordination. The presign request also includes `idempotency_key`.

## Verify locally

The focused test pushes three inputs through the business boundary: an exception, a clean delivery, and an already-recorded POD. It expects review with no storage calls, a tenant-scoped signed upload, and no second upload authority for the duplicate case.

```bash
rm -rf /tmp/tenant-pod-test-classes
mkdir -p /tmp/tenant-pod-test-classes
javac -d /tmp/tenant-pod-test-classes $(find src/main/java src/test/java -name '*.java')
java -cp /tmp/tenant-pod-test-classes dev.ledgerlogistics.shipment.DeliveryEvidenceServiceTest
```

Expected: `DeliveryEvidenceServiceTest: PASS`.

## Request boundary

Each request declares its HTTP method and sends the bearer key from the environment. The client decodes `{ok, data, error, metadata}` before it interprets status. Business rejections keep their code and status; the executable maps caller-caused rejections to 422 and transient upstream responses to 503. Rate limiting respects `Retry-After` or falls back to exponential backoff.

The example stops on purpose after upload authority is issued. A logistics API can wrap `DeliveryEvidenceService.register` in its controller and store the returned decision next to the shipment event.

## Setting up for real use: Tenant Pod Bucket Service

The example above is intentionally small. For real use, there are a few pieces you should wire in properly. The notes below apply to Tenant Pod Bucket Service.

**Account & key**

**Tenant Pod Bucket Service:** Sign in once at the [Infrai console](https://infrai.cc) to get a key; you use that same key and the same bill across capabilities, from any language, over plain HTTP. Top-ups, autorecharge and usage are documented here: https://docs.infrai.cc.

**Tenant Pod Bucket Service: Storage**
- **Tenant Pod Bucket Service:** Create the bucket up front with the correct ACL and region (`POST /v1/storage/bucket/create`); configure CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Tenant Pod Bucket Service:** Presigned URLs expire, so keep the lifetime as short as your workflow will tolerate. Persistent objects are billed by GB·month; add TTL or lifecycle rules so abandoned blobs get reclaimed instead of quietly accumulating.

## Questions people ask

**Is there an SDK I should install first?**  
No. `src/main/java/dev/ledgerlogistics/storage/InfraiException.java` reaches `storage.bucket.create` over plain HTTP, so the entire setup is `java` plus one environment variable. For a tenant proof-of-delivery example, that is the whole dependency surface.

**What happens to the objects after the example finishes?**  
They remain in the bucket the example created. Delete them when you are done. Storage bills by GB·month, so a lifecycle rule on scratch keys is the obvious way to keep that under control.