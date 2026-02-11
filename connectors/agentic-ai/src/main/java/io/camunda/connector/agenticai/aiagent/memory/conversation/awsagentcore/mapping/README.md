# AWS AgentCore Memory Message Mapping

## Overview

This package implements the bidirectional mapping between Camunda's internal `Message` types and AWS AgentCore Memory's `Event` payloads. The mapping is designed to optimize for AWS's long-term memory extraction while enabling exact 1:1 round-trip reconstruction.

## Design Principles

### 1. Optimize for AWS Long-Term Memory Extraction
Since only conversational payloads feed long-term memory, we store **human-readable text** in conversational payloads wherever possible.

### 2. Enable 1:1 Round-Trip Mapping
To reconstruct exact internal `Message` objects, we use:
- **Content type discriminator**: `Content` objects already have `@JsonTypeInfo(property="type")`
- **Typed blob envelopes**: For structured data without native discriminators (toolCalls, toolCallResults)

### 3. One Internal Message = One AgentCore Event
Each internal message maps to exactly one AgentCore event, which may contain multiple payloads.

## Components

### AwsAgentCoreConversationMapper
Main mapper class that handles bidirectional conversion:
- `toPayloads(Message)` → `List<PayloadType>` for storing
- `fromEvent(Event)` → `List<Message>` for loading

### BlobEnvelope
Versioned wrapper for structured blob payloads with type discrimination.

### BlobEnvelopeType
Enum defining supported blob types: `TOOL_CALLS`, `TOOL_CALL_RESULTS`, `MESSAGE_CONTENT`.

## Message Type Mappings

### SystemMessage
**Storage:** Not stored in AgentCore (stored in `AwsAgentCoreConversationContext` only)

**Rationale:** AWS AgentCore doesn't have a SYSTEM role, and system prompts are applied at inference time, not stored as conversation history.

---

### UserMessage
**Storage:** One event with multiple payloads

**Mapping:**
```
UserMessage {
  name: "Alice",
  content: [
    TextContent("Hello"),
    DocumentContent(...)
  ],
  metadata: {
    "userId": "user123",
    "sessionId": "session456"
  }
}

→ AgentCore Event with payloads:
  [
    Conversational { role: USER, content: { text: "Hello" } },
    Blob { 
      "blobType": "camunda.messageContent",
      "version": 1,
      "content": {
        "type": "document",
        "document": { ... },
        "metadata": { ... }
      }
    }
  ]
  metadata: {
    "userId": "user123",
    "sessionId": "session456"
  }
```

**Rules:**
- Each `TextContent` → separate Conversational USER payload with that text
- Each non-Text `Content` → Blob envelope with `blobType: "camunda.messageContent"`
  - The Content object is wrapped in the envelope with its native `type` discriminator preserved
- `metadata` is stored in Event's metadata field (as Map<String, MetadataValue>)
  - All values are converted to strings (complex objects are JSON serialized)
- `name` field is not stored (lossy)

**Deserialization:**
- Conversational USER payloads → collect text into `TextContent` list
- Blob payloads with `blobType: "camunda.messageContent"` → parse as `Content` objects
  - Jackson automatically deserializes to correct subclass using Content's `type` field
- Event metadata → Message metadata map (string values)
- Reconstruct `UserMessage` with collected content

---

### AssistantMessage
**Storage:** One event with multiple payloads

**Mapping:**
```
AssistantMessage {
  content: [
    TextContent("Here's the result"),
    ObjectContent(...)
  ],
  toolCalls: [
    ToolCall(id="1", name="search", arguments={...})
  ],
  metadata: {
    "modelName": "gpt-4",
    "temperature": "0.7"
  }
}

→ AgentCore Event with payloads:
  [
    Conversational { role: ASSISTANT, content: { text: "Here's the result" } },
    Blob { 
      "blobType": "camunda.messageContent",
      "version": 1,
      "content": {
        "type": "object",
        "object": { ... },
        "metadata": { ... }
      }
    },
    Blob { 
      "blobType": "camunda.toolCalls",
      "version": 1,
      "toolCalls": [
        { "id": "1", "name": "search", "arguments": {...} }
      ]
    }
  ]
  metadata: {
    "modelName": "gpt-4",
    "temperature": "0.7"
  }
```

**Rules:**
- Each `TextContent` → separate Conversational ASSISTANT payload
- Each non-Text `Content` → Blob envelope with `blobType: "camunda.messageContent"`
- If `toolCalls` is non-empty → Blob envelope with `blobType: "camunda.toolCalls"`
- `metadata` is stored in Event's metadata field
- **Edge case**: If AssistantMessage has only toolCalls and no text content, only the blob payload is created

