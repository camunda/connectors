# Step 1: Inline Document Type (Platform-Level)

**Epic:** camunda/product-hub#3224
**Sub-issue:** camunda/connectors#5590

## Design

New document type alongside `camunda` and `external`. Discriminator value: `"inline"`.

```json
// JSON content as object (natural for FEEL — no stringify needed):
{
  "camunda.document.type": "inline",
  "content": {"name": "Stefanie", "age": 32},
  "name": "me.json"
}

// Text content as string (CSV, TXT, XML, etc.):
{
  "camunda.document.type": "inline",
  "content": "col1,col2\nval1,val2",
  "name": "data.csv"
}
```

### Fields

- **`content`** (required, polymorphic `Object`): Accepts JSON strings, objects, arrays, or numbers. If the value is a string → stored as UTF-8 text bytes. If the value is an object/array/number → serialized to JSON bytes via Jackson. Users can use FEEL's `base64encode()` to encode binary content as a base64 text string if needed.
- **`name`** (required): Filename with extension. Content type inferred from extension via Apache Tika's `MimeTypes.getDefaultMimeTypes()` (already on classpath, used by aws-bedrock `FileUtil`).

**No `encoding` field.** Content is always literal. For binary file creation from decoded bytes, users should use Camunda Document (document store) or External Document (URL) paths.

### Validation — lenient

- `null` content or `null` name → validation error
- Empty content → creates an empty file (allowed)
- Name without extension → content type defaults to `application/octet-stream`

### Runtime behavior

- Materialized into a full `Document` object (in-memory, like `ExternalDocument`)
- NOT stored in the Camunda Document Store — ephemeral, in-memory only
- No separate file size limit — Zeebe variable size limit (~4MB) applies naturally
- `asByteArray()`: String content → `content.getBytes(UTF_8)`. Object content → `objectMapper.writeValueAsBytes(content)`
- `metadata()`: contentType from Tika MIME mapping of filename extension, size from byte length, fileName from name field
- `reference()`: returns `InlineDocumentReference` carrying content + name (round-trips back to same JSON structure). Required by `DocumentSerializer` for serialization and `CustomValueMapper` for FEEL access.
- `generateLink()`: returns `null` (no URL for inline content)

**No connector-side changes needed.** InlineDocument implements the `Document` interface. Existing upload code in all connectors calls `document.asByteArray()` and `document.metadata()` — works automatically.

## Files to Create/Modify

### New: `InlineDocumentReference` interface — connector-sdk/core

- `connector-sdk/core/src/main/java/io/camunda/connector/api/document/InlineDocumentReference.java`
- Extends `DocumentReference`
- Methods: `content()` (returns `Object`), `name()` (returns `String`)

### New: `InlineDocumentReferenceModel` record — jackson-datatype-document

- `connector-runtime/jackson-datatype-document/src/main/java/io/camunda/connector/document/jackson/DocumentReferenceModel.java`
- Add `InlineDocumentReferenceModel` record as new `@JsonSubTypes.Type` with `name = "inline"`
- Fields: `content` (`Object`), `name` (`String`)
- `documentType()` method returns `"inline"`

### New: `InlineDocument` class — connector-runtime-core

- `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/document/InlineDocument.java`
- Constructor: takes `Object content`, `String name`, `ObjectMapper objectMapper`
- Holds content bytes in memory (computed once from content field)
- Infers metadata via Tika MIME mapping
- Implements all `Document` interface methods

### Modify: `DocumentFactoryImpl`

- `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/document/DocumentFactoryImpl.java`
- Add `instanceof InlineDocumentReference` branch in `resolve()` → creates `InlineDocument`

### Modify: `DocumentSerializer`

- `connector-runtime/jackson-datatype-document/src/main/java/io/camunda/connector/document/jackson/serializer/DocumentSerializer.java`
- Add `instanceof InlineDocumentReference` branch → writes `InlineDocumentReferenceModel` JSON

## Tests

- Unit tests for `InlineDocument`: text content, JSON object content, empty content, metadata inference from various extensions, no-extension fallback
- Serialization/deserialization round-trip tests for `InlineDocumentReferenceModel`
- Integration test: inline document JSON → Jackson deserialization → Document object → `asByteArray()` returns correct bytes
