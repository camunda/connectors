# AWS AgentCore Memory Storage

## Overview

This document covers the AWS Bedrock AgentCore Memory conversation store implementation. AgentCore Memory is an **event log optimized for conversational AI** with:

- **Short-term memory**: Stores raw events (messages) for session replay
- **Long-term memory**: Asynchronously extracts insights from conversational payloads
- **Event model**: Immutable events scoped by `memoryId`, `actorId`, `sessionId`
- **Payload types**:
  - `Conversational` (role + text) — flows into long-term memory extraction
  - `Blob` (arbitrary structured data) — does NOT flow into long-term memory

## Architecture

```
AwsAgentCoreConversationStore (factory, creates sessions)
    │
    ├── DefaultBedrockAgentCoreClientFactory (AWS client lifecycle)
    │
    └── AwsAgentCoreConversationSession (load/store lifecycle)
            │
            ├── AwsAgentCoreConversationContext (state in Zeebe variables)
            │
            └── AwsAgentCoreConversationMapper (bidirectional Message ↔ Event)
                    ├── BlobEnvelope (versioned envelope wrapper)
                    └── BlobEnvelopeType (envelope type enum)
```

**Separation of concerns:**
- **Store**: Factory for creating conversation sessions, manages AWS client lifecycle (try-with-resources)
- **Session**: Orchestrates load/store operations with branch-per-turn isolation
- **Context**: Persists conversation metadata (branch pointer, system message) in Zeebe variables
- **Mapper**: Handles bidirectional Message ↔ AWS Event conversion
- **Envelope**: Type-safe wrapper for structured blob payloads

## Branch-Per-Turn Strategy

AgentCore is an append-only external store. Unlike snapshot-based stores (InProcess, CamundaDocument), writing to AgentCore is visible immediately — creating a split-brain risk if job completion fails after storing.

The implementation uses **event branching** to achieve the same isolation property as the Document store's new-document-per-turn approach:

```
Turn 1 (main timeline):
  Write: [user, assistant] → main
  Context: { branchName: null, lastEventId: "evt-2" }

Turn 2:
  Load: ListEvents(branch=null) → [user, assistant]
  Write: [user, assistant] → new branch "abc-123", rooted at "evt-2"
  Context: { branchName: "abc-123", lastEventId: "evt-4" }

Turn 3:
  Load: ListEvents(branch="abc-123", includeParentBranches=true) → [all 4 messages]
  Write: [user, assistant] → new branch "def-456", rooted at "evt-4"
  Context: { branchName: "def-456", lastEventId: "evt-6" }
```

### Failure Recovery

If job completion fails after storing (messages written to AgentCore, but Zeebe has stale context):

```
Turn 3 (FAILS after store):
  Messages written to branch "def-456" in AgentCore
  Job completion fails → Zeebe still has Turn 2's context

Turn 3 (RETRY):
  Load: ListEvents(branch="abc-123") → [4 messages from turns 1+2]  ← clean!
  Write: new branch "ghi-789" from "evt-4"
  Branch "def-456" is orphaned — invisible when loading other branches
```

This mirrors the Document store pattern where orphaned documents are harmless.

### Branch Behavior (Empirically Verified)

- `includeParentBranches=true` traverses the **full chain** (main → branch A → branch B → ...)
- Multiple events can be written to the same branch by including the `branch` field on each `CreateEvent`
- Orphaned branches are invisible when loading other branches
- No documented limit on branches per session
- The ListEvents API does **not** guarantee event ordering; client-side sorting by `eventTimestamp` + turn-local `seq` metadata is required
- Each event carries a `seq` metadata entry (turn-local offset) written via `CreateEvent` metadata, used as a deterministic tiebreaker for events with identical timestamps within a turn

## Message Mapping

### Design Principles

1. **Optimize for long-term memory extraction**: Human-readable text goes into conversational payloads
2. **Enable 1:1 round-trip mapping**: Structured data uses versioned blob envelopes
3. **Preserve content ordering**: Payloads are emitted and read in the same order as the original content list, interleaving conversational and blob payloads as needed
4. **One Message = one Event**: Each internal message maps to one AgentCore event with multiple payloads

### Mapping Table

