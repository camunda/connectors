# aiagent-otel

## Purpose

OpenTelemetry tracing of the AI Agent that follows the GenAI semantic conventions, as an agent lifecycle
listener. The module does nothing unless `camunda.connector.agenticai.aiagent.tracing.enabled` is `true` and the
runtime provides an `OpenTelemetry` bean. It depends on the OpenTelemetry API, `aiagent-api`, `aiagent-model` and
`agentic-ai-common`, not on `connector-aiagent`.

## Key types

- `AgentTracingListener`: the lifecycle listener that starts the observer of each traced job.
- `AgentTracingJobObserver`: turns the lifecycle events of one job into spans.
- `AgentTracer`: creates the spans of a run with explicit times and parents.
- `RunTraceState` and `RunTraceStateResolver`: the run state kept in the agent context, and the decision
  whether a job continues the run or starts a new one.
- `GenAiAttributeMapper` and `GenAiAttributes`: the GenAI and Camunda span attributes.
- `AgentTracingAutoConfiguration` and `AgentTracingProperties`: the Spring configuration.

## Docs

- [OpenTelemetry GenAI tracing](docs/opentelemetry-tracing.md)
- [Agent lifecycle listener SPI](../aiagent-api/docs/agent-lifecycle-listener.md)
- [Tracing backends for local development](../examples/tracing/README.md)
