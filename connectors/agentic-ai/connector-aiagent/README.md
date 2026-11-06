# connector-aiagent

<!-- README.md is generated from README.peb in the Maven process-classes phase. Edit README.peb. -->

## Purpose

The AI Agent connectors: the AI Agent Task on a service task and the AI Agent Sub-process on an ad-hoc
sub-process, both in v1 and v2. The module holds the agent orchestration, the chat model providers
(native and LangChain4j), the conversation stores (in-process, Camunda document and AWS AgentCore) and
the agent instance client.

## Key types

- `AgentTaskV1Function` and `AgentTaskV2Function`: the AI Agent Task connectors.
- `AgentSubProcessV1Function` and `AgentSubProcessV2Function`: the AI Agent Sub-process connectors.
- `BaseAgentRequestHandler`: the orchestrator that both flavors share.
- `AgentInitializerImpl`: the agent state machine and the gateway tool discovery.
- `AgentConversation`: the immutable turn aggregate of one invocation.
- `ChatModelRegistryImpl`: selects the `ChatModelFactory` for a chat model configuration. The providers
  are in the packages below `aiagent.chatmodel.provider`.
- `ConversationStoreRegistry`: selects the conversation store. The built-in stores are
  `InProcessConversationStore`, `CamundaDocumentConversationStore` and `AwsAgentCoreConversationStore`.
- `CamundaAgentInstanceClient`: reports status, metrics and history to the agent instance API.
- `AgenticAiConnectorsAutoConfiguration`: the Spring configuration.

## Docs

- [Two flavors: AI Agent Task and AI Agent Sub-process](docs/flavors.md)
- [Agent state machine and initialization](docs/agent-state-machine.md)
- [Conversation memory](docs/conversation-memory.md)
- [AWS AgentCore memory storage](docs/aws-agentcore-memory.md)
- [Chat model implementation](docs/chat-model.md)
- [Native providers](docs/native-providers.md)
- [System prompt composition](docs/system-prompt.md)
- [Response handling](docs/response-handling.md)
- [Error codes](docs/error-codes.md)
- [Process instance migration](docs/process-instance-migration.md)
- [Agent instance integration](docs/agent-instance.md)
- [Key code paths](docs/code-paths.md)
- [Agentic loop](../docs/architecture/agentic-loop.md)
- [Extension points](../docs/architecture/extension-points.md)
- [Spring auto-configuration](../docs/architecture/spring-auto-configuration.md)
- [Element templates](element-templates/README.md)

## Examples Directory Reference

The `examples/` directory contains reference BPMN processes and configurations. When making code changes that affect
connector behavior, element template properties, or data model shapes, update the relevant examples to stay in sync.

### AI Agent Examples

- **`ai-agent/ad-hoc-sub-process/`**: Recommended approach — AI Agent as job worker on AHSP
  - Shows tool elements inside AHSP, event handling, and the implicit feedback loop
- **`ai-agent/service-task/`**: AI Agent as connector on service task
  - Shows explicit BPMN loop with gateway checking for tool calls
  - Multi-instance AHSP for parallel tool execution

