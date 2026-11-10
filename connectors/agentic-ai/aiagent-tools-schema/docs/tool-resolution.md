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
1. **ToolDefinitions**: Regular tools with name, description, JSON schema for input parameters and the optional
   `documentDelivery`
2. **GatewayToolDefinitions**: Special tools (MCP clients, A2A) that need discovery

A tool activity with the extension property `io.camunda.agenticai.documents.delivery` gets the `documentDelivery` the
property names (`DocumentDelivery.fromExtension`: `reference` or `content`, ignoring case and surrounding blanks; a blank or
absent property is `reference`). A value that is neither fails the tool discovery with a message that names the element
and the allowed values. With `content` the agent adds the content of the documents the tool returns to the request instead
of leaving them as references. The field is part of the stored tool definition and of the configuration fingerprint when
set, and the model providers do not get it (`ToolDefinition.withoutDocumentDelivery()` drops it where the conversation is windowed for the model call). See [ADR 018](../../docs/adr/018-document-flow.md).

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
