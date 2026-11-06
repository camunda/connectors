# agentic-ai-common

## Purpose

Shared helpers for the agentic AI modules, grouped by topic: JSON, JSON schema, retry, HTTP, validation
and gateway tool constants. The module also holds the common configuration properties. It depends on no
other agentic AI module.

## Key types

- `AgenticAiCommonAutoConfiguration`: registers `AgenticAiHttpProxySupport` and `AgenticAiHttpProperties`.
- `AgenticAiHttpProxySupport`: HTTP proxy support for the HTTP clients of the agentic AI connectors.
- `AgenticAiRecord`: marks a record that gets a generated builder.
- `CamundaApiRetry`: runs an operation and retries retryable failures with exponential backoff
  (`ExponentialBackoffRetry`).
- `GatewayToolConstants`: the name of the BPMN extension property that marks a gateway tool element.
- `HttpUrl`: validation annotation for an absolute HTTP or HTTPS URL.

## Docs

- [Spring auto-configuration](../docs/architecture/spring-auto-configuration.md)
- [Extension points](../docs/architecture/extension-points.md): gateway tool detection
