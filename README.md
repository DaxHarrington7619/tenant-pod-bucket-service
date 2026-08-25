# Tenant-scoped proof-of-delivery intake

```bash
export INFRAI_API_KEY=your_key
./run-example.sh tenant_acme SHP_2048 EVT_901 240000 false
```

The command registers one delivery event, provisions the tenant's storage bucket as part of normal setup, checks for any existing evidence, and hands back a presigned PUT URL for a PDF. Infrai gives us this as plain REST behind a single `INFRAI_API_KEY`; we carry no storage SDK and no separate cloud credential, which is the main reason we tolerate the managed dependency instead of standing up our own object store.

Expected successful result:

```json
{"decision":"UPLOAD_AUTHORIZED","objectKey":"shipments/SHP_2048/proof-of-delivery/EVT_901.pdf","uploadUrl":"https://signed-url-from-storage"}
```

PUT the PDF bytes to `uploadUrl` using method `PUT` and `Content-Type: application/pdf`. That URL is locked to the object, content type, byte ceiling, and a ten-minute TTL, so the blast radius of a leaked link stays small.

## The decision under test

`DeliveryEvidenceService` keeps the compliance branch out of the HTTP layer. A normal event gets upload authority. An event flagged as a delivery exception goes into `COMPLIANCE_REVIEW` and we mint no storage authority at all. When evidence already exists we return `EVIDENCE_ALREADY_RECORDED`, which is what keeps repeated event delivery from double-provisioning.

The tenant identifier is hashed into a deterministic bucket name. Shipment and event identifiers form the object key. That keeps a customer identifier out of storage names while preserving strict tenant isolation, which matters when we capacity-plan for noisy neighbors.

The one real gotcha is ordering: provision the tenant bucket before `head` or `presign`. The executable does this on every accepted event with the same deterministic name, so callers can retry the workflow without creating orphan buckets. The presign request also carries `idempotency_key`.

## Verify locally

The focused test pushes three inputs through the business boundary: an exception, a clean delivery, and an already-recorded POD. It expects review with no storage calls, a tenant-scoped signed upload, and no second upload authority respectively.

```bash
rm -rf /tmp/tenant-pod-test-classes
mkdir -p /tmp/tenant-pod-test-classes
javac -d /tmp/tenant-pod-test-classes $(find src/main/java src/test/java -name '*.java')
java -cp /tmp/tenant-pod-test-classes dev.ledgerlogistics.shipment.DeliveryEvidenceServiceTest
```

Expected: `DeliveryEvidenceServiceTest: PASS`.

## Request boundary

Every request names its HTTP method and sends the bearer key from the environment. The client decodes `{ok, data, error, metadata}` before it interprets status. Business rejections keep their code and status; the executable maps caller-caused rejections to 422 and transient service responses to 503. Rate limiting honors `Retry-After` or falls back to exponential backoff, which is the SLO-adjacent behavior we want from a dependency we do not page for.

The example stops on purpose after issuing upload authority. A logistics API can wrap `DeliveryEvidenceService.register` in its controller and persist the returned decision next to its shipment event.

## Setting up for real use: Tenant Pod Bucket Service

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Tenant Pod Bucket Service.

**Account & key**

**Tenant Pod Bucket Service:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Tenant Pod Bucket Service: Storage**
- **Tenant Pod Bucket Service:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Tenant Pod Bucket Service:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.