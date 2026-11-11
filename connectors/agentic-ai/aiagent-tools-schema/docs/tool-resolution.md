# Tool Resolution & Ad-Hoc Sub-Process Integration

## Job Worker Flavor

Tools come from the `adHocSubProcessElements` special variable populated by Zeebe:

```json
[
  {
    "elementId": "check_weather",
    "elementName": "Check Weather",
    "documentation": "Retrieves current weather data",
    "properties": { ... },
    "parameters": { ... }  // from fromAi() FEEL function
  }
]
```

The `AdHocToolsSchemaResolver` processes these into:
1. **ToolDefinitions**: Regular tools with name, description, and JSON schema for input parameters
2. **GatewayToolDefinitions**: Special tools (MCP clients, A2A) that need discovery

## Connector (Task) Flavor

The `ProcessDefinitionAdHocToolElementsResolver` fetches the BPMN XML from Camunda's API:
1. `ProcessDefinitionClient` calls `GET /process-definitions/{key}/xml` (with retries for eventual consistency)
2. `CamundaClientProcessDefinitionAdHocToolElementsResolver` parses the XML to find the ad-hoc sub-process by element ID
3. Extracts element metadata similar to the Zeebe-provided `adHocSubProcessElements`
4. Results are cached by `CachingProcessDefinitionAdHocToolElementsResolver` (Caffeine cache, default: max 100 entries, 10min TTL, configurable via `camunda.connector.agenticai.tools.process-definition.cache.*`)

Every step is scoped to the job's physical tenant, which the resolver takes as its first argument.
The definition is read through that tenant's own `CamundaClient` (via `PhysicalTenantClientSelector`)
and the cache is keyed by it alongside the definition key: keys are only unique within a single
orchestration cluster, so a runtime serving several of them would otherwise serve one tenant's tool
elements to another.

## FEEL Parameter Extraction

`CamundaClientProcessDefinitionAdHocToolElementsResolver` extracts tool parameters from BPMN input/output mappings that use FEEL expressions with the `fromAi()` tagging function:

1. For each flow node in the AHSP, reads `ZeebeIoMapping` extension elements
2. For each mapping with a FEEL expression source (starts with `=`), calls `AdHocToolElementParameterExtractor.extractParameters()`
3. `AdHocToolElementParameterExtractorImpl` uses `FeelEngineApi` to parse the expression and `TaggedParameterExtractor` to extract `fromAi()` tagged parameters
4. Each `AdHocToolElementParameter` has: `name`, `description` (nullable), `type` (nullable), `schema` (nullable `Map<String,Object>`), `options` (nullable — contains `required` flag)

## Tool Schema Generation

`AdHocToolSchemaGeneratorImpl` converts extracted parameters into JSON Schema:
- Parameter names are stripped of `toolCall.` prefix and validated (no dots, no `_meta`)
- `schema` map from the parameter is used as base, with `type` and `description` overlaid
- If `type` is unset, defaults to `"string"`
- Required unless `options.required == false`
- Output: `{type: "object", properties: {...}, required: [...]}`

### Document parameters

A tool parameter that carries a document is declared with the type `document`:
`fromAi(toolCall.report, "The report to upload.", "document")`. The generator stores the schema of a document handle
(`DocumentSchemas.document`): an object with the properties `type`, whose one allowed value is `camunda.agenticai.document`, and
`id`, both required, and the description as authored; it sets no `additionalProperties`.
`AuthoredSchemaNormalizer` of the tools schema module normalizes the shorthand type also inside the `items` and
`properties` of an authored schema and in the `anyOf` and `oneOf` alternatives of such a node at any depth, and nowhere else
(`allOf`, `not`, `if`/`then`/`else`, `additionalProperties`, `patternProperties`, `prefixItems`, `$defs` and `$ref` keep the shorthand as
authored). The schema goes to the
model provider as it is, so the stored definition, the configuration fingerprint and the request hold the same wording.
Document parameters are found at the top level, as array `items` and through object
`properties` at any depth (`ToolDefinition#documentParameters()`, the sealed `DocumentParameter` of `Document` and
`Union`). A node is a `Document` when it is an object whose `properties.type.enum` is exactly
`["camunda.agenticai.document"]`; the search stops there, so its `type` and `id` properties are no parameters. A node is a
`Union` when it has `anyOf` or `oneOf` with more than one alternative and a handle schema sits inside its alternatives (see
below); the search stops there too. A handle schema behind `$ref` is not a document parameter.

The model passes the handle it read as the value: `{"type": "camunda.agenticai.document", "id": "doc_0123456789ab"}`. One
rule defines a handle everywhere: an object with the `type` `camunda.agenticai.document`, or a string that is exactly such an
object as JSON (a model or a tool-calling API that stringified the argument), surrounding whitespace ignored. Further fields of
the object, such as `fileName` and `size`, are ignored, and the `type` is required. A string that is the bare id of a document
is no handle. The value of a `Document` parameter must be a handle of a known document. Anything else is invalid: a bare id, an
object without the `type`, an unknown id, other text, a number. A call with an invalid value is answered by the agent itself, with a tool result of one sentence per invalid
value, joined by a space, and the tool does not run. The sentence depends on the value. When the agent knows no documents, it
tells the model to pass none (it wins over the other two):
`Parameter 'docs[1]' takes a document, but the conversation has no documents. Do not pass a document.`
When the value is a handle with an id that is not known, it names the id:
`Parameter 'docs[1]' has the id 'doc_000000000000', but the conversation has no document with this id. Pass the handle of a document from the conversation: a JSON object such as {"type":"camunda.agenticai.document","id":"doc_…"}.`
For any other value it names the expected shape:
`Parameter 'docs[1]' must be the handle of a document from the conversation: a JSON object such as {"type":"camunda.agenticai.document","id":"doc_…"}.`
In a union parameter that holds a handle while the agent knows no documents, the first sentence reads
`Parameter 'message.file' holds a document handle, but the conversation has no documents. Do not pass a document handle.`
(`name`, `parent.name` and `name[i]` are the paths of the value; an array parameter that is not an array reads
`Parameter 'p' must be an array.` and the object that holds a document parameter, when it is not an object, reads
`Parameter 'p' must be an object.`). A missing or `null` argument is left alone.

