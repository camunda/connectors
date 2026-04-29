# Step 1: Inline Document Type (Platform-Level)

**Epic:** camunda/product-hub#3224
**Sub-issue:** camunda/connectors#5590

## Goal

Add a third document type, `inline`, alongside the existing `camunda` (Document Store) and `external` (URL) types. Inline documents carry their content directly in process variables — no Document Store roundtrip, no URL fetch. Suitable for small text/JSON files constructed from process data.

**Out of scope:**

- The "process gets stuck on too-big inline download" bug ([camunda/connectors#7049](https://github.com/camunda/connectors/issues/7049)) — fixed in Step 3 alongside the new return format.
- Per-connector field configuration (filename required vs. optional, hidden content type, etc.) — covered in Step 2.

## JSON Format

```json
// JSON content as object (natural for FEEL — no stringify needed)
{
  "camunda.document.type": "inline",
  "content": {"name": "Jane", "age": 28},
  "name": "me.json"
}

// Text content with explicit content type
{
  "camunda.document.type": "inline",
  "content": "col1,col2\nval1,val2",
  "name": "data.csv",
  "contentType": "text/csv"
}

// Minimal — just content, no name, no content type
{
  "camunda.document.type": "inline",
  "content": "hello world"
}
```

### Fields

| Field | Required | Type | Notes |
|---|---|---|---|
| `camunda.document.type` | yes | `"inline"` | Discriminator |
| `content` | **yes** | `Object` | String, JSON object/array, number, boolean. See polymorphism rules below. |
| `name` | no | `String` | Filename with extension. Drives MIME inference when `contentType` is not set. |
| `contentType` | no | `String` | Explicit MIME type (e.g., `application/json`). Overrides Tika inference. |

### Content polymorphism

| Java type at runtime | How bytes are computed |
|---|---|
| `String` | `content.getBytes(UTF_8)` |
| `Map`, `List`, JSON object, JSON array | `objectMapper.writeValueAsBytes(content)` |
| `Number`, `Boolean` | `objectMapper.writeValueAsBytes(content)` (e.g., `42` → `"42"`) |
| `byte[]` | No special handling — Jackson serializes to a base64 string. Inline is text-not-binary by design; users should not pass raw bytes. |
| Non-serializable POJO (circular refs, etc.) | Jackson throws → wrapped in `ConnectorInputException` (no retries) |
| `null` | Bean validation rejects with `ConnectorInputException` |

### Content type resolution order

1. Explicit `contentType` field (if set)
2. Tika MIME inference from `name` extension (if `name` set and has an extension)
3. Fallback: `application/octet-stream`

### Filename resolution

- If `name` is provided → returned by `metadata().getFileName()`
- If `name` is null → `InlineDocument` generates a random UUID at construction time, returned by `metadata().getFileName()` for the lifetime of that instance. **Not** serialized back into the reference JSON — round-trip preserves the original "no name" intent.

## Validation

Bean validation via `@NotNull` on `InlineDocumentReferenceModel.content`:

```java
record InlineDocumentReferenceModel(
    @NotNull(message = "Inline document content must not be null") Object content,
    String name,
    String contentType
) implements DocumentReferenceModel, InlineDocumentReference { ... }
```

Validation runs at the start of `DocumentFactoryImpl.resolve()`. Violations throw `ConnectorInputException` (no retries).

**This is a new pattern in the document layer.** Today, `ExternalDocumentReferenceModel` and `CamundaDocumentReferenceModel` have no validation — null fields propagate and fail at use time with opaque errors. We're explicitly improving this for inline only; existing types are not touched in this epic.

User-facing error message:
```
Found constraints violated while validating input:
 - Property: content: Validation failed. Original message: Inline document content must not be null
```

## Runtime Behavior

`InlineDocument` implements the `Document` interface:

| Method | Behavior |
|---|---|
| `asByteArray()` | Lazy: computed on first call, cached for instance lifetime. Single-threaded (connector job context); no synchronization. |
| `asInputStream()` | `new ByteArrayInputStream(asByteArray())` |
| `asBase64()` | `Base64.getEncoder().encodeToString(asByteArray())` |
| `metadata().getContentType()` | Resolved per the order above |
| `metadata().getFileName()` | Original `name` if set, otherwise generated UUID |
| `metadata().getSize()` | `asByteArray().length` |
| `metadata().getExpiresAt()` | `null` (inline documents are ephemeral, no expiration) |
| `metadata().getProcessDefinitionId()` | `""` (matches `ExternalDocument`) |
| `metadata().getProcessInstanceKey()` | `0L` (matches `ExternalDocument`) |
| `metadata().getCustomProperties()` | `Map.of()` |
| `reference()` | Returns `InlineDocumentReference` carrying the **original** `content`, `name`, `contentType` (round-trip fidelity — input JSON matches round-trip JSON) |
| `generateLink(...)` | Returns `null` (no URL for inline content) |

Memory: one `InlineDocument` instance holds both the original `Object content` (for `reference()` round-trip) and the lazily-computed bytes. Both bounded by Zeebe variable size limit (~4MB).

**No connector-side changes needed.** All connectors call `document.asByteArray()` and `document.metadata()` against the `Document` interface — works automatically.

## Files to Create

### `InlineDocumentReference` interface

**File:** `connector-sdk/core/src/main/java/io/camunda/connector/api/document/InlineDocumentReference.java`

```java
public interface InlineDocumentReference extends DocumentReference {
  Object content();
  String name();        // nullable
  String contentType(); // nullable
}
```

Nested in `DocumentReference` alongside `CamundaDocumentReference` and `ExternalDocumentReference`, consistent with existing structure.

### `InlineDocument` runtime class

**File:** `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/document/InlineDocument.java`

Constructor:
```java
public InlineDocument(Object content, String name, String contentType, ObjectMapper objectMapper)
```

- `objectMapper` is injected from `DocumentFactoryImpl` (avoids a static default that loses framework-level Jackson configuration).
- Generates UUID for missing `name` once at construction, caches it.
- Lazily computes bytes on first `asByteArray()` call; caches result.
- Wraps `JsonProcessingException` from `objectMapper.writeValueAsBytes(...)` in `ConnectorInputException`.

### `MimeTypeResolver` utility

**File:** `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/document/MimeTypeResolver.java`

Singleton wrapping Apache Tika's `MimeTypes.getDefaultMimeTypes()` — expensive to initialize, so cached statically.

```java
public final class MimeTypeResolver {
  private static final MimeTypes MIME_TYPES = MimeTypes.getDefaultMimeTypes();

  public static String resolveContentType(String explicitContentType, String fileName) {
    if (explicitContentType != null && !explicitContentType.isBlank()) {
      return explicitContentType;
    }
    if (fileName != null && !fileName.isBlank()) {
      try {
        return MIME_TYPES.forName(MIME_TYPES.getMimeType(fileName).getName()).getName();
      } catch (Exception e) {
        // fall through
      }
    }
    return "application/octet-stream";
  }
}
```

Tika is already on the classpath transitively (via `aws-bedrock`'s `FileUtil`). We add it as a direct dependency in `connector-runtime-core` to avoid relying on transitive resolution.

## Files to Modify

### `DocumentReferenceModel` — add `InlineDocumentReferenceModel`

**File:** `connector-runtime/jackson-datatype-document/src/main/java/io/camunda/connector/document/jackson/DocumentReferenceModel.java`

```java
@JsonSubTypes({
  @JsonSubTypes.Type(value = CamundaDocumentReferenceModel.class, name = "camunda"),
  @JsonSubTypes.Type(value = ExternalDocumentReferenceModel.class, name = "external"),
  @JsonSubTypes.Type(value = InlineDocumentReferenceModel.class, name = "inline")  // NEW
})
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface DocumentReferenceModel extends DocumentReference { ... }

@JsonInclude(JsonInclude.Include.NON_NULL)
record InlineDocumentReferenceModel(
    @NotNull(message = "Inline document content must not be null") Object content,
    String name,
    String contentType
) implements DocumentReferenceModel, InlineDocumentReference {

  @JsonProperty(DISCRIMINATOR_KEY)
  private String documentType() {
    return "inline";
  }
}
```

`@JsonInclude(NON_NULL)` ensures null `name` / `contentType` are omitted from serialized JSON — round-trip stays clean.

### `DocumentFactoryImpl` — add inline branch + inject ValidationProvider

**File:** `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/document/DocumentFactoryImpl.java`

```java
public class DocumentFactoryImpl implements DocumentFactory {

  private final CamundaDocumentStore documentStore;
  private final ValidationProvider validationProvider; // NEW
  private final ObjectMapper objectMapper;             // NEW
  // ... existing fields

  public DocumentFactoryImpl(
      CamundaDocumentStore documentStore,
      ValidationProvider validationProvider,
      ObjectMapper objectMapper) {
    this.documentStore = documentStore;
    this.validationProvider = validationProvider;
    this.objectMapper = objectMapper;
    // ...
  }

  @Override
  public Document resolve(DocumentReference reference) {
    if (reference == null) return null;
    validationProvider.validate(reference); // NEW — validates @NotNull etc.
    if (reference instanceof CamundaDocumentReference c) {
      return new CamundaDocument(c.getMetadata(), c, documentStore);
    }
    if (reference instanceof ExternalDocumentReference e) {
      return new ExternalDocument(e.url(), e.name(), downloadDocument);
    }
    if (reference instanceof InlineDocumentReference i) { // NEW
      return new InlineDocument(i.content(), i.name(), i.contentType(), objectMapper);
    }
    throw new IllegalArgumentException("Unknown document reference type: " + reference.getClass());
  }
}
```

The validation call is generic — it just runs bean validation on whatever was passed in. External and Camunda references have no validation annotations, so it's a no-op for them. Only inline references trigger violations. If we later add validation annotations to External/Camunda, they start working with no further changes.

Constructor signature change is breaking for anyone building `DocumentFactoryImpl` directly. Wired up automatically in Spring runtime via `OutboundConnectorRuntimeConfiguration` and `InboundConnectorRuntimeConfiguration`.

### `DocumentSerializer` — add inline branch

**File:** `connector-runtime/jackson-datatype-document/src/main/java/io/camunda/connector/document/jackson/serializer/DocumentSerializer.java`

```java
public void serialize(Document document, JsonGenerator jsonGenerator, SerializerProvider sp) throws IOException {
  var reference = document.reference();
  if (reference instanceof ExternalDocumentReference e) {
    jsonGenerator.writeObject(new ExternalDocumentReferenceModel(e.url(), e.name()));
  } else if (reference instanceof CamundaDocumentReference c) {
    // ... existing logic
  } else if (reference instanceof InlineDocumentReference i) { // NEW
    jsonGenerator.writeObject(new InlineDocumentReferenceModel(i.content(), i.name(), i.contentType()));
  } else {
    throw new IllegalArgumentException("Unsupported document reference type: " + reference);
  }
}
```

Without this branch, serializing an `InlineDocument` would throw — `CustomValueMapper` and any `ObjectMapper.writeValue(document)` call would fail.

### Spring runtime configuration — wire dependencies

**File:** `connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/outbound/OutboundConnectorRuntimeConfiguration.java`
**File:** `connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/inbound/InboundConnectorRuntimeConfiguration.java`

Update `DocumentFactoryImpl` bean creation to pass the new `ValidationProvider` and `ObjectMapper` constructor arguments. Both already exist as Spring beans.

## Components NOT Modified

For clarity, these were considered but require **no changes**:

- **`CustomValueMapper`** — uses `document.reference()` then `objectMapper.convertValue(reference, MAP_TYPE)`. Generic, works for any `DocumentReference` subtype automatically.
- **`DocumentDeserializer`** — uses Jackson's polymorphic deserialization on `DocumentReferenceModel`. Adding the new `@JsonSubTypes.Type` is sufficient; the deserializer code itself doesn't need updating.
- **`ExternalDocument` / `CamundaDocument` / `ExternalDocumentReferenceModel` / `CamundaDocumentReferenceModel`** — left as-is. Adding bean validation to those is intentionally out of scope to keep the diff small.

## Tests

### Unit tests for `InlineDocument`

| Scenario | Expectation |
|---|---|
| String content | `asByteArray()` returns UTF-8 bytes |
| JSON object content | `asByteArray()` returns Jackson-serialized JSON bytes |
| Empty string content | Creates an empty document (allowed) |
| `name = "report.json"`, no `contentType` | `metadata().getContentType()` returns `application/json` (Tika) |
| `name = "report"` (no extension), no `contentType` | Returns `application/octet-stream` |
| Explicit `contentType = "application/x-custom"` | Returns the explicit value, ignores name |
| `name = null`, `contentType = null` | Returns `application/octet-stream`; `getFileName()` returns a UUID |
| `getFileName()` called twice on same instance | Returns the same UUID both times (stability within instance) |
| `asByteArray()` called twice | Same byte array reference (lazy + cached) |
| Non-serializable content (circular ref) | Throws `ConnectorInputException` |
| `byte[]` content | No special handling; bytes contain Jackson's base64 string representation |

### Unit tests for `InlineDocumentReferenceModel`

| Scenario | Expectation |
|---|---|
| Deserialize valid JSON | Produces correct record |
| Round-trip (deserialize → serialize) preserves original content | Bytes match (modulo whitespace) |
| Serialize with null `name` and `contentType` | Output JSON has no `name` / `contentType` keys (`@JsonInclude(NON_NULL)`) |
| Validation: null `content` | `validator.validate(model)` returns one violation with the custom message |
| Validation: non-null `content`, null `name` and `contentType` | No violations |

### Unit tests for `DocumentFactoryImpl.resolve()`

| Scenario | Expectation |
|---|---|
| Inline reference with null content | Throws `ConnectorInputException` |
| Inline reference with valid content | Returns `InlineDocument` instance |
| External reference with null url (existing pattern) | Returns `ExternalDocument` (no validation, unchanged behavior) |

### Unit tests for `DocumentSerializer`

| Scenario | Expectation |
|---|---|
| Serialize `InlineDocument` | Produces JSON with discriminator + content + (optionally) name + contentType |
| Round-trip via `ObjectMapper` | Final JSON matches original input JSON |

### Integration test

End-to-end: connector input JSON → Jackson deserialization → `Document` resolution → `asByteArray()` returns expected bytes. Run for each content type variant.

## Open Considerations

- **Bean validation in document layer is a new pattern.** If team wants consistency, a follow-up could add `@NotNull` to `ExternalDocumentReferenceModel.url` and `CamundaDocumentReferenceModel.documentId` / `storeId`. Out of scope here.
- **`MimeTypeResolver` placement.** Currently in `connector-runtime-core`. If other modules later need MIME inference, can be promoted to a dedicated module without API changes.
- **Self-managed runtimes** that don't use Spring will need to wire `ValidationProvider` and `ObjectMapper` into `DocumentFactoryImpl` themselves (constructor signature change).
