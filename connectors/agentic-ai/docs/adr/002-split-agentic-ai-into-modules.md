# Split Agentic AI into independent modules

* Deciders: Agentic AI Team
* Date: Feb 19, 2026

## Status

**Implemented**.

The monolithic `connector-agentic-ai` module was split into 5 sub-modules: `api`, `schema`, `agent`, `mcp`, `a2a`.

## Context and Problem Statement

All agentic AI functionality (AI Agent, MCP Client, A2A Client, Ad-Hoc Tools Schema) resides in a single
`connector-agentic-ai` Maven module. This prevents consumers from using individual components without pulling in the
entire module and all its transitive dependencies (LangChain4j, AWS SDK, Azure SDK, A2A SDK, MCP SDK, etc.).

The monolithic structure also makes it difficult to:
- Develop and test components independently
- Add new gateway tool integrations without modifying the core module
- Control dependency exposure and classpath pollution

## Decision Drivers

* **Independent consumption** — consumers should be able to use only MCP or only A2A without pulling in AI Agent dependencies
* **Extensibility** — adding new gateway tool handlers should not require modifying the core module
* **Build performance** — smaller modules enable faster incremental builds
* **Backward compatibility** — existing consumers should not need code changes

## Considered Options

1. **Keep monolithic module** — status quo, manage complexity with internal packages
2. **Split into 5 modules with thin API module** — temporary API module for cross-module SPIs
3. **Split with no shared module** — each module fully self-contained with its own boundary types

## Decision Outcome

**Option 2: Split into 5 modules with a thin temporary API module.**

The API module contains only SPI interfaces (`GatewayToolHandler`, `GatewayToolDefinitionResolver`,
`SystemPromptContributor`) and the types referenced by their method signatures. A future issue will eliminate this
module by internalizing boundary types per module.

### Module Structure

```
connectors/agentic-ai/
├── api/      # connector-agentic-ai-api (temporary — SPI interfaces only)
├── schema/   # connector-agentic-ai-schema (Ad-Hoc Tools Schema)
├── agent/    # connector-agentic-ai-agent (AI Agent, LangChain4j)
├── mcp/      # connector-agentic-ai-mcp (MCP Client)
└── a2a/      # connector-agentic-ai-a2a (A2A Client)
```

### Dependency Graph

```
api ← schema ← agent
api ← mcp
api ← a2a
```

MCP and A2A are independent of each other and of Agent/Schema.

### Positive Consequences

* Consumers can depend on individual modules (e.g., only `connector-agentic-ai-mcp`)
* New gateway tool handlers can be added as separate modules depending only on `api`
* Faster incremental builds — changes to MCP don't rebuild Agent
* Clear dependency boundaries prevent accidental coupling

### Negative Consequences

* Temporary API module adds a transitional artifact that must be eliminated later
* More complex Maven multi-module configuration
* Element template generation requires per-module plugin configuration

## Implementation Approach

1. Convert `connector-agentic-ai` to parent POM (`packaging=pom`)
2. Use `git mv` to relocate source files into sub-modules (preserves git history)
3. Each module gets its own auto-configuration, element templates, and Spring metadata
4. Package names are preserved — no breaking changes for consumers
5. `ConversationContext` subtypes registered via Jackson `BeanPostProcessor` instead of `@JsonSubTypes`
   (since subtypes are in agent module but interface is in API module)

## References

* [Issue #5003](https://github.com/camunda/connectors/issues/5003) — Refactor agentic AI module for better extensibility
