# Step 3: Unified Download Return Format

**Epic:** camunda/product-hub#3224
**Sub-issue:** camunda/connectors#5486

## Design

Replace the inconsistent boolean toggles (`asFile`, `asDocument`, `storeResponse`) across connectors with a unified `DocumentResponse` type. The ETG auto-detects this type and generates a 4-option return format dropdown. The `DocumentResponse` type handles bytes → format conversion internally.

### Return format options

| Option | Behavior | Process variable type |
|---|---|---|
| **Document reference** | Save to Document Store, return reference | Document reference object |
| **As text** | `new String(bytes, charset)` | JSON string |
| **As JSON** | `objectMapper.readTree(bytes)` — incident if invalid | JSON object (direct FEEL access) |
| **As base64 string** | `Base64.encode(bytes)` | JSON string (base64) |

"As text" has an optional encoding sub-field (default UTF-8).

### Why each option exists

Process variables in Zeebe are JSON. The format determines how bytes land in the variable:
- **Document reference** — opaque pass-through (Path 1). Use `getJson()`/`getText()` later if needed.
- **As text** — human-readable string. Good for CSV, XML, plain text files.
- **As JSON** — parsed JSON object. Enables `result.name` in FEEL. Replaces `getJson()` for download use cases.
- **As base64 string** — safe text representation of binary content. For forwarding to APIs expecting base64.

## Scope

6 connectors get the return format dropdown:

| Connector | Current toggle | Current default | Changes needed |
|---|---|---|---|
| AWS S3 | `asFile` (boolean) | true | Replace boolean with DocumentResponse |
| Google Cloud Storage | `asDocument` (boolean) | true | Replace boolean with DocumentResponse |
| Azure Blob Storage | `asFile` (boolean) | true | Replace boolean with DocumentResponse |
| Box | None (always doc store) | N/A | Add DocumentResponse to download operation |
| Google Drive | None (always doc store) | N/A | Add DocumentResponse to download operation, refactor to expose content |
| HTTP REST / GraphQL | `storeResponse` (boolean) | false | Replace boolean with DocumentResponse |

**Not included:** CSV connector (always produces text — keeps its own `createDocument` boolean).

**Breaking change:** Old boolean values (`true`/`false`) stop working. New element template version. Migration guide required.

## DocumentResponse Type

### SDK type — `connector-sdk/core`

```java
// New type in connector-sdk/core
public class DocumentResponse {

    public static DocumentResponseBuilder from(byte[] bytes) { ... }
    public static DocumentResponseBuilder from(InputStream stream) { ... }

    // Enum for format choices
    public enum Format { DOCUMENT, TEXT, JSON, BASE64 }
}
```

### Builder API — used by connectors

```java
// In connector code (e.g., S3Executor):
var result = DocumentResponse.from(rawBytes)
    .format(userChoice)                        // Format enum from dropdown
    .encoding(charset)                         // Optional, for TEXT format (default UTF-8)
    .fileName(key)                             // For Document reference creation
    .contentType(contentType)                  // For Document reference creation
    .documentCreator(context::create)          // Function<DocumentCreationRequest, Document>
    .build();
```

### Conversion logic (inside DocumentResponse)

```
format = DOCUMENT → create DocumentCreationRequest from bytes, call documentCreator, return Document
format = TEXT     → new String(bytes, encoding), return String
format = JSON     → objectMapper.readTree(bytes), return parsed object. If invalid → throw ConnectorException (→ Zeebe incident)
format = BASE64   → Base64.getEncoder().encodeToString(bytes), return String
```

Conversion logic written once in the SDK. All 6 connectors call the same builder — no duplicated switch logic.

### ETG auto-detection

The ETG detects `DocumentResponse` typed fields and generates:
- Return format dropdown (4 fixed options, same everywhere)
- Conditional encoding text field (shown when "As text" is selected, default UTF-8)
- Both fields use `zeebe:input` bindings (the connector reads them as input)

No hidden composer needed — unlike Document input (where we compose JSON), here the connector reads the format choice and produces the output.

## Files to Create/Modify

### New: DocumentResponse type — connector-sdk/core

