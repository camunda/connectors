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
  metadata: {...}
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
```

**Rules:**
- Each `TextContent` → separate Conversational USER payload with that text
- Each non-Text `Content` → Blob envelope with `blobType: "camunda.messageContent"`
  - The Content object is wrapped in the envelope with its native `type` discriminator preserved
- `name` and `metadata` fields are not stored (lossy) - only content parts are preserved

**Deserialization:**
- Conversational USER payloads → collect text into `TextContent` list
- Blob payloads with `blobType: "camunda.messageContent"` → parse as `Content` objects
  - Jackson automatically deserializes to correct subclass using Content's `type` field
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
  metadata: {...}
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
```

**Rules:**
- Each `TextContent` → separate Conversational ASSISTANT payload
- Each non-Text `Content` → Blob envelope with `blobType: "camunda.messageContent"`
- If `toolCalls` is non-empty → Blob envelope with `blobType: "camunda.toolCalls"`
- `metadata` field is not stored (lossy)
- **Edge case**: If AssistantMessage has only toolCalls and no text content, only the blob payload is created

**Deserialization:**
- Conversational ASSISTANT payloads → collect text into `TextContent` list
- Blob payloads:
  - If `blobType == "camunda.toolCalls"` → parse as `ToolCall[]`
  - If `blobType == "camunda.messageContent"` → parse as `Content` object
- If event has only blob payloads with `blobType: "camunda.toolCalls"`, infer it's an AssistantMessage
- Reconstruct `AssistantMessage` with content + toolCalls

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
  metadata: {...}
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
```

**Rules:**
- Conversational TOOL payload contains concatenated natural-language content (newline-separated) from all results
  - This enables AWS long-term memory to extract meaningful tool result information
- Blob payload contains full structured `ToolCallResult[]` array in typed envelope
  - Enables exact reconstruction with `id`, `name`, `properties`, etc.

**Deserialization:**
- If Blob with `blobType == "camunda.toolCallResults"` exists → parse and use full structure
- Else fallback to Conversational TOOL text → create minimal `ToolCallResult` with just content

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

## Trade-offs & Limitations

### What IS preserved (1:1 round-trip):
✅ Message role/type  
✅ Text content  
✅ Non-text Content objects (documents, blobs, objects, resources)  
✅ Assistant toolCalls (via typed blob)  
✅ ToolCallResult structure (id, name, content via typed blob)  

### What is NOT preserved (lossy):
❌ `Message.metadata` maps - not stored  
❌ `UserMessage.name` field - not stored  
❌ `ToolCallResult.properties` map - round-trip behavior depends on @JsonAnySetter/@JsonAnyGetter through nested JSON
❌ Exact ordering of text vs non-text content within a single message (text always comes first on reconstruction)