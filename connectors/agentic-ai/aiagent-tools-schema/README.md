# aiagent-tools-schema

## Purpose

Resolves the tools of an ad-hoc sub-process. It reads the tool elements, extracts the `fromAi()`
parameters from their FEEL expressions and generates a JSON schema for each tool. Gateway tool elements
(MCP, A2A) are returned separately.

## Key types

- `AdHocToolsSchemaResolver`: resolves the tool definitions and the gateway tool definitions of the
  tool elements.
- `ProcessDefinitionAdHocToolElementsResolver`: reads the tool elements from the BPMN XML of a process
  definition. `CachingProcessDefinitionAdHocToolElementsResolver` caches the result.
- `AdHocToolElementParameterExtractor`: extracts the `fromAi()` parameters from a FEEL expression.
- `AdHocToolSchemaGenerator`: generates the JSON schema of a tool from its parameters.
- `AgenticAiToolsSchemaAutoConfiguration` and `ToolsSchemaProperties`: the Spring configuration.

## Docs

- [Tool resolution](docs/tool-resolution.md)
- [Extension points](../docs/architecture/extension-points.md): gateway tool detection
- [Spring auto-configuration](../docs/architecture/spring-auto-configuration.md)
