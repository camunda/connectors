# connector-ad-hoc-tools-schema

## Purpose

The Ad-Hoc Tools Schema connector. It returns the tool definitions of an ad-hoc sub-process, so that
a custom LLM connector can use them without the AI Agent.

## Key types

- `AdHocToolsSchemaFunction`: the connector function.
- `AdHocToolsSchemaConnectorAutoConfiguration`: registers the connector.

## Docs

- [Tool resolution](../aiagent-tools-schema/docs/tool-resolution.md)
- [Spring auto-configuration](../docs/architecture/spring-auto-configuration.md)
- [Element templates](element-templates/README.md)

## Ad-Hoc Tools Schema Example

- **`ad-hoc-tools-schema/`**: Direct use of the Ad-Hoc Tools Schema Resolver
  - For custom LLM connectors that want to leverage AHSP tool metadata without the full AI Agent
