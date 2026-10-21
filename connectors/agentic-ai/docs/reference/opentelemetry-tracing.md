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
  ends with the turn whose model response has no tool calls. The next input starts a new trace. Runs are not
  linked to each other: the session groups the runs of one agent instance.
- **Anchor span** `<element id> run` (internal, no GenAI operation): the root of the run's trace, a marker
  without duration. With
  `capture-content` on, the name continues with the start of the run input, `<element id> run: <first 60
  characters of the input>`, and the span carries the full input as `input.value` and
  `openinference.span.kind=AGENT`. The job that starts the run exports the anchor with its start and end both
  at the start of that job, the instant its first `invoke_agent` span starts, so it shows as a point. It
  carries only what is known at the start of the run (name, input, trigger, session); the outcome and the
  error of a job are on the `invoke_agent` span.
- **`invoke_agent {element id}`** per job that does agent work (a tool discovery dispatch or a turn), with the
  job's real duration.
- **`chat {model}`** per model call, including provider continuation rounds: provider, models, token usage,
  finish reason, response id. It is exported as soon as the model call ends.
- **`execute_tool {tool}`** per tool result a turn consumes: from the dispatch of the tool call to the engine's
  completion time, so a user task that takes days appears as a long span. It is a child of the `invoke_agent`
  span of the job that issued the call (for a gateway tool discovery call, the job that dispatched the
  discovery): it starts at the time that job recorded its turn, shortly before the engine starts the tool, and
  ends when the engine completes the tool, after its parent ended. The job that consumes the results exports
  these spans after the engine accepted its completion.
- **Session.** The agent instance key (prefixed `<cluster-id>:` if configured) is both `session.id` and
  `gen_ai.conversation.id` on every span, so backends group all runs of one agent instance.

### Which jobs export spans

A job creates its spans when it reports the start of a turn or the dispatch of a tool discovery, so a job
that did no agent work leaves no trace of itself. Both events are reported after the lease-fenced write of the
agent instance history succeeded, so they identify a job that held the lease at that point:

- A job that runs a turn creates its spans when its turn starts. The anchor of a run that this job starts is
  exported at that point.
- A job that dispatches gateway tool discovery creates its `invoke_agent` span (job kind `tool_discovery`) at
  the dispatch. When it starts the run, its anchor is exported at the same point.
- A conversation job that completes without a turn exports nothing. That covers a job that only collects
  partial tool results and a job that finds no input.
- A job that fails before its turn starts, for example because the model call limit is reached, reports no
  turn start. It exports a failed `invoke_agent` span with status Error and `error.type`, and the anchor when
  it starts the run; the anchor carries no status.
- A superseded duplicate job that is rejected by the fenced write exports nothing, so the job that took over
  is the only one that exports the spans, the anchor included. A job that is superseded after its turn
  started, or whose completion the engine ignores, exports its spans with outcome `superseded`,
  `error.type=job_superseded`, and the status is left unset, so the model calls and tokens it spent stay
  visible.
- A job that fails or ends with a BPMN error exports its own spans with the outcome (`failed`,
  `bpmn_error`) and `error.type`.

### The run state

A later job attaches its spans to the run's anchor, which was exported by an earlier job, possibly days
before. The tracing listener keeps what it needs in the agent context, in the listener state slot
`lifecycle:otel-tracing`: the trace id and anchor span id, the sampling decision, whether the run is open or
closed, the turn iteration key, and the span id of the `invoke_agent` span of the job that wrote the state,
which is the job that issued the tool calls that a later job consumes, and, when the job issued tool calls, the
time it recorded its turn as epoch milliseconds (`issuedAt`). It is written by the two kinds of job that
persist the agent context (tool discovery dispatch and turn completion), and the entry stays within the 4096
byte limit for listener state.

A job continues the run when the stored run is open and its iteration key equals the agent context's last
iteration key. Otherwise it starts a new run: when no readable state exists, when the last turn gave the final
answer, or when the state is stale. A job that starts a new run does not use the issuer of the earlier state.
A state without a valid issuer span id, such as one written before the field existed, makes the tool spans
children of the consuming job's `invoke_agent` span. A state without an issue time makes the tool spans start
at the start of the consuming job. The tool spans never start after they end: the start is the earlier of the
two, because the clock of the worker that recorded the issue time can differ from the clock of the engine that
recorded the completion. The agent instance key identifies the execution, not the process instance
or element instance: a job from another ad-hoc sub-process element instance that re-enters the same agent
context continues the open run, or starts a new one if the previous run closed.

## Reading the traces

