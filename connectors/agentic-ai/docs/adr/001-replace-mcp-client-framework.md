# Overcome MCP client limitations

* Deciders: Agentic AI Team
* Date: Feb 3, 2026

## Status

**Implemented**.

`langchain4j-mcp` was replaced by prominent [`MCP Java SDK`](https://github.com/modelcontextprotocol/java-sdk) in PRs [#6226](https://github.com/camunda/connectors/pull/6226) and [#6279](https://github.com/camunda/connectors/pull/6279)

## Context and Problem Statement

The Camunda Connectors Agentic AI module requires a robust MCP (Model Context Protocol) client implementation to
communicate with MCP servers. The current implementation uses `langchain4j-mcp`, but we have encountered several
limitations that hinder our ability to provide a production-ready, specification-compliant MCP client. Should we
continue with `langchain4j-mcp` or migrate to the established [
`MCP Java SDK`](https://github.com/modelcontextprotocol/java-sdk)?

## Decision Drivers

* **Extensibility**: Need ability to customize HTTP client configuration, especially upcoming features regarding
  outbound proxy connection, without waiting for upstream changes.
* **Specification compliance**: Should support latest MCP specification attributes in domain models / catch up soon
  after specification creation
* **Maintenance overhead**: Avoid dependency on upstream project's release cycle for closing potential limitations on
  framework capabilities

## Considered Options

1. Continue with `langchain4j-mcp` and submit PRs for missing features
2. Replace with [`MCP Java SDK`](https://github.com/modelcontextprotocol/java-sdk) from modelcontextprotocol.io

## Decision Outcome

Chosen option: **Option 2 \- Replace with MCP Java SDK**  
because it is the official reference implementation maintained by the MCP specification authors, provides almost
complete specification compliance, and allows rich control over transport configuration with access to the internal
HTTP client. Furthermore, important framework decisions are implemented in a pluggable way that ensures the ability to
substitute default options, like JSON serdes library, HTTP transport implementation, etc.

### Positive Consequences

* Full control over HTTP transport configuration with access to internal HTTP client (especially important for proxy
  configuration)
* Rich domain model covering the MCP schema of `2025-06-18` specification
* Reference implementation with high potential for future specification alignment and good maintenance policy

### Negative Consequences

* Migration effort required for existing codebase
* Potential requirement to temporarily support both implementations in parallel during the migration period

## Pros and Cons of the Options

### Option 1: Continue with langchain4j-mcp

The LangChain4j project provides an MCP client integration as part of their ecosystem.

* Good, because no migration effort
* Good, because patterns of LangChain4j ecosystem we already use are well-known
* Bad, because domain model lags behind MCP specification (e.g., missing `ResourceLink` attributes)
* Bad, because overcoming limitations requires to contribute upstream PR and wait for release cycle (usually once per
  month)
* Bad, because philosophy explicitly discourages access to internals, like HTTP client

### Option 2: Replace with MCP Java SDK

Use the MCP Java SDK referenced by modelcontextprotocol.io

* Good, because official reference implementation
* Good, because design philosophy is pluggable, meaning certain pointcuts allow own implementations and access to some
  internals
* Good, because designed for simplicity, allowing lightweight integration without many external dependencies, but
  leaving room for richer integration (e.g. into Spring ecosystem)

* Good, because extensible transport configuration via standard Java HTTP clients with various customization options
    * Supports custom HTTP client configuration
    * Requires no additional dependencies; uses the standard JDK HTTP client by default
* Good, because full specification compliance expected soon after release
* Good, because used by Spring AI project
    * Broad community adoption ensures proper maintenance and exploration of issues / limitations
* Bad, because requires migration effort
* Bad, because newer project with potentially evolving APIs
* Bad, because the internal code architecture wrapping async code in sync (blocking adapters) is far more complex

## Implementation Approach

The migration follows a side-by-side implementation strategy:

1. **Framework abstraction layer**: Introduce `McpClientFactory` interface with implementations for both SDKs (part of PR [#6226](https://github.com/camunda/connectors/pull/6226))
2. **Configuration-based selection**: Use configuration properties to select active implementation (part of PR [#6226](https://github.com/camunda/connectors/pull/6226))
3. **Domain model mapping**: Create dedicated RPC request classes that map MCP SDK types to internal domain models (part of PR [#6226](https://github.com/camunda/connectors/pull/6226))
4. **Gradual migration**: Introduce MCP-SDK implementation in coexistence with existing Lanchain4J implementation. Then
   assure quality of implementation by exploration and comparison. Finally, remove langchain4j based implementation and
   switch to `mcpsdk` as runtime default. (PR [#6279](https://github.com/camunda/connectors/pull/6279))


## References

* [MCP Specification](https://spec.modelcontextprotocol.io/)
* [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)
* [LangChain4j MCP](https://github.com/langchain4j/langchain4j/tree/main/langchain4j-mcp)