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

### Configuration Parameters

- **memoryId** (required): ID of the pre-provisioned AgentCore Memory resource
- **actorId** (required): Identifier of the actor (supports FEEL expressions)
- **region** (optional): AWS region where the memory resource is located
- **endpointOverride** (optional): Custom endpoint URI for testing
- **authentication** (required): AWS authentication configuration
  - **type**: Either `"credentials"` or `"defaultCredentialsChain"`
  - **accessKey** (for credentials type): AWS IAM access key
  - **secretKey** (for credentials type): AWS IAM secret key

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

## Limitations

- **System messages not stored in AgentCore**: System prompts are preserved in conversation context only
- **Pre-provisioned memory**: Memory resources must be pre-provisioned (not created by connector)
- **Long-term memory extraction**: Managed by AWS AgentCore (not controlled by this implementation)
- **Message metadata lossy**: See mapping/README.md for details on what fields are preserved vs. lost