**One agent instance is one session.** Every span carries `session.id` and `gen_ai.conversation.id`, set to the
agent instance key that Operate shows (prefixed `<cluster-id>:` when `cluster-id` is set). Search the backend
for that value to find all runs of the agent instance.

**One run is one trace.** Open a trace to see the anchor marker at the root, one `invoke_agent` span for each job that
did agent work, and under each of those the `chat` spans of its model rounds and the `execute_tool` spans of the
tool calls it issued, which are exported by the job that consumes their results. Attributes such as `camunda.process_instance.key`, `camunda.element_instance.key` and
`camunda.job.key` tell which activation produced a span.

**Token totals.** Token usage is set only on `chat` spans, so summing the `chat` spans does not count a
call twice. The sum over a trace is the token usage of the run, and the sum over a session is the token usage
of the agent instance. The totals equal the token usage that the agent instance history reports when all
categories are summed: input, output, cache read and cache write tokens. They differ only in where the cache
and reasoning tokens are shown. The semantic conventions count cached tokens inside `gen_ai.usage.input_tokens`,
so on a span the input tokens are the agent instance's `inputTokens` plus its cache read and cache creation
tokens, whereas `inputTokens` of the agent instance history excludes the cache tokens. The output tokens are the
same in both and include the reasoning tokens. Backends that split the cache read, cache write and reasoning
tokens off the input and output tokens, such as Langfuse, show the agent instance's own split and price the cache
tokens separately; no cache amount is counted twice. A model call counts as soon as its `chat` span is exported,
so a call whose turn was rejected afterwards, because the job lost its lease, is still included.

**Run input and final answer.** With `capture-content` on:

- The anchor carries the run input: its name, `input.value`, and `openinference.span.kind=AGENT`. It is the text
  of the last user message among the turn's input messages. A run that starts with a tool discovery dispatch
  has no input at that point, so its anchor is named `<element id> run` and has no `input.value`.
- The final answer is on the `invoke_agent` span of the last job of the run, as `gen_ai.output.messages`, and on
  the `chat` span of the last model round.
- Nothing is set as the output of the anchor.

What the two backends make of this:

- **Langfuse** (version 4, events-only storage) stores the input and output of the root observation as the
  trace's input and output, and does not store `langfuse.trace.input` or `langfuse.trace.output`. The trace
  input is therefore the anchor's run input, and the trace output stays empty because the anchor carries no
  output.
- **Arize Phoenix** reads `input.value` and `output.value` of the root span for the turns of a session. The
  anchor sets `input.value`; it sets no `output.value`.
- Neither backend stores or renders OpenTelemetry span links, which is why runs are grouped by session and not
  linked.

## Attributes