`DocumentParameterResolver` resolves the document parameters of a call from the call, the tool definition and the agent
documents only. A process tool receives the Camunda document reference JSON of the agent document (`resolveProcessCall`), a
runtime tool the `AgentDocumentId` of the document (`resolveRuntimeCall`); `check` validates the call of a tool of either kind. An `Untracked` agent instance has
no document parameters: the calls are not touched. The agent uses the resolver at three places, and the tool call of the
assistant message is never rewritten, so the stored conversation and the history keep what the model sent:

- **The admission of a call.** `ToolCallAdmission` admits the tool calls of an assistant message before it is ingested
  (see [What Happens When Tools Complete](../../docs/architecture/agentic-loop.md#what-happens-when-tools-complete)). A call of a tool of either kind whose document parameters are invalid keeps its kind and
  gets the sentences above as its `ToolCall.rejection`. Like every rejected call it has the rejection as its tool call result from the
  moment its `ToolCallBatch` is created, and is never executed or activated.
- **The call that goes to the process.** `AgentResponseHandlerImpl` resolves the document parameters of the process tool
  calls of the last turn into the Camunda document references before the gateway transformation, and the response carries
  them.
- **The execution of a runtime call.** `RuntimeTools` resolves the document parameters of a runtime tool call with the
  definition the tool is registered with into `AgentDocumentId`s before it calls the tool. A call whose document
  parameters are invalid gets the sentences above as its error result and the tool is not called.

A parameter that takes a document or something else (a union parameter) is declared with the schema argument of `fromAi()`,
an `anyOf` (or `oneOf`) with more than one alternative, a `document` among them, directly or inside an object alternative
(its properties) or an array alternative (its items). Leave the type argument out with named arguments, so the type of the
other alternatives stays valid. Text or a document:

```feel
fromAi(
  value: toolCall.body,
  description: "The body of the message.",
  schema: {"anyOf": [{"type": "string"}, {"type": "document"}]}
)
```

One document or a list of documents:

```feel
fromAi(
  value: toolCall.files,
  description: "The files to attach.",
  schema: {"anyOf": [{"type": "document"}, {"type": "array", "items": {"type": "document"}}]}
)
```

Text or a file with a caption:

```feel
fromAi(
  value: toolCall.message,
  description: "The message.",
  schema: {"anyOf": [
    {"type": "string"},
    {"type": "object", "properties": {"file": {"type": "document"}, "caption": {"type": "string"}}}
  ]}
)
```

The alternative `document` becomes the handle schema wherever the shorthand sits in an alternative, and the other
alternatives stay as authored. A type array that lists `document`, such as `schema: {"type": ["string", "document"]}`, is not
supported: the tool schema generation fails for the element with a message that asks for `"type": "document"` or an `anyOf`
with `{"type": "document"}` as one alternative. A type array without `document` stays as authored. The agent
does not assign the value to an alternative: a handle identifies itself by its `type`, so the agent scans the value of a union
and every handle at or below it is a document. The tool receives the Camunda document reference for it, a runtime tool an
`AgentDocumentId`, and a handle with an unknown id is answered with the sentence above, with the path of the handle (for
example `files[1]` or `message.file`), and the tool does not run. Everything else is passed as it is: text (also a string that
equals the id of a known document, or a string that is not a handle as JSON), a number, a boolean, `null`, and the rest of an
object (for another alternative, such as a structured value) or an array around its handles; a runtime tool receives a `String`
for text. This
holds also for a union without a text alternative. A union parameter is resolved by the same `DocumentParameterResolver`, at the
same three places as a document parameter: a call of a tool of either kind with an unknown handle is rejected at admission,
keeps its kind, carries the sentence as its `ToolCall.rejection` and has the rejection as its tool call result. A union is stored as
it is authored, with the shorthand turned into the handle schema. A union with a handle schema in an alternative gets no default
`type: string` next to its `anyOf`. The search for document parameters stops at a union, and it does not look into `allOf`,
`not`, `if`/`then`/`else`, `additionalProperties`, `patternProperties`, `prefixItems`, `$defs` or `$ref`. The output of the Ad-Hoc
Tools Schema Resolver carries the handle schema. The providers receive the handle schema as it is: Gemini unchanged, OpenAI in
strict mode (the handle object is closed with `additionalProperties: false`) and LangChain4j with a `oneOf` that has no `type` or
`$ref` of its own sent as an `anyOf` of the same alternatives. The shorthand type `document` is normalized only for `fromAi()` parameters. See
[ADR 018](../../docs/adr/018-document-flow.md).
