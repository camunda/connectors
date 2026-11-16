# Error Codes

## AgentErrorCodes

The codes are defined in
[`AgentErrorCodes`](../../connector-aiagent/src/main/java/io/camunda/connector/agenticai/aiagent/agent/AgentErrorCodes.java)
and thrown as `ConnectorException(errorCode, message)`. To find a code's current throw site, search the
codebase for its constant. `Value` is the externally visible error code (what BPMN error handling keys
on).

| Constant                                                | Value                                          | When                                                                   |
|---------------------------------------------------------|------------------------------------------------|------------------------------------------------------------------------|
| `ERROR_CODE_NO_USER_MESSAGE_CONTENT`                    | `NO_USER_MESSAGE_CONTENT`                      | user messages list is empty (task connector)                            |
| `ERROR_CODE_TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT`         | `TOOL_CALL_RESULTS_ON_EMPTY_CONTEXT`           | tool results arrive but there is no prior conversation                  |
| `ERROR_CODE_MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED`      | `MAXIMUM_NUMBER_OF_MODEL_CALLS_REACHED`        | model calls reached the configured maximum                              |
| `ERROR_CODE_FAILED_TO_PARSE_RESPONSE_CONTENT`           | `FAILED_TO_PARSE_RESPONSE_CONTENT`             | JSON parse failure (explicit JSON response format only)                 |
| `ERROR_CODE_FAILED_MODEL_CALL`                          | `FAILED_MODEL_CALL`                            | the chat model call threw                                               |
| `ERROR_CODE_UNSUPPORTED_MODEL_CONFIGURATION`            | `UNSUPPORTED_MODEL_CONFIGURATION`              | model configuration is invalid for the provider                         |
| `ERROR_CODE_MODEL_RESPONSE_CONTENT_FILTERED`            | `MODEL_RESPONSE_CONTENT_FILTERED`              | provider blocked the model response with content filtering              |
| `ERROR_CODE_MODEL_CONTEXT_WINDOW_EXCEEDED`              | `MODEL_CONTEXT_WINDOW_EXCEEDED`                | model's context window was exceeded before it finished responding       |
| `ERROR_CODE_MODEL_RESPONSE_GUARDRAIL_INTERVENED`        | `MODEL_RESPONSE_GUARDRAIL_INTERVENED`          | provider blocked the model response with a guardrail policy             |
| `ERROR_CODE_MIGRATION_MISSING_TOOLS`                    | `MIGRATION_MISSING_TOOLS`                      | existing tools were removed after a process migration                   |
| `ERROR_CODE_MIGRATION_GATEWAY_TOOL_DEFINITIONS_CHANGED` | `MIGRATION_GATEWAY_TOOL_DEFINITIONS_CHANGED`   | gateway tools were added or removed after a process migration           |
| `ERROR_CODE_DOCUMENT_ID_COLLISION`                      | `DOCUMENT_ID_COLLISION`                        | a document gets the id of a different document the agent already knows or one registered with it (the ids are derived from the store id and the document id, so this needs a hash collision of the 48 bit id) |
| `ERROR_CODE_UNSUPPORTED_DOCUMENT_REFERENCE`             | `UNSUPPORTED_DOCUMENT_REFERENCE`               | an agent instance with the `documents` feature gets a document that is not a Camunda document, an external or an inline document, in the user prompt, a tool call result or an event; the message names the kind of the document and the place. The incident repeats on retry until the process passes a Camunda document |
| `ERROR_CODE_DOCUMENT_PARAMETER_SCHEMA_INVALID`          | `DOCUMENT_PARAMETER_SCHEMA_INVALID`            | a tool of the process of an agent instance with the `documents` feature has the type `document` where no document parameter can sit, such as in an `anyOf` alternative or in a type array; the message names the tool and the parameter |
| `ERROR_CODE_AGENT_INSTANCE_CREATION_FAILED`             | `AGENT_INSTANCE_CREATION_FAILED`               | agent instance creation failed (retries exhausted or non-retryable)     |
| `ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED`               | `AGENT_INSTANCE_UPDATE_FAILED`                 | agent instance update failed (retries exhausted or non-retryable)       |
| `ERROR_CODE_RUNTIME_TOOL_NAME_CONFLICT`                 | `RUNTIME_TOOL_NAME_CONFLICT`                   | a tool of the process of an agent instance with the `runtimeTools` feature has the name of an enabled runtime tool |
| `ERROR_CODE_AGENT_INSTANCE_SUPERSEDED`                  | `AGENT_INSTANCE_SUPERSEDED`                    | a batched agent instance update was rejected because the job activation was superseded (zero retries) |

Additional errors from `CamundaClientProcessDefinitionAdHocToolElementsResolver`:
- `AD_HOC_SUB_PROCESS_NOT_FOUND` — element ID doesn't resolve to an `AdHocSubProcess` in BPMN
- `AD_HOC_TOOL_DEFINITION_INVALID` — FEEL expression on a mapping fails to parse

All errors are thrown as `ConnectorException(errorCode, message)`.

For MCP error codes, see [`mcp-client.md`](../../connector-mcp/docs/mcp-client.md#error-codes).
For A2A error codes, see [`a2a-client.md`](../../connector-a2a/docs/a2a-client.md#error-codes).