**Deserialization:**
- Conversational ASSISTANT payloads → collect text into `TextContent` list
- Blob payloads:
  - If `blobType == "camunda.toolCalls"` → parse as `ToolCall[]`
  - If `blobType == "camunda.messageContent"` → parse as `Content` object
- Event metadata → Message metadata map
- If event has only blob payloads with `blobType: "camunda.toolCalls"`, infer it's an AssistantMessage
- Reconstruct `AssistantMessage` with content + toolCalls + metadata

---

### ToolCallResultMessage
**Storage:** One event with two payloads (conversational + blob)

**Mapping:**
```
ToolCallResultMessage {
  results: [
    ToolCallResult(id="1", name="search", content="Found 3 items", properties={...}),
    ToolCallResult(id="2", name="fetch", content="Data retrieved", properties={...})
  ],
  metadata: {
    "toolName": "search",
    "executionTime": "150ms"
  }
}

→ AgentCore Event with payloads:
  [
    Conversational { 
      role: TOOL, 
      content: { text: "Found 3 items\nData retrieved" } 
    },
    Blob {
      "blobType": "camunda.toolCallResults",
      "version": 1,
      "results": [
        { "id": "1", "name": "search", "content": "Found 3 items", "properties": {...} },
        { "id": "2", "name": "fetch", "content": "Data retrieved", "properties": {...} }
      ]
    }
  ]
  metadata: {
    "toolName": "search",
    "executionTime": "150ms"
  }
```

**Rules:**
- Conversational TOOL payload contains concatenated natural-language content (newline-separated) from all results
  - This enables AWS long-term memory to extract meaningful tool result information
- Blob payload contains full structured `ToolCallResult[]` array in typed envelope
  - Enables exact reconstruction with `id`, `name`, `properties`, etc.
- `metadata` is stored in Event's metadata field

**Deserialization:**
- If Blob with `blobType == "camunda.toolCallResults"` exists → parse and use full structure
- Else fallback to Conversational TOOL text → create minimal `ToolCallResult` with just content
- Event metadata → Message metadata map

---

## Blob Envelope Format

For ALL structured data (non-conversational text), we use a versioned envelope with `blobType` as the discriminator:

```json
{
  "blobType": "camunda.<typeIdentifier>",
  "version": 1,
  "<dataKey>": { ... }
}
```

**Supported blobType values:**
- `camunda.toolCalls` - envelope for `ToolCall[]` array (data key: `toolCalls`)
- `camunda.toolCallResults` - envelope for `ToolCallResult[]` array (data key: `results`)
- `camunda.messageContent` - envelope for `Content` objects (data key: `content`)

**Design rationale:**
- **Unified approach**: All non-text structured data uses the same envelope pattern
- **Type discrimination**: `blobType` is checked first during deserialization
- **Version evolution**: Each blob type can evolve independently
- **Nested discriminators**: Content objects preserve their native `type` field inside the envelope

**Example with nested discriminators:**
```json
{
  "blobType": "camunda.messageContent",
  "version": 1,
  "content": {
    "type": "document",        ← Content's native discriminator
    "document": { "id": "..." },
    "metadata": {}
  }
}
```

**Deserialization strategy:**
```java
// 1. Parse blob as BlobEnvelope
BlobEnvelope envelope = BlobEnvelope.fromDocument(blob, objectMapper);

// 2. Check blobType
if (envelope.is(BlobEnvelopeType.TOOL_CALLS)) {
  return envelope.parseData(new TypeReference<List<ToolCall>>() {}, objectMapper);
} else if (envelope.is(BlobEnvelopeType.MESSAGE_CONTENT)) {
  // Jackson uses Content's @JsonTypeInfo to deserialize to correct subclass
  return envelope.parseData(Content.class, objectMapper);
}
```

**Future extensibility:**
- Increment `version` if structure changes
- Add new `blobType` values for new structured data types

---

## Metadata Handling

All Message types support a `metadata` field (Map<String, Object>) that is stored in AWS AgentCore Event's metadata field.

### Storage
```java
// Message with metadata
UserMessage message = UserMessage.builder()
    .content(List.of(textContent("Hello")))
    .metadata(Map.of(
        "userId", "user123",
        "sessionId", "session456",
        "timestamp", "2024-01-15"
    ))
    .build();

// Converted to AWS AgentCore Event
Event event = Event.builder()
    .payload(payloads)
    .metadata(Map.of(
        "userId", MetadataValue.fromStringValue("user123"),
        "sessionId", MetadataValue.fromStringValue("session456"),
        "timestamp", MetadataValue.fromStringValue("2024-01-15")
    ))
    .build();
```

### Conversion Rules
- **AWS AgentCore constraint**: Event metadata only supports string values (MetadataValue wraps strings)
- **Mapper behavior**:
  - String values → stored as-is
  - Non-string values → JSON serialized to string
  - Null values → skipped (not stored)
