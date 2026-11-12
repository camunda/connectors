# Document parameters

A tool of the process declares a parameter that carries a document with the type `document`. It can be the parameter
itself, the `items` of an array or a property of an object, at any depth:

```feel
fromAi(toolCall.report, "The report to upload.", "document")
```

```feel
fromAi(
  value: toolCall.message,
  description: "The message.",
  schema: {
    "type": "object",
    "properties": {
      "attachments": {"type": "array", "items": {"type": "document"}},
      "caption": {"type": "string"}
    }
  }
)
```

The tool schema generation keeps the type `document` as authored (see
[Tool resolution](../../aiagent-tools-schema/docs/tool-resolution.md)). The agent maps it when it loads the tools of the
process (`AgentToolsResolverImpl.loadAdHocToolsSchema`):

- **With the `documents` feature**, `DocumentParameter.fromDocumentType` replaces each type `document` with the schema
  of a document handle (`DocumentParameter.documentSchema`):

  ```json
  {
    "type": "object",
    "description": "The report to upload.",
    "properties": {
      "type": {"type": "string", "enum": ["camunda.agenticai.document"]},
      "id": {"type": "string", "description": "The id of the document."}
    },
    "required": ["type", "id"]
  }
  ```

  The type `document` anywhere else, such as in an `anyOf` alternative or in a type array (`["string", "document"]`),
  fails the job with the error code `DOCUMENT_PARAMETER_SCHEMA_INVALID` (see [Error codes](error-codes.md)). For a
  parameter that takes text or a document, declare two parameters.
- **Without the feature**, the schema stays as authored, and the agent instance has no document parameters. See
  [ADR 018](../../docs/adr/018-document-flow.md).

The model passes the handle it read, also as a string that holds the object as JSON:

```json
{"report": {"type": "camunda.agenticai.document", "id": "doc_0123456789ab"}}
```

`DocumentParameterResolver.resolve` replaces each handle. A process tool receives the Camunda document reference of the
document, a runtime tool its `AgentDocumentId`. A missing or `null` argument stays as it is. Any other value is invalid: a
bare id, an unknown id, other text, a number. The agent answers a call with an invalid value itself, with one sentence per
invalid value, and the tool does not run:

```text
Parameter 'docs[1]' takes a document, but the conversation has no documents. Do not pass a document.
Parameter 'docs[1]' has the id 'doc_000000000000', but the conversation has no document with this id. Pass the handle of a document from the conversation: a JSON object such as {"type":"camunda.agenticai.document","id":"doc_…"}.
Parameter 'docs[1]' must be the handle of a document from the conversation: a JSON object such as {"type":"camunda.agenticai.document","id":"doc_…"}.
Parameter 'message' must be an object.
Parameter 'message.attachments' must be an array.
```

The agent resolves a call at three places. The tool call of the assistant message is never rewritten, so the stored
conversation and the history keep what the model sent.

- **Admission.** `ToolCallAdmission` rejects a call with an invalid value: the call gets the sentences as its
  `ToolCall.rejection` and is never executed or activated (see
  [What Happens When Tools Complete](../../docs/architecture/agentic-loop.md#what-happens-when-tools-complete)).
- **Process tools.** `AgentResponseHandlerImpl` replaces the handles of the calls that go to the process with
  `DocumentParameterResolver.resolveArguments`. It runs in the job of the admission, so an invalid value there is a
  defect and fails the job.
- **Runtime tools.** `RuntimeTools` replaces the handles before it calls the tool. When the assistant message also has
  calls of process tools, the runtime calls run in a later job, and the definition of a tool can differ there, such as
  after an update of the connector runtime. A call with an invalid value then gets the sentences as an error result,
  and the tool does not run.
