# OpenTelemetry GenAI tracing

The AI Agent can export OpenTelemetry traces that follow the GenAI semantic conventions, for
LLM-observability backends such as Langfuse and Arize Phoenix. Tracing is a second channel next to the
agent instance history that Operate shows. It is built on the experimental agent lifecycle listener API
([ADR 018](../adr/018-agent-lifecycle-listeners.md)) by the `aiagent-otel` module
([ADR 019](../adr/019-opentelemetry-genai-tracing.md)). It is a proof of concept: the attribute set and the
lifecycle events may change.

## Enable it

`aiagent-otel` ships in the agentic AI bundle but does nothing until you enable it. The default runtime
contains no OpenTelemetry SDK or exporter, so you add Spring Boot's OpenTelemetry support first.

1. Add `spring-boot-starter-opentelemetry` (version managed by your Spring Boot version) to the runtime. On
   the stock image, put the jars in the custom library directory with versions that match the image.
2. Set the properties:

   ```properties
   camunda.connector.agenticai.aiagent.tracing.enabled=true
   # prompts, completions, tool arguments and results become span attributes
   camunda.connector.agenticai.aiagent.tracing.capture-content=false
   # optional prefix of the session id, for several clusters in one backend project
   camunda.connector.agenticai.aiagent.tracing.cluster-id=
   management.tracing.sampling.probability=1.0
   management.opentelemetry.tracing.export.otlp.endpoint=http://localhost:6006/v1/traces
   management.opentelemetry.tracing.export.otlp.transport=http
   ```

   Spring Boot samples 10% of traces by default, so set the probability to `1.0`, or only about one run in
   ten is exported. The decision is made once per run and holds for every span of the run.
3. Spring Boot's Observation-to-span bridge would also trace inbound webhooks, actuator calls and other
   connectors' HTTP clients. Exclude it if you only want agent spans
   (`spring.autoconfigure.exclude=org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration`;
   the exclusion is still to be verified against the Spring Boot version you run).

The tracing listener is registered only when `tracing.enabled` is `true` and an `OpenTelemetry` bean
exists. `camunda.connector.agenticai.enabled=false` disables the module.

For a local setup with Phoenix or Langfuse, see
[`examples/tracing`](../../examples/tracing/README.md): Docker Compose files and Spring profiles. To start
the runtime locally from the IDE, run `LocalConnectorRuntime`; the `local` profile of the default bundle
enables tracing against Phoenix on port 6006.

## What you get

Only AI Agent Sub-process agents that have an agent instance are traced. Task agents and agents without an
agent instance emit nothing.

- **One trace per run.** A run starts with an input (the first activation, a user message, or an event) and
  ends with the turn whose model response has no tool calls. The next input starts a new trace, which links
  to the previous run's anchor span.
- **Anchor span** `<element id> run` (internal, no GenAI operation): the root of the run's trace.
- **`invoke_agent`** per job that does agent work (tool discovery or a turn), with the job's real duration.
- **`chat`** per model call, including provider continuation rounds: provider, models, token usage, finish
  reason, response id.
- **`execute_tool`** per tool result a turn consumes: from the dispatch of the tool call to the engine's
  completion time, so a user task that takes days appears as a long span under the consuming job. The span
  start is derived from the time-ordered id of the assistant message that issued the call.
- **Session.** The agent instance key (prefixed `<cluster-id>:` if configured) is both `session.id` and
  `gen_ai.conversation.id` on every span, so backends group all runs of one agent instance.

Jobs that only collect partial tool results emit no spans. A job that fails or is superseded exports its own
spans with an outcome (`failed`, `superseded`, `bpmn_error`) and `error.type`, so the model calls and tokens
it spent stay visible.

## Attributes

The vocabulary is pinned to a commit of the upstream
[semantic-conventions-genai](https://github.com/open-telemetry/semantic-conventions-genai) repository, which is
in Development status: names may change upstream, and `GenAiAttributes` is the single place to update. Camunda
specific attributes use the `camunda.*` namespace (process instance, element, job, tenant, agent instance,
job outcome, attempt, tool outcome).

- Token usage follows the semantic conventions: `gen_ai.usage.input_tokens` includes cached tokens, and the
  cache read and write counts are subsets of it. Reasoning tokens are a subset of the output tokens. The cache
  write count is also emitted under its legacy name, because Phoenix reads only that one.
- Message content (system instructions, input and output messages, tool definitions, tool arguments and
  results) is recorded only with `capture-content=true`, as JSON strings. Documents appear as references, never
  as bytes.
- Error spans carry the error status and `error.type`. Exception messages and status descriptions appear only
  with content capture on, because provider error bodies can contain prompt text.
- The Anthropic SDK's own OpenTelemetry spans and trace-context headers are turned off, so they cannot
  duplicate the agent's `chat` spans.

## Data handling

Every registered lifecycle listener receives complete event data: prompts, tool arguments and tool results.
With content capture on, that data reaches your tracing backend. Enable it only where that is acceptable, and
note that the PoC has no redaction hook and no size limit for captured content.

## Limitations

- Spans are exported at most once. If a worker crashes after the engine accepted a job's completion, that job's
  `invoke_agent` and tool spans are lost. If the crashed job started the run, its anchor span is lost as well,
  and the run's later spans point at a root that never arrives.
- Terminating the process or the sub-process while tools run ends the trace without a closing marker.
- A failed tool is not distinguishable from a successful one: `execute_tool` shows `completed` or
  `cancelled`, and a tool incident produces no span. A structured tool error marker is planned.
- Backends that reject old spans (Datadog accepts spans up to about 18 hours old) drop the long tool spans.
- The tool discovery calls of gateway tools get no `execute_tool` spans.
- Tracing state lives in the agent context under `lifecycle:otel-tracing` and is written only by the jobs
  that persist the context. When it is lost, the next job starts a new trace.

## Lifecycle listener API

To build your own observer, implement `AgentLifecycleListener` from `aiagent-api` (package
`aiagent.api.lifecycle`) and register it as a Spring bean. It is marked `@Experimental`: it may change in any
release without a migration path. The callback contracts are on `AgentJobObserver`: notifications are failure
isolated and cannot change the job, state contributions are stored in the agent context only on the two paths
that persist it, and model rounds are always closed in pairs.