| Message Type | Conversational Payload | Blob Payload |
|---|---|---|
| `UserMessage` | Each `TextContent` → USER (in order) | Each non-text Content → `camunda.messageContent` blob (in order) |
| `AssistantMessage` | Each `TextContent` → ASSISTANT (in order) | Non-text Content → `camunda.messageContent` blob (in order); ToolCalls → `camunda.toolCalls` blob (appended after content) |
| `ToolCallResultMessage` | Summary text → TOOL (for extraction) | Full structure → `camunda.toolCallResults` blob |
| `SystemMessage` | **Not stored in AgentCore** | Preserved in `AwsAgentCoreConversationContext` |
| *(all types)* | — | Metadata → `camunda.messageMetadata` blob (appended last) |

### Detailed Mapping Examples

**UserMessage with mixed content (order preserved):**
```
UserMessage { content: [TextContent("Hello"), DocumentContent(...), TextContent("more")], metadata: {"userId": "u1"} }

→ Event with payloads (in order):
    Conversational { role: USER, content: { text: "Hello" } }
    Blob { "blobType": "camunda.messageContent", "version": 1, "content": {"type": "document", ...} }
    Conversational { role: USER, content: { text: "more" } }
    Blob { "blobType": "camunda.messageMetadata", "version": 1, "metadata": {"userId": "u1"} }
```
Content items are emitted in their original order — `TextContent` becomes conversational, non-text becomes blob, interleaved as they appear. Metadata is appended as a separate blob envelope. `UserMessage.name` is preserved in the metadata blob envelope's `properties` section.

**AssistantMessage with tool calls:**
```
AssistantMessage { content: [TextContent("Here's the result")], toolCalls: [ToolCall(id="1", name="search", ...)] }

→ Event with payloads:
    Conversational { role: ASSISTANT, content: { text: "Here's the result" } }
    Blob { "blobType": "camunda.toolCalls", "version": 1, "toolCalls": [{...}] }
    Blob { "blobType": "camunda.messageMetadata", "version": 1, "metadata": {...} }
```
Edge case: if AssistantMessage has only toolCalls and no text content, only the toolCalls blob is created (no conversational payload). The deserializer detects this by the `camunda.toolCalls` blob type.

**ToolCallResultMessage:**
```
ToolCallResultMessage { results: [ToolCallResult(id="1", content="Found 3 items"), ToolCallResult(id="2", content="Data retrieved")] }

→ Event with payloads:
    Conversational { role: TOOL, content: { text: "Found 3 items\nData retrieved" } }
    Blob { "blobType": "camunda.toolCallResults", "version": 1, "results": [{...}, {...}] }
    Blob { "blobType": "camunda.messageMetadata", "version": 1, "metadata": {...} }
```
The conversational TOOL payload contains concatenated text for AWS long-term memory extraction. The blob preserves the full structured result array for exact reconstruction. On read, the blob is authoritative; the conversational text is ignored.

### Blob Envelope Format

All structured data uses a versioned envelope with `blobType` as discriminator:

```json
{
  "blobType": "camunda.<typeIdentifier>",
  "version": 1,
  "<dataKey>": { ... }
}
```

Supported types:
- `camunda.toolCalls` (data key: `toolCalls`) — `ToolCall[]` from AssistantMessage
- `camunda.toolCallResults` (data key: `results`) — `ToolCallResult[]` from ToolCallResultMessage
- `camunda.messageContent` (data key: `content`) — Non-text `Content` objects (preserves native `type` discriminator)
- `camunda.messageMetadata` (data key: `metadata`) — Message metadata map (timestamps, framework info, custom properties)