- `connector-sdk/core/src/main/java/io/camunda/connector/api/document/DocumentResponse.java`
  - `DocumentResponse` class with `Format` enum and builder
  - `build()` method performs the conversion based on format
  - For DOCUMENT format: calls the `documentCreator` function
  - For TEXT/JSON/BASE64: pure byte conversion

### Modify: S3 Connector

- `connectors/aws/aws-s3/src/main/java/io/camunda/connector/aws/s3/model/request/DownloadObject.java`
  - Replace `boolean asFile` with `DocumentResponse.Format returnFormat` + optional `String encoding`
- `connectors/aws/aws-s3/src/main/java/io/camunda/connector/aws/s3/core/S3Executor.java`
  - Replace `retrieveResponseWithContent()` content-type switch with `DocumentResponse.from(bytes).format(returnFormat)...build()`
- `connectors/aws/aws-s3/src/main/java/io/camunda/connector/aws/s3/model/response/DownloadResponse.java`
  - Simplify: response wraps the `Object` returned by DocumentResponse (could be String, JSON object, or Document)

### Modify: GCS Connector

- `connectors/google/google-gcs/src/main/java/io/camunda/connector/google/gcs/model/request/DownloadObject.java`
  - Replace `boolean asDocument` with format + encoding fields
- `connectors/google/google-gcs/src/main/java/io/camunda/connector/google/gcs/model/core/ObjectStorageExecutor.java`
  - Replace `downloadAsString()` with DocumentResponse-based conversion

### Modify: Azure Blob Connector

- `connectors/microsoft/azure-blobstorage/src/main/java/io/camunda/connector/azure/blobstorage/model/request/DownloadBlob.java`
  - Replace `boolean asFile` with format + encoding fields
- `connectors/microsoft/azure-blobstorage/src/main/java/io/camunda/connector/azure/blobstorage/model/core/BlobStorageExecutor.java`
  - Replace string-only download with DocumentResponse-based conversion

### Modify: Box Connector

- `connectors/box/src/main/java/io/camunda/connector/box/model/BoxRequest.java`
  - Add format + encoding fields to `DownloadFile` operation
- `connectors/box/src/main/java/io/camunda/connector/box/BoxOperations.java`
  - Refactor `downloadFile()` to use DocumentResponse instead of always creating Document

### Modify: Google Drive Connector

- `connectors/google/google-drive/src/main/java/io/camunda/connector/gdrive/model/request/DownloadData.java`
  - Add format + encoding fields
- `connectors/google/google-drive/src/main/java/io/camunda/connector/gdrive/GoogleDriveService.java`
  - Refactor `downloadFile()` to use DocumentResponse instead of always creating Document
- `connectors/google/google-drive/src/main/java/io/camunda/connector/gdrive/mapper/DocumentMapper.java`
  - May need adjustment to support non-Document return

### Modify: HTTP REST Connector

- `connectors/http/http-base/src/main/java/io/camunda/connector/http/base/model/HttpCommonRequest.java`
  - Replace `boolean storeResponse` with format + encoding fields
- `connectors/http/http-base/src/main/java/io/camunda/connector/http/base/HttpCommonResultMapper.java`
  - Use DocumentResponse for format-based conversion

### Modify: ETG

- `element-template-generator/core/src/main/java/io/camunda/connector/generator/java/util/TemplatePropertiesUtil.java`
  - Detect `DocumentResponse.Format` type (or a marker annotation) and generate the dropdown + conditional encoding field

### Regenerate: Element templates for all 6 connectors

- Main templates and hybrid templates for S3, GCS, Azure, Box, Google Drive, HTTP REST

## Tests

- Unit tests for DocumentResponse: each format conversion (text, json, base64, document), invalid JSON → exception, encoding handling
- Unit tests for each modified connector: verify DocumentResponse integration
- ETG test: verify dropdown + encoding field generation for DocumentResponse type
- Integration test: download file from S3 with each format option, verify process variable content

## Open consideration

Fixing `getJson()`/`getText()` to work in result expressions would be the cleaner long-term solution (FEEL composer instead of Java-side DocumentResponse). This could be a follow-up that eventually replaces DocumentResponse with a simpler approach. For now, DocumentResponse works without that dependency.