## AI Agent Task
![AI Agent Task connector icon](data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMzIiIGhlaWdodD0iMzIiIHZpZXdCb3g9IjAgMCAzMiAzMiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPGNpcmNsZSBjeD0iMTYiIGN5PSIxNiIgcj0iMTYiIGZpbGw9IiNBNTZFRkYiLz4KPG1hc2sgaWQ9InBhdGgtMi1vdXRzaWRlLTFfMTg1XzYiIG1hc2tVbml0cz0idXNlclNwYWNlT25Vc2UiIHg9IjQiIHk9IjQiIHdpZHRoPSIyNCIgaGVpZ2h0PSIyNCIgZmlsbD0iYmxhY2siPgo8cmVjdCBmaWxsPSJ3aGl0ZSIgeD0iNCIgeT0iNCIgd2lkdGg9IjI0IiBoZWlnaHQ9IjI0Ii8+CjxwYXRoIGZpbGwtcnVsZT0iZXZlbm9kZCIgY2xpcC1ydWxlPSJldmVub2RkIiBkPSJNMjAuMDEwNSAxMi4wOTg3QzE4LjQ5IDEwLjU4OTQgMTcuMTU5NCA4LjEwODE0IDE2LjE3OTkgNi4wMTEwM0MxNi4xNTIgNi4wMDQ1MSAxNi4xMTc2IDYgMTYuMDc5NCA2QzE2LjA0MTEgNiAxNi4wMDY2IDYuMDA0NTEgMTUuOTc4OCA2LjAxMTA0QzE0Ljk5OTQgOC4xMDgxNCAxMy42Njk3IDEwLjU4ODkgMTIuMTQ4MSAxMi4wOTgxQzEwLjYyNjkgMTMuNjA3MSA4LjEyNTY4IDE0LjkyNjQgNi4wMTE1NyAxNS44OTgxQzYuMDA0NzQgMTUuOTI2MSA2IDE1Ljk2MTEgNiAxNkM2IDE2LjAzODcgNi4wMDQ2OCAxNi4wNzM2IDYuMDExNDQgMTYuMTAxNEM4LjEyNTE5IDE3LjA3MjkgMTAuNjI2MiAxOC4zOTE5IDEyLjE0NzcgMTkuOTAxNkMxMy42Njk3IDIxLjQxMDcgMTQuOTk5NiAyMy44OTIgMTUuOTc5MSAyNS45ODlDMTYuMDA2OCAyNS45OTU2IDE2LjA0MTEgMjYgMTYuMDc5MyAyNkMxNi4xMTc1IDI2IDE2LjE1MTkgMjUuOTk1NCAxNi4xNzk2IDI1Ljk4OUMxNy4xNTkxIDIzLjg5MiAxOC40ODg4IDIxLjQxMSAyMC4wMDk5IDE5LjkwMjFNMjAuMDA5OSAxOS45MDIxQzIxLjUyNTMgMTguMzk4NyAyMy45NDY1IDE3LjA2NjkgMjUuOTkxNSAxNi4wODI0QzI1Ljk5NjUgMTYuMDU5MyAyNiAxNi4wMzEgMjYgMTUuOTk5N0MyNiAxNS45Njg0IDI1Ljk5NjUgMTUuOTQwMyAyNS45OTE1IDE1LjkxNzFDMjMuOTQ3NCAxNC45MzI3IDIxLjUyNTkgMTMuNjAxIDIwLjAxMDUgMTIuMDk4NyIvPgo8L21hc2s+CjxwYXRoIGZpbGwtcnVsZT0iZXZlbm9kZCIgY2xpcC1ydWxlPSJldmVub2RkIiBkPSJNMjAuMDEwNSAxMi4wOTg3QzE4LjQ5IDEwLjU4OTQgMTcuMTU5NCA4LjEwODE0IDE2LjE3OTkgNi4wMTEwM0MxNi4xNTIgNi4wMDQ1MSAxNi4xMTc2IDYgMTYuMDc5NCA2QzE2LjA0MTEgNiAxNi4wMDY2IDYuMDA0NTEgMTUuOTc4OCA2LjAxMTA0QzE0Ljk5OTQgOC4xMDgxNCAxMy42Njk3IDEwLjU4ODkgMTIuMTQ4MSAxMi4wOTgxQzEwLjYyNjkgMTMuNjA3MSA4LjEyNTY4IDE0LjkyNjQgNi4wMTE1NyAxNS44OTgxQzYuMDA0NzQgMTUuOTI2MSA2IDE1Ljk2MTEgNiAxNkM2IDE2LjAzODcgNi4wMDQ2OCAxNi4wNzM2IDYuMDExNDQgMTYuMTAxNEM4LjEyNTE5IDE3LjA3MjkgMTAuNjI2MiAxOC4zOTE5IDEyLjE0NzcgMTkuOTAxNkMxMy42Njk3IDIxLjQxMDcgMTQuOTk5NiAyMy44OTIgMTUuOTc5MSAyNS45ODlDMTYuMDA2OCAyNS45OTU2IDE2LjA0MTEgMjYgMTYuMDc5MyAyNkMxNi4xMTc1IDI2IDE2LjE1MTkgMjUuOTk1NCAxNi4xNzk2IDI1Ljk4OUMxNy4xNTkxIDIzLjg5MiAxOC40ODg4IDIxLjQxMSAyMC4wMDk5IDE5LjkwMjFNMjAuMDA5OSAxOS45MDIxQzIxLjUyNTMgMTguMzk4NyAyMy45NDY1IDE3LjA2NjkgMjUuOTkxNSAxNi4wODI0QzI1Ljk5NjUgMTYuMDU5MyAyNiAxNi4wMzEgMjYgMTUuOTk5N0MyNiAxNS45Njg0IDI1Ljk5NjUgMTUuOTQwMyAyNS45OTE1IDE1LjkxNzFDMjMuOTQ3NCAxNC45MzI3IDIxLjUyNTkgMTMuNjAxIDIwLjAxMDUgMTIuMDk4NyIgZmlsbD0id2hpdGUiLz4KPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0yMC4wMTA1IDEyLjA5ODdDMTguNDkgMTAuNTg5NCAxNy4xNTk0IDguMTA4MTQgMTYuMTc5OSA2LjAxMTAzQzE2LjE1MiA2LjAwNDUxIDE2LjExNzYgNiAxNi4wNzk0IDZDMTYuMDQxMSA2IDE2LjAwNjYgNi4wMDQ1MSAxNS45Nzg4IDYuMDExMDRDMTQuOTk5NCA4LjEwODE0IDEzLjY2OTcgMTAuNTg4OSAxMi4xNDgxIDEyLjA5ODFDMTAuNjI2OSAxMy42MDcxIDguMTI1NjggMTQuOTI2NCA2LjAxMTU3IDE1Ljg5ODFDNi4wMDQ3NCAxNS45MjYxIDYgMTUuOTYxMSA2IDE2QzYgMTYuMDM4NyA2LjAwNDY4IDE2LjA3MzYgNi4wMTE0NCAxNi4xMDE0QzguMTI1MTkgMTcuMDcyOSAxMC42MjYyIDE4LjM5MTkgMTIuMTQ3NyAxOS45MDE2QzEzLjY2OTcgMjEuNDEwNyAxNC45OTk2IDIzLjg5MiAxNS45NzkxIDI1Ljk4OUMxNi4wMDY4IDI1Ljk5NTYgMTYuMDQxMSAyNiAxNi4wNzkzIDI2QzE2LjExNzUgMjYgMTYuMTUxOSAyNS45OTU0IDE2LjE3OTYgMjUuOTg5QzE3LjE1OTEgMjMuODkyIDE4LjQ4ODggMjEuNDExIDIwLjAwOTkgMTkuOTAyMU0yMC4wMDk5IDE5LjkwMjFDMjEuNTI1MyAxOC4zOTg3IDIzLjk0NjUgMTcuMDY2OSAyNS45OTE1IDE2LjA4MjRDMjUuOTk2NSAxNi4wNTkzIDI2IDE2LjAzMSAyNiAxNS45OTk3QzI2IDE1Ljk2ODQgMjUuOTk2NSAxNS45NDAzIDI1Ljk5MTUgMTUuOTE3MUMyMy45NDc0IDE0LjkzMjcgMjEuNTI1OSAxMy42MDEgMjAuMDEwNSAxMi4wOTg3IiBzdHJva2U9IiM0OTFEOEIiIHN0cm9rZS13aWR0aD0iNCIgc3Ryb2tlLWxpbmVqb2luPSJyb3VuZCIgbWFzaz0idXJsKCNwYXRoLTItb3V0c2lkZS0xXzE4NV82KSIvPgo8L3N2Zz4K)