- **Round-trip**: Values are returned as strings (complex objects remain JSON strings)

### Use Cases
Metadata enables:
- **User tracking**: Store user IDs, session IDs
- **Model parameters**: Track model name, temperature, max tokens
- **Execution metrics**: Store execution time, token counts
- **Custom tags**: Add application-specific metadata

### Example Round-Trip
```java
// Original
Map<String, Object> metadata = Map.of(
    "userId", "user123",
    "model", "gpt-4",
    "tokens", 150,
    "config", Map.of("temp", 0.7)
);

// After round-trip
Map<String, Object> reconstructed = Map.of(
    "userId", "user123",      // string preserved
    "model", "gpt-4",          // string preserved
    "tokens", "150",           // converted to string
    "config", "{\"temp\":0.7}" // JSON serialized
);
```

---

## ObjectMapper Configuration

**MUST USE:** `io.camunda.connector.jackson.ConnectorsObjectMapperSupplier.getCopy()`

**DO NOT USE:** `new ObjectMapper()`

The configured mapper includes:
- Java 8 date/time support
- Polymorphic type handling for `Content` subclasses
- Consistent serialization settings across the connector ecosystem

---

## Backward Compatibility

The deserializer handles legacy formats for smooth migration:

1. **Old assistant toolCalls format**: Blob containing raw JSON array `[{...}]` without envelope
   - Detected by attempting to parse blob as `ToolCall[]` directly
   - Automatically upgraded to internal representation
   
2. **Old tool results format**: Conversational TOOL with plain text, no blob
   - Creates minimal `ToolCallResult` with text as content
   - Loses structural information but maintains conversation flow

3. **Plain conversational messages**: No blob payloads
   - Maps directly to messages with only text content

---

## Trade-offs & Limitations

### What IS preserved (1:1 round-trip):
✅ Message role/type  
✅ Text content  
✅ Non-text Content objects (documents, blobs, objects, resources)  
✅ Assistant toolCalls (via typed blob)  
✅ ToolCallResult structure (id, name, content via typed blob)  

### What is NOT preserved (lossy):
❌ `Message.metadata` value types - metadata is stored and round-trips, but all values are converted to strings  
❌ `UserMessage.name` field - not stored  
❌ `ToolCallResult.properties` map - round-trip behavior depends on @JsonAnySetter/@JsonAnyGetter through nested JSON
❌ Exact ordering of text vs non-text content within a single message (text always comes first on reconstruction)  

### Why these trade-offs:
- AgentCore Memory is optimized for **conversational text extraction**, not as a general-purpose object store
- Storing metadata/name would require encoding into conversational text (pollutes long-term memory) or blob (can't query/index)
- For use cases requiring full message fidelity, consider using Camunda Document Store instead

---

## Testing Strategy

Each mapping has comprehensive unit tests:

1. **Round-trip tests**: `Message → Event → Message` equality
2. **Content type tests**: Each `Content` subclass round-trips correctly
3. **Edge cases**: Empty lists, null fields, text-only vs mixed content
4. **Backward compatibility**: Legacy formats still deserialize
5. **Blob envelope versioning**: Future versions handled gracefully
6. **Blob-only events**: AssistantMessage with toolCalls but no text

Test classes:
- `BlobEnvelopeTest.java` - 13 tests for envelope serialization/deserialization
- `AwsAgentCoreConversationMapperTest.java` - 20 tests for full message mapping

---

## Usage Example

```java
// Create mapper
var mapper = new AwsAgentCoreConversationMapper(
    ConnectorsObjectMapperSupplier.getCopy()
);

// Message to Event payloads
AssistantMessage message = AssistantMessage.builder()
    .content(List.of(TextContent.textContent("Hello")))
    .toolCalls(List.of(ToolCall.builder()
        .id("call-1")
        .name("search")
        .arguments(Map.of("query", "test"))
        .build()))
    .build();

List<PayloadType> payloads = mapper.toPayloads(message);
// Returns: [Conversational(ASSISTANT, "Hello"), Blob(toolCalls envelope)]

// Event to Messages
Event event = Event.builder()
    .payload(payloads)
    .build();

List<Message> messages = mapper.fromEvent(event);
// Returns: [AssistantMessage with text + toolCalls restored]
```

---

## Error Handling

The mapper uses strict error handling:

- **Serialization failures**: Throws `AgentCoreMapperException` with details
- **Unknown message types**: Throws `IllegalArgumentException`
- **Malformed blobs**: Throws `AgentCoreMapperException` with parsing details
- **No silent failures**: All errors are propagated to caller

This ensures data integrity and makes debugging easier during development.