The envelope design provides: unified approach for all non-text data, type discrimination via `blobType` checked first during deserialization, independent version evolution per blob type, and support for nested discriminators (e.g., Content's `@JsonTypeInfo(property="type")` is preserved inside the envelope).

### Metadata Handling

Message metadata is stored as a `camunda.messageMetadata` blob envelope appended to each event's payload list. This preserves the full metadata structure with exact round-trip fidelity, including complex types like `ZonedDateTime` objects and nested maps.

The AWS event-level metadata field is **not used** because it only supports string values matching `[a-zA-Z0-9\s._:/=+@-]*`, which rejects JSON-serialized objects and timestamps.

### System Message Handling

AgentCore Memory doesn't support the SYSTEM role. System messages are:
1. **On store**: Extracted from runtime memory and saved in `AwsAgentCoreConversationContext.systemMessage`
2. **On load**: Restored from context **before** loading events from AgentCore
3. **Result**: System prompt persists across iterations, same as other stores

### Trade-offs

**Preserved (1:1 round-trip):**
- Message role/type, text content, non-text Content objects
- Content ordering within a message (interleaving of text and non-text preserved)
- Message metadata (stored as blob envelope, exact round-trip including complex types)
- Assistant toolCalls (via blob), ToolCallResult structure including properties (via blob)

## Configuration

```json
{
  "type": "aws-agentcore",
  "memoryId": "your-memory-resource-id",
  "actorId": "=userId",
  "region": "us-east-1",
  "endpoint": null,
  "authentication": {
    "type": "credentials",
    "accessKey": "secrets.AWS_ACCESS_KEY",
    "secretKey": "secrets.AWS_SECRET_KEY"
  }
}
```

### Parameters

| Parameter | Required | Description |
|---|---|---|
| `memoryId` | Yes | ID of the pre-provisioned AgentCore Memory resource |
| `actorId` | Yes | Identifier of the actor (supports FEEL expressions) |
| `region` | Yes | AWS region (example: `us-east-1`) |
| `endpoint` | No | Custom endpoint for VPC/PrivateLink, GovCloud, or non-standard deployments |
| `authentication` | Yes | `credentials` (access key + secret key) or `defaultCredentialsChain` |

### Authentication

**Static credentials:**
```json
{ "type": "credentials", "accessKey": "...", "secretKey": "..." }
```

**Default credentials chain** (Hybrid/Self-Managed only):
```json
{ "type": "defaultCredentialsChain" }
```

Uses the AWS SDK default credentials provider chain, which resolves credentials from multiple sources (environment variables, system properties, credential profiles, container credentials, instance profiles). Not available on SaaS.

### AWS Permissions Required

- `bedrock-agentcore:CreateEvent`
- `bedrock-agentcore:ListEvents`

## Error Handling

- **Load failures**: `BedrockAgentCoreException` (AWS SDK errors) and unexpected exceptions are caught, logged with context (sessionId, memoryId, actorId), and re-thrown as `IllegalStateException` — fail-fast to prevent corrupted conversation state
- **Store failures**: Exception on any `CreateEvent` call propagates immediately. With branch-per-turn, partially-written branches are orphaned and invisible on retry
- **Mapping failures**: All serialization/deserialization errors throw `AgentCoreMapperException` with cause chain
- **Missing previous context**: Gracefully starts a new conversation (no error)

## ObjectMapper Requirement

The mapper **must** use the Connectors-configured `ObjectMapper` (injected via `@ConnectorsObjectMapper` in auto-configuration). Do not use `new ObjectMapper()` — the configured mapper includes polymorphic type handling for `Content` subclasses (`@JsonTypeInfo`), Java 8 date/time support, and consistent serialization settings.

## HTTP Proxy Support

The AgentCore client uses the same HTTP proxy configuration as the Bedrock LLM client. Proxy settings are applied via `CONNECTOR_HTTP_PLAIN_PROXY_HOST` / `CONNECTOR_HTTPS_PLAIN_PROXY_HOST` environment variables.

## Validation

- `memoryId` and `actorId` are validated for consistency on load — changing them mid-conversation throws `IllegalStateException` before any API calls
- Configuration type is validated at session creation
- **Default credentials chain is blocked on SaaS** — the same `@AssertFalse` validation as the Bedrock LLM provider prevents using `defaultCredentialsChain` in SaaS environments

## Limitations

- **System messages not stored in AgentCore**: Preserved in conversation context (Zeebe variable)
- **Pre-provisioned memory**: Memory resources must exist before use
- **Long-term memory extraction**: Managed by AWS (not controlled by this implementation)
- **Event ordering**: ListEvents API does not guarantee order; client-side sorting by `eventTimestamp` + turn-local `seq` metadata required
- **Rate limits**: 5 TPS per actor/session for conversational payloads (natural LLM latency provides spacing)