Execute a single AI-powered action with tool calling capabilities
Find the user documentation in our [Camunda Documentation](https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent/).

### Build

```bash
mvn clean package
```

### API

#### Result

The following json structure will be returned by the Connector and can be used in the result expression. Depending on the
configuration, the result will contain the following fields:

##### Text response

```json
{
  "context" : {
    "metrics" : {
      "modelCalls" : 3,
      "tokenUsage" : {
        "inputTokenCount" : 10,
        "outputTokenCount" : 20
      },
      "toolCalls" : 0
    },
    "schemaVersion" : 1,
    "state" : "READY",
    "toolDefinitions" : [ {
      "description" : "A sample tool for demonstration purposes.",
      "inputSchema" : {
        "properties" : {
          "input1" : {
            "type" : "string"
          },
          "input2" : {
            "type" : "number"
          }
        },
        "required" : [ "input1", "input2" ],
        "type" : "object"
      },
      "name" : "sampleTool"
    } ]
  },
  "responseText" : "This is a sample response text from the AI agent.",
  "toolCalls" : [ ]
}
```

The response text can be accessed via FEEL:
```json
={ responseText: responseText }
```
leading to the following result
```json
{
  "responseText" : "This is a sample response text from the AI agent."
}
```

##### JSON response

```json
{
  "context" : {
    "metrics" : {
      "modelCalls" : 3,
      "tokenUsage" : {
        "inputTokenCount" : 10,
        "outputTokenCount" : 20
      },
      "toolCalls" : 0
    },
    "schemaVersion" : 1,
    "state" : "READY",
    "toolDefinitions" : [ {
      "description" : "A sample tool for demonstration purposes.",
      "inputSchema" : {
        "properties" : {
          "input1" : {
            "type" : "string"
          },
          "input2" : {
            "type" : "number"
          }
        },
        "required" : [ "input1", "input2" ],
        "type" : "object"
      },
      "name" : "sampleTool"
    } ]
  },
  "responseJson" : {
    "firstname" : "John",
    "lastname" : "Doe",
    "userId" : 5
  },
  "toolCalls" : [ ]
}
```