The vocabulary is pinned to a commit of the upstream
[semantic-conventions-genai](https://github.com/open-telemetry/semantic-conventions-genai) repository, which is
in Development status: names may change upstream, and `GenAiAttributes` is the single place to update. Camunda
specific attributes use the `camunda.*` namespace (process definition, process instance, element and element
instance, BPMN process id, tenant, agent instance, job key, job kind, job outcome, run trigger, tool outcome,
tool start time source).

- Token usage follows the semantic conventions: `gen_ai.usage.input_tokens` includes cached tokens, and the
  cache read and write counts are subsets of it. Reasoning tokens are a subset of the output tokens. The cache
  write count is emitted as `gen_ai.usage.cache_write.input_tokens`.
- Message content (system instructions, input and output messages, tool definitions, tool arguments and
  results, the tool description) is recorded only with `capture-content=true`, as JSON strings or text. Documents
  appear as references, never as bytes.
- Job outcomes are `completed`, `failed`, `superseded` and `bpmn_error`. A tool result has the outcome
  `completed` or `cancelled`. The run trigger on the anchor is `initial` when no earlier run state exists and
  `follow_up` when the run starts after an earlier one.
- Failed spans carry the error status and `error.type`. `error.type` is the canonical class name of the first
  exception in the cause chain that is not a connector exception, which is normally the provider SDK
  exception, and for a model rejection the rejection class (content filter, context window, guardrail). When
  the chain holds only connector exceptions, it is the error code, and otherwise the class name of the
  failure. A BPMN error uses its error code, and a job that is superseded uses `job_superseded`. A model call
  that is still open when its job ends is closed with finish reason `error` and the `error.type` of the
  ending cause, or `job_abandoned` when the job completes. Exception messages and status descriptions appear only with content capture on, because
  provider error bodies can contain prompt text.
- The Anthropic SDK's own OpenTelemetry spans and trace-context headers are turned off, so they cannot
  duplicate the agent's `chat` spans. This happens only when
  `camunda.connector.agenticai.aiagent.tracing.enabled=true`; with tracing disabled the SDK keeps its default
  behavior.

## Data handling

Every registered lifecycle listener receives complete event data: prompts, tool arguments and tool results.
With content capture on, that data reaches your tracing backend. Enable it only where that is acceptable, and
note that the PoC has no redaction hook and no size limit for captured content.

## Known limitations

- **Tool span timing.** The start of an `execute_tool` span is approximate: it is the time the issuing job
  recorded its turn, shortly before the engine starts the tool, so it precedes the activation of the tool by
  the time it took to store the conversation and complete the job. The end is the engine's completion time of
  the tool, which is exact; when the result has none, the end is the end of the consuming job. When worker and
  engine clocks differ, a start that would lie after the end is set to the end, and a span of a state that has
  no recorded issue time starts at the start of the consuming job. The span outlives its parent, the `invoke_agent` span of the issuing job, because the tool runs in the engine after
  that job ended; the waiting time of a tool, for example a human task, is the duration of its span.
- **Tool outcome.** Every tool result that is not marked as interrupted shows as `completed`, whatever its
  content, so a tool that failed or was rejected looks like a tool that succeeded. A tool incident produces no
  span, because no result arrives.
- **Gateway tool discovery.** The discovery calls of gateway tools get no `execute_tool` spans.
- **Untraced work.** Failures during agent initialization, before the job starts, and jobs that complete
  without a turn are not traced.
- **Run input of the first run.** A run that starts with a tool discovery dispatch has no input on its anchor,
  because no turn exists yet.
- **At most once.** A runtime failure after the handler returned, such as an oversized result, is reported to
  the listeners and exports the job's spans as failed. A worker that crashes after the engine accepted a job's
  completion loses that job's `invoke_agent` and tool spans. If the crashed job started the run, its anchor
  span is lost as well, and the run's later spans point at a root that never arrives.
- **Termination.** Terminating the process or the sub-process while tools run ends the trace without a closing
  marker.
- **No span links.** Runs are not linked to each other; the session is the only connection between them.
- **No trace-level output.** The anchor sets no output, so a backend that shows the output of the root span has
  nothing to show; the final answer is on the last `invoke_agent` and `chat` spans.
- **Late spans.** Backends that reject old spans (Datadog accepts spans up to about 18 hours old) drop the long
  tool spans.
- **Cache-write tokens in Arize Phoenix.** Phoenix may not show cache-write tokens, because it reads an older
  attribute name for them; the spans carry the name that the conventions define.
- **Run root is a start marker.** The run's root span is a zero-duration marker at the start of the run, because
  the module cannot extend a span that an earlier job exported. The extent of a run is shown by the child
  spans and by the trace window, so a backend shows the root bar as a point, and its trace latency comes from
  the span window and not from the root. Trace-level output is not set (see *No trace-level output*).
- **Lost state.** Tracing state lives in the agent context under `lifecycle:otel-tracing` and is written only by
  the jobs that persist the context. When it is lost or cannot be read, the next job starts a new trace.

### Differences from the GenAI conventions

The spans follow the GenAI semantic conventions, with a few differences that you notice in the backend. The
full list with the reasons is in
[ADR 019](../adr/019-opentelemetry-genai-tracing.md#conformance-with-the-genai-conventions).

- The root of a trace is the anchor, a start marker without duration, with no GenAI operation and no output. The final answer is on the
  last `invoke_agent` and `chat` spans (see *No trace-level output* above).
- A run has one `invoke_agent` span for each job that did agent work, not one for the whole run.
- `gen_ai.agent.name` is the BPMN element id of the agent, and there is no `gen_ai.agent.id`.
- The start of a tool span is approximate, and a tool that failed shows as completed (see *Tool span timing*
  and *Tool outcome* above).
- Runs are connected through the session only, with no span links (see *No span links* above).

## Lifecycle listener API

To build your own observer, implement `AgentLifecycleListener` from `aiagent-api` (package
`aiagent.api.lifecycle`) and register it as a Spring bean; the callbacks and the events are described in
[Observe agent execution](ai-agent.md#254-observe-agent-execution). It is marked `@Experimental`: it may change
in any release without a migration path. The callback contracts are on `AgentJobObserver`: events are built only
when a listener exists, notifications are failure isolated and cannot change the job, state contributions are
stored in the agent context only on the two paths that persist it, and model rounds are always closed in pairs.
