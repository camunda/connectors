# MCP Integration Reference

This document provides a comprehensive, code-level reference for the MCP (Model Context Protocol) integration in the
`agentic-ai` module. MCP enables the AI Agent to discover and call tools from MCP servers.

For the core AI Agent architecture, see [`agentic-loop.md`](../../docs/architecture/agentic-loop.md).
For the Gateway Tool Pattern that MCP implements, see [`extension-points.md`](../../docs/architecture/extension-points.md#gateway-tool-pattern).

## Overview

MCP integration is bidirectional:

1. **Agent Integration**: MCP Client elements inside the AHSP act as gateway tools, exposing MCP server tools to the LLM
2. **Standalone**: MCP Client connector used independently (not as an AI Agent tool)

MCP exposes **multiple discrete tools per server** — one MCP Client BPMN element becomes N tools the LLM can call.

## Connector Types

**MCP Client** (`McpClientFunction`):
- Type: `io.camunda.agenticai:mcpclient:1`
- Uses pre-configured MCP connections on the runtime (via Spring properties)
- Client lifecycle managed by `McpClientRegistry`
- Supports STDIO and HTTP/SSE transports

**MCP Remote Client** (`McpRemoteClientFunction`):
- Type: `io.camunda.agenticai:mcpremoteclient:1`
- Connects to remote MCP servers via HTTP (Streamable HTTP or SSE)
- Connection configured per connector instance (URL, auth, etc.)
- Client lifecycle managed by `McpRemoteClientRegistry` with Caffeine cache
- Cache key: `(processDefinitionKey, elementId)` — scoped per deployment + element
- Supports Basic, Bearer, and OAuth authentication

## Package Structure

See `io.camunda.connector.agenticai.mcp` — split into `client` (connectors, handlers, execution, framework, model) and `integration` (gateway tool integration) sub-packages.

The two sub-packages ship as separate Maven modules:

- `connector-mcp` holds `mcp.client` (the MCP Client and MCP Remote Client connectors, their element templates and
  Spring auto-configuration). It depends on `agentic-ai-common` only.
- `aiagent-mcp-integration` holds `mcp.integration` (the gateway tool handler and
  `McpClientAgenticToolAutoConfiguration`). It depends on `connector-mcp`, `aiagent-api`, `aiagent-model` and
  `agentic-ai-common`, not on `connector-aiagent`.

## MCP Client Architecture

```
McpClientFunction / McpRemoteClientFunction
  │
  ├── McpClientHandler / McpRemoteClientHandler
  │     └── resolves client from registry, creates operation
  │
  ├── McpClientExecutor
  │     ├── routes to McpClientDelegate method by operation
  │     └── McpClientResultDocumentHandler (binary → document conversion)
  │
  ├── McpClientDelegate (interface)
  │     ├── listTools(filter, meta)
  │     ├── callTool(params, filter, meta)
  │     ├── listResources(filter, meta), readResource(params, filter, meta)
  │     └── listPrompts(filter, meta), getPrompt(params, filter, meta)
  │
  └── Implementation: MCP SDK (mcpsdk package)
        └── McpSdkClientFactory → creates MCP SDK client instances
```

### MCP Operations

| Operation                 | Delegate method                          | Used By                                           |
|---------------------------|------------------------------------------|---------------------------------------------------|
| `LIST_TOOLS`              | `listTools(filter, meta)`                | Discovery (agent integration) and standalone       |
| `CALL_TOOL`               | `callTool(params, filter, meta)`         | Tool execution (agent integration) and standalone  |
| `LIST_RESOURCES`          | `listResources(filter, meta)`            | Standalone only                                    |
| `LIST_RESOURCE_TEMPLATES` | `listResourceTemplates(filter, meta)`    | Standalone only                                    |
| `READ_RESOURCE`           | `readResource(params, filter, meta)`     | Standalone only                                    |
| `LIST_PROMPTS`            | `listPrompts(filter, meta)`              | Standalone only                                    |
| `GET_PROMPT`              | `getPrompt(params, filter, meta)`        | Standalone only                                    |

All operations accept an optional `meta: Map<String, Object>`, forwarded unmodified as the MCP request's `_meta`
field (see [Request Data Model](#request-data-model)). When not configured, no `_meta` field is sent — fully backwards compatible.

### RPC Layer

Each operation is implemented by a package-private request class in the `rpc` subpackage:
- `ListToolsRequest`: Calls `McpSyncClient.listTools()` when `meta` is unset (preserving prior behavior exactly),
  or `listTools(cursor, meta)` (`cursor` always `null`, pagination is not supported) when `meta` is configured;
  the four list operations (`ListToolsRequest`, `ListResourcesRequest`, `ListResourceTemplatesRequest`,
  `ListPromptsRequest`) all follow this same guard so the `meta`-aware SDK overload is only exercised when
  actually needed. Applies `AllowDenyList`, maps `McpSchema.Tool` → `McpToolDefinition`
- `ToolCallRequest`: Validates tool name, parses parameters, and calls `McpSyncClient.callTool()` with a
  `McpSchema.CallToolRequest` built via `.meta(meta)`. Maps response
  content: `TextContent` → `McpTextContent`, `ImageContent`/`AudioContent` → `McpBlobContent` (Base64 decoded),
  `EmbeddedResource` → `McpEmbeddedResourceContent`, `ResourceLink` → `McpResourceLinkContent`,
  `structuredContent` → `McpObjectContent`. Failures during the MCP call or response mapping are caught and returned as
  `McpClientCallToolResult` with `isError = true`; earlier validation/argument parsing failures (e.g.,
  `ConnectorException(MCP_CLIENT_INVALID_PARAMS)` or null tool name) propagate as exceptions to the caller.

The `meta` parameter is forwarded unmodified into the corresponding MCP SDK request object for every operation:
`CallToolRequest.builder(...).meta(meta)`, `ReadResourceRequest(uri, meta)`, `GetPromptRequest(name, arguments, meta)`,
and the SDK's `(cursor, meta)` client overloads for the four list operations.

## Request Data Model

### McpClientRequest (runtime-configured)

```
McpClientRequest
└── data: McpClientRequestData
    ├── client: ClientConfiguration
    │   └── clientId: String              // ID registered in the runtime
    └── connectorMode: McpConnectorModeConfiguration
```

### McpRemoteClientRequest (on-demand remote)

```
McpRemoteClientRequest
└── data: McpRemoteClientRequestData
    ├── transport: McpRemoteClientTransportConfiguration  (sealed: StreamableHttp | SSE)
    ├── options: McpRemoteClientOptionsConfiguration
    │   └── clientCache: Boolean          // whether to cache the client
    └── connectorMode: McpConnectorModeConfiguration
```

### McpConnectorModeConfiguration (sealed interface)

Discriminated by `type` JSON property:

**`ToolModeConfiguration`** (`type = "aiAgentTool"`): Used as AI agent tool gateway.
- `toolOperation: McpClientOperationConfiguration` — method + params
- `meta: Map<String, Object>` — optional `meta` FEEL property, forwarded unmodified as the MCP request's `_meta` field
- `toolModeFilters: McpClientToolModeFiltersConfiguration` — optional tool allow/deny

**`StandaloneModeConfiguration`** (`type = "standalone"`): Used for direct invocation.
- `operation: McpStandaloneOperationConfiguration` — method + optional params
- `meta: Map<String, Object>` — optional `meta` FEEL property, forwarded unmodified as the MCP request's `_meta` field
- `standaloneModeFilters: McpClientStandaloneFiltersConfiguration` — tools + resources + prompts allow/deny

`meta` lives once per mode (bound to `data.connectorMode.meta`), not per operation subtype, since the underlying MCP
SDK supports `_meta` on every operation — a single shared property keeps the element template compact.

Both implement `toMcpClientOperation()` → `McpClientOperation` and `createFilterOptions()` → `Optional<FilterOptions>`.
`toMcpClientOperation()` forwards the mode-level `meta` into `McpClientOperation`, defaulting to an empty
map when unconfigured.

### McpClientOperation (sealed interface)

`McpClientOperation#method` is the `McpMethod` to call (`LIST_TOOLS`, `CALL_TOOL`, `LIST_RESOURCES`,
`LIST_RESOURCE_TEMPLATES`, `READ_RESOURCE`, `LIST_PROMPTS` or `GET_PROMPT`). `McpClientOperation#params` holds the
method-specific parameters, and `McpClientOperation#meta` is forwarded unmodified as the `_meta` field of the MCP request.

`McpMethod` enum maps to MCP JSON-RPC method strings: `"tools/list"`, `"tools/call"`, `"resources/list"`, etc.

Factory methods: `of(method)`, `of(method, params)`, `of(method, params, meta)` — the 1-/2-arg overloads delegate to
the 3-arg one with an empty `meta` map.

`McpClientOperationDefinitions` provides factory methods: `listTools()`, `callTool(name, arguments)`. These back the
AI-agent-internal tool discovery/invocation path (see [Discovery Flow](../../aiagent-mcp-integration/docs/mcp-gateway-tool.md#discovery-flow) and
[Tool Call Execution Flow](../../aiagent-mcp-integration/docs/mcp-gateway-tool.md#tool-call-execution-flow)), which is separate from the static Operation-group
configuration above and has no `meta` FEEL property.

## Result Data Model

### Result Type Hierarchy

```
McpClientResult (sealed interface)
├── McpClientListToolsResult         (toolDefinitions: List<McpToolDefinition>)
├── McpClientListPromptsResult
├── McpClientListResourcesResult
├── McpClientListResourceTemplatesResult
└── McpClientResultWithStorableData (sealed sub-interface)
    ├── McpClientCallToolResult      (name, content: List<McpContent>, isError)
    ├── McpClientGetPromptResult
    └── McpClientReadResourceResult
```

`McpClientResultWithStorableData` has method `convertStorableMcpResultData(DocumentFactory)` for binary→document
conversion.

### McpContent (sealed interface)

Jackson polymorphic on `type` property:

| Type            | Class                        | Fields                                      |
|-----------------|------------------------------|---------------------------------------------|
| `text`          | `McpTextContent`             | `String text`                               |
| `blob`          | `McpBlobContent`             | `byte[] blob`, `String mimeType`            |
| `object`        | `McpObjectContent`           | structured JSON                             |
| `resource`      | `McpEmbeddedResourceContent` | `EmbeddedResource` (Text/Blob/BlobDocument) |
| `resource_link` | `McpResourceLinkContent`     | `uri, name, description, mimeType`          |
| `document`      | `McpDocumentContent`         | Camunda Document reference                  |

### McpToolDefinition

`McpToolDefinition` is a record with the tool `name`, an optional `title` and `description`, and the JSON
`inputSchema` of the tool arguments.

## Client Lifecycle

### McpClientFactory (interface)

`McpClientFactory#createClient` creates an `McpClientDelegate` for a client id and an `McpClientConfiguration`.

`McpSdkClientFactory` implements this using the `io.modelcontextprotocol:mcp` SDK:
- Client info: `Camunda 8 MCP Connector` / `1.0.0`
- Default timeout: 30 seconds
- Supported protocol versions: `MCP_2024_11_05`, `MCP_2025_03_26`, `MCP_2025_06_18`

### Pre-configured Clients (McpClientRegistry)

- `Map<String, Supplier<McpClientDelegate>>` — registered lazily during Spring context init
- `Map<String, McpClientDelegate>` — `ConcurrentHashMap`, `computeIfAbsent` on first `getClient()`
- Duplicate registration throws; `close()` only closes actually-constructed clients

### Remote Clients (McpRemoteClientRegistry)

- Caffeine `Cache<McpRemoteClientIdentifier, McpClientDelegate>`
- Key: `McpRemoteClientIdentifier(processDefinitionKey: Long, elementId: String)`
- `getClient(id, transport, cacheable)`: if `cacheable=true`, uses cache; if `false`, creates new client (caller
  responsible for closing)
- Eviction listener auto-closes evicted clients
- Default: max 15 entries, expires 10 minutes after last access

## Transport & Authentication

### Transport Types

`McpSdkClientFactory` creates transports:
- **STDIO**: `StdioClientTransport(ServerParameters.builder(command).args(args).build())`
- **Streamable HTTP**: `HttpClientStreamableHttpTransport.builder(url)` with custom headers supplier
- **SSE**: `HttpClientSseClientTransport.builder(url)` with custom headers supplier

### McpRemoteClientTransportConfiguration (sealed interface)

Discriminated by `type` property:

**`StreamableHttpMcpRemoteClientTransportConfiguration`** (`type = "http"`):
- `http.url` — HTTP or HTTPS URL, typically ending with `/mcp`
- `http.headers` — static headers map
- `http.authentication` — `Authentication` sealed interface
- `http.timeout` — ISO-8601 Duration

**`SseHttpMcpRemoteClientTransportConfiguration`** (`type = "sse"`):
- `sse.url` — HTTP or HTTPS URL, typically ending with `/sse`
- `sse.headers`, `sse.authentication`, `sse.timeout`

### Authentication (sealed interface)

Jackson polymorphic on `type` property:
- `NoAuthentication` (`type = "noAuth"`)
- `BasicAuthentication` (`type = "basic"`) — username + password
- `BearerAuthentication` (`type = "bearer"`) — token
- `OAuthAuthentication` (`type = "oauth"`) — full OAuth2 config

`McpClientHeadersSupplierFactory` builds a composite `Supplier<Map<String, String>>` merging static headers with auth
headers (uses `OAuthService` + `CustomApacheHttpClient` for OAuth).

## Filtering

### AllowDenyList

`AllowDenyList#isPassing` accepts a name when the `allowed` list is empty or contains it, and the `denied` list
does not contain it. `AllowDenyList#allowingEverything` returns the list with both lists empty.

### FilterOptions

`FilterOptions` holds one `AllowDenyList` each for tools, resources and prompts. `FilterOptions#defaultOptions`
allows everything.

Filters are configured per connector instance via the element template. Both MCP Client types support allow/deny
filtering for tools, resources, and prompts.

## Binary Content & Document Handling

`McpClientResultDocumentHandler` checks if `McpClientResult instanceof McpClientResultWithStorableData` and calls
`convertStorableMcpResultData(documentFactory)`. This converts:

- `McpBlobContent` → `McpDocumentContent` (Camunda Document)
- `McpEmbeddedResourceContent.BlobResource` → `McpEmbeddedResourceContent.BlobDocumentResource`

This handles MCP resources that return binary data (images, files) by storing them as Camunda Documents.

## Spring Configuration

### McpClientAutoConfiguration

Auto-configuration for the MCP client connectors, ordered after `AgenticAiCommonAutoConfiguration`.
Imports `McpClientConfiguration` and `McpRemoteClientConfiguration`.

### McpClientBaseConfiguration

Always imported by both runtime and remote configs. Creates:
`McpClientExecutor`, `McpClientHeadersSupplierFactory`, `McpClientResultDocumentHandler`

### McpClientConfiguration (runtime clients)

**Condition**: `camunda.connector.agenticai.mcp.client.enabled` (default: **false** — opt-in)

Beans: `McpClientFunction`, `DefaultMcpClientHandler`, `McpClientRegistry`

Imports: `McpClientBaseConfiguration`, `McpSdkMcpClientConfiguration`

### McpRemoteClientConfiguration (remote clients)

**Condition**: `camunda.connector.agenticai.mcp.remote-client.enabled` (default: **true**)

Beans: `McpRemoteClientFunction`, `DefaultMcpRemoteClientHandler`, `McpRemoteClientRegistry`

Imports: `McpClientBaseConfiguration`, `McpSdkMcpRemoteClientConfiguration`

### Configuration Properties

**Runtime client** (`camunda.connector.agenticai.mcp.client`):

```yaml
camunda.connector.agenticai.mcp.client:
  enabled: false          # opt-in
  clients:
    my-server:
      enabled: true
      type: STDIO | HTTP | SSE
      stdio:
        command: "npx"
        args: ["-y", "@modelcontextprotocol/server-filesystem"]
        env: {}
      http:
        url: "https://my-mcp-server.com/mcp"
        headers: {}
        authentication:
          type: NONE | BASIC | BEARER | OAUTH
          ...
        timeout: PT30S
      initializationTimeout: PT30S
      toolExecutionTimeout: PT30S
      reconnectInterval: PT5S
```

**Remote client** (`camunda.connector.agenticai.mcp.remote-client`):

```yaml
camunda.connector.agenticai.mcp.remote-client:
  enabled: true           # on by default
  client:
    cache:
      enabled: true
      maximumSize: 15
      expireAfter: PT10M
```

### SDK Factory Disambiguation

`McpSdkMcpClientConfiguration` creates `McpSdkClientFactory` annotated `@RuntimeMcpClientFactory`.
`McpSdkMcpRemoteClientConfiguration` creates `McpSdkClientFactory` annotated `@RemoteMcpClientFactory`.
These Spring `@Qualifier` meta-annotations disambiguate the two factory beans.

## Error Codes

From `McpClientErrorCodes`:

| Constant                           | Value                            | Context                                     |
|------------------------------------|----------------------------------|---------------------------------------------|
| `ERROR_CODE_INVALID_PARAMS`        | `MCP_CLIENT_INVALID_PARAMS`      | Tool call params deserialization failure     |
| `ERROR_CODE_GET_PROMPT_ERROR`      | `MCP_CLIENT_GET_PROMPT_ERROR`    | Error during get-prompt operation            |
| `ERROR_CODE_READ_RESOURCE_ERROR`   | `MCP_CLIENT_READ_RESOURCE_ERROR` | Error during read-resource operation         |
| `ERROR_CODE_INVALID_METHOD`        | `MCP_CLIENT_INVALID_METHOD`      | Unknown MCP method string                   |

## Key Source Files

| File                                           | Purpose                                                       |
|------------------------------------------------|---------------------------------------------------------------|
| `McpClientFunction.java`                       | Pre-configured MCP client connector entry point               |
| `McpRemoteClientFunction.java`                 | Remote MCP client connector entry point                       |
| `McpClientRegistry.java`                       | Pre-configured client lifecycle management                    |
| `McpRemoteClientRegistry.java`                 | Remote client cache with Caffeine                             |
| `McpClientExecutor.java`                       | Routes operations to McpClientDelegate methods                |
| `McpClientDelegate.java`                       | Abstract MCP client interface                                 |
| `McpSdkClientFactory.java`                     | Creates MCP SDK client instances                              |
| `McpClientResultDocumentHandler.java`          | Binary content → Camunda document conversion                  |