The JSON body can be accessed via FEEL:
```json
=responseJson.firstname
```
leading to the following result
```json
"John"
```

##### Response containing the full assistant message

```json
{
  "context" : {
    "metrics" : {
      "modelCalls" : 3,
      "tokenUsage" : {
        "inputTokenCount" : 10,
        "outputTokenCount" : 20
      },
      "toolCalls" : 0
    },
    "schemaVersion" : 1,
    "state" : "READY",
    "toolDefinitions" : [ {
      "description" : "A sample tool for demonstration purposes.",
      "inputSchema" : {
        "properties" : {
          "input1" : {
            "type" : "string"
          },
          "input2" : {
            "type" : "number"
          }
        },
        "required" : [ "input1", "input2" ],
        "type" : "object"
      },
      "name" : "sampleTool"
    } ]
  },
  "responseMessage" : {
    "role" : "assistant",
    "content" : [ {
      "type" : "text",
      "text" : "This is a sample response text from the AI agent."
    } ],
    "id" : "01a00023-829e-723b-8cae-8bd1bb11c25b",
    "metadata" : {
      "framework" : {
        "finishReason" : "STOP",
        "id" : "chatcmpl-123",
        "tokenUsage" : {
          "inputTokenCount" : 5,
          "outputTokenCount" : 6,
          "totalTokenCount" : 11
        }
      }
    }
  },
  "toolCalls" : [ ]
}
```

The response message can be accessed via FEEL:
```json
=responseMessage.content[type = "text"][1].text
```
leading to the following result
```json
"This is a sample response text from the AI agent."
```

##### Response containing tool call requests

```json
{
  "context" : {
    "metrics" : {
      "modelCalls" : 3,
      "tokenUsage" : {
        "inputTokenCount" : 10,
        "outputTokenCount" : 20
      },
      "toolCalls" : 0
    },
    "schemaVersion" : 1,
    "state" : "READY",
    "toolDefinitions" : [ {
      "description" : "A sample tool for demonstration purposes.",
      "inputSchema" : {
        "properties" : {
          "input1" : {
            "type" : "string"
          },
          "input2" : {
            "type" : "number"
          }
        },
        "required" : [ "input1", "input2" ],
        "type" : "object"
      },
      "name" : "sampleTool"
    } ]
  },
  "toolCalls" : [ {
    "_meta" : {
      "id" : "123456",
      "name" : "sampleTool"
    },
    "input1" : "value1",
    "input2" : 42
  } ]
}
```

The list of tool calls can be accessed via FEEL:
```json
=toolCalls
```
leading to the following result
```json
[ {
  "_meta" : {
    "id" : "123456",
    "name" : "sampleTool"
  },
  "input1" : "value1",
  "input2" : 42
} ]
```

| Connector Info            |                                                                       |
| ---                       | ---                                                                   |
| Type                      | io.camunda.agenticai:aiagent:1                                                            |
| Version                   | 13                                                         |
| Supported element types   |     |

