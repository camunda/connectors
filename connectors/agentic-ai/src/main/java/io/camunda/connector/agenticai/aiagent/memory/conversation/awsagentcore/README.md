# AWS AgentCore Memory Conversation Storage

## Overview

This package implements conversation storage using AWS Bedrock AgentCore Memory service. AgentCore Memory is designed as an **event log optimized for conversational AI**, with these characteristics:

- **Short-term memory**: Stores raw events (messages) for session replay
- **Long-term memory**: Asynchronously extracts insights from conversational payloads only
- **Event model**: Immutable events scoped by `memoryId`, `actorId`, `sessionId`
- **Payload types**: 
  - `Conversational` (role + text) - flows into long-term memory
  - `Blob` (arbitrary data) - does NOT flow into long-term memory

## Message Mapping

For detailed information about how Camunda messages are mapped to/from AWS AgentCore events, including:
- Message type mappings (UserMessage, AssistantMessage, ToolCallResultMessage)
- Blob envelope format and versioning
- Round-trip preservation guarantees
- Trade-offs and limitations

See: **[mapping/README.md](mapping/README.md)**

## Implementation Structure

```
awsagentcore/
├── README.md (this file)
├── AwsAgentCoreConversationSession.java (session lifecycle)
├── AwsAgentCoreConversationStore.java (store factory)
├── AwsAgentCoreConversationContext.java (conversation state)
├── mapping/
│   ├── README.md (mapping documentation)
│   ├── AwsAgentCoreConversationMapper.java (bidirectional mapper)
│   ├── BlobEnvelope.java (typed envelope wrapper)
│   └── BlobEnvelopeType.java (envelope type enum)
```

**Separation of concerns:**
- **Store**: Factory for creating conversation sessions
- **Session**: Orchestrates load/store operations, manages state tracking
- **Context**: Persists conversation metadata and system messages
- **Mapper**: Handles bidirectional Message ↔ AWS Event conversion
- **Envelope**: Type-safe wrapper for structured blob payloads

---

## Components

### AwsAgentCoreConversationStore
Main conversation store implementation that integrates with the Camunda connector framework. Provides factory methods for creating conversation sessions with AWS AgentCore backend.

**Key responsibilities:**
- Creates and manages `BedrockAgentCoreClient` instances
- Provides session factory with proper authentication
- Integrates with Camunda's memory configuration system

### AwsAgentCoreConversationSession
Handles the session lifecycle for loading and storing messages to/from AgentCore Memory.

**Key responsibilities:**
- **Load phase**: Retrieves events from AgentCore, reconstructs messages using the mapper
- **Store phase**: Converts new messages to events, stores incrementally
- **State tracking**: Manages `storedMessageCount` to avoid duplicate writes
- **System message handling**: Preserves system messages in conversation context (not in AgentCore)

**Session lifecycle:**
```java
1. loadIntoRuntimeMemory(agentContext, memory)
   - Load previous conversation context
   - Restore system message from context
   - Load events from AgentCore → reconstruct messages
   - Add messages to runtime memory

2. [Agent execution happens here]

3. storeFromRuntimeMemory(agentContext, memory)
   - Extract system message from memory
   - Identify new messages (not yet stored)
   - Convert messages to events using mapper
   - Store events to AgentCore
   - Update conversation context with new counts
```

### AwsAgentCoreConversationContext
Stores the conversation state including:
- `conversationId`: Unique identifier for the conversation (used as sessionId in AgentCore)
- `memoryId`: ID of the AgentCore Memory resource
- `actorId`: Identifier of the actor (user/agent) associated with the conversation
- `sessionId`: Session ID in AgentCore (typically same as conversationId)
- `storedMessageCount`: Number of messages already stored (for incremental writes)
- `systemMessage`: The system message (not stored in AgentCore, preserved in context)

**Why separate context:**
- System messages need persistence but can't be stored in AgentCore
- State tracking (message counts) enables incremental writes
- Maintains compatibility with other conversation store implementations

### DefaultBedrockAgentCoreClientFactory
Factory for creating AWS Bedrock AgentCore clients with support for:
- Static credentials (access key + secret key)
- Default credentials chain (for hybrid/self-managed deployments)
- Custom region configuration
- Endpoint overrides (useful for testing)

## Features

### Incremental Message Storage
Only new messages are stored to AgentCore Memory, avoiding duplicate writes by tracking the `storedMessageCount`.

**How it works:**
1. On first store: All storable messages written, count saved in context
2. On subsequent stores: Only messages beyond the saved count are written
3. Result: Efficient updates, no redundant API calls

### Message Type Support
Supports conversion between Camunda message types and AgentCore conversational payloads:
- `UserMessage` → Role.USER
- `AssistantMessage` → Role.ASSISTANT  
- `ToolCallResultMessage` → Role.TOOL
- `SystemMessage` → Preserved in context (not in AgentCore)

### System Message Handling

**Important**: System messages are handled differently because AWS AgentCore Memory doesn't support the SYSTEM role:

1. **On Store**: The system message is extracted from runtime memory and saved in the `AwsAgentCoreConversationContext.systemMessage` field (single message, not a list)
2. **On Load**: The system message is restored from the conversation context **before** loading messages from AgentCore Memory
3. **Result**: The system message persists across iterations just like in other conversation stores (InProcess, CamundaDocument)

This ensures system prompts are maintained throughout the conversation while respecting AgentCore's limitations. Only the most recent system message is stored (typically there's only one system message per conversation).

### storedMessageCount Behavior

The `storedMessageCount` field only tracks messages that are actually stored in AgentCore Memory (USER, ASSISTANT, TOOL). System messages are **not** included in this count because they're stored separately in the conversation context, not in AgentCore.

### Error Handling
- Gracefully handles missing memory on load (starts fresh conversation)
- Throws `IllegalStateException` if write operations fail (fail-fast approach)
- Validates configuration at runtime
- All mapping errors propagated with context (via `AgentCoreMapperException`)

## Configuration

Add to your element template in the Memory Storage section:

```json
{
  "type": "aws-agentcore",
  "memoryId": "your-memory-resource-id",
  "actorId": "user-123",
  "region": "us-east-1",  // optional
  "endpointOverride": null,  // optional
  "authentication": {
    "type": "credentials",
    "accessKey": "secrets.AWS_ACCESS_KEY",
    "secretKey": "secrets.AWS_SECRET_KEY"
  }
}
```

### Configuration Parameters

- **memoryId** (required): ID of the pre-provisioned AgentCore Memory resource
- **actorId** (required): Identifier of the actor (supports FEEL expressions)
- **region** (optional): AWS region where the memory resource is located
- **endpointOverride** (optional): Custom endpoint URI for testing
- **authentication** (required): AWS authentication configuration
  - **type**: Either `"credentials"` or `"defaultCredentialsChain"`
  - **accessKey** (for credentials type): AWS IAM access key
  - **secretKey** (for credentials type): AWS IAM secret key

### Authentication Types

#### Static Credentials
Use explicit access key and secret key:
```json
{
  "authentication": {
    "type": "credentials",
    "accessKey": "AKIAIOSFODNN7EXAMPLE",
    "secretKey": "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
  }
}
```

#### Default Credentials Chain (Hybrid/Self-Managed Only)
Use AWS SDK default credentials provider chain:
```json
{
  "authentication": {
    "type": "defaultCredentialsChain"
  }
}
```

The default credentials chain looks for credentials in this order:
1. Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
2. System properties
3. Default credential profiles file (~/.aws/credentials)
4. Amazon ECS container credentials
5. Instance profile credentials (EC2)

## Features

### Incremental Message Storage
Only new messages are stored to AgentCore Memory, avoiding duplicate writes by tracking the `storedMessageCount`.

### Message Type Support
Supports conversion between Camunda message types and AgentCore conversational payloads:
- `UserMessage` → Role.USER
- `AssistantMessage` → Role.ASSISTANT
- `ToolCallResultMessage` → Role.TOOL

### Error Handling
- Gracefully handles missing memory on load (starts fresh conversation)
- Throws `IllegalStateException` if write operations fail
- Validates configuration at runtime

## Usage Example

```java
// Configuration in process variable
{
  "memory": {
    "storage": {
      "type": "aws-agentcore",
      "memoryId": "mem-abc123",
      "actorId": "=userId",  // FEEL expression
      "region": "us-east-1",
      "authentication": {
        "type": "credentials",
        "accessKey": "secrets.AWS_ACCESS_KEY",
        "secretKey": "secrets.AWS_SECRET_KEY"
      }
    },
    "contextWindowSize": 20
  }
}
```

## AWS Permissions Required

The AWS credentials used must have the following permissions:
- `bedrock-agentcore:CreateEvent`
- `bedrock-agentcore:ListEvents`

## Testing

Tests use Mockito to mock the AWS SDK BedrockAgentCoreClient:
- `AwsAgentCoreConversationStoreTest`: Integration tests for store and session functionality
- `AwsAgentCoreConversationMapperTest`: Unit tests for message mapping (see mapping/README.md)
- `BlobEnvelopeTest`: Unit tests for blob envelope serialization

## Integration

The store is automatically registered in `AgenticAiConnectorsAutoConfiguration`:

```java
@Bean
@ConditionalOnMissingBean
public AwsAgentCoreConversationStore aiAgentAwsAgentCoreConversationStore() {
  return new AwsAgentCoreConversationStore(new DefaultBedrockAgentCoreClientFactory());
}
```

## Limitations

- **System messages not stored in AgentCore**: System prompts are preserved in conversation context only
- **Pre-provisioned memory**: Memory resources must be pre-provisioned (not created by connector)
- **Long-term memory extraction**: Managed by AWS AgentCore (not controlled by this implementation)
- **Message metadata lossy**: See mapping/README.md for details on what fields are preserved vs. lost

## Future Enhancements

Potential improvements:
- Batch write optimization for large conversations
- Memory resource provisioning support
- Branch name support for conversation forking

