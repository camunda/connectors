# Trace AI Agent runs with OpenTelemetry GenAI spans

* Deciders: Agentic AI Team
* Date: Oct 19, 2026

## Status

**Proposed**. Delivered as a proof of concept first. The trace shape is validated against Langfuse and
Arize Phoenix before the production hardening is planned, and the topology can change if the
validation shows it cannot work.

## Context and Problem Statement

Customers ask to see Camunda AI Agent executions in Arize Phoenix and Langfuse, the LLM observability
tools their AI teams already use for tracing and evaluation, and the team needs the same view to
evaluate agent behavior. Today the only structured record is the agent instance history shown in
Operate, which is Camunda-internal and records only the committed path of an agent. Camunda has no
trace propagation through jobs (camunda/camunda#9742 "As a Zeebe user, I want to see Open Telemetry
support for Zeebe" and camunda/camunda#25864 "Introduce minimal job worker tracing" are open), and
generic OpenTelemetry Java agent instrumentation sees disconnected HTTP and gRPC calls but nothing
about agents, turns, models, tokens, or tools.

The execution model makes naive tracing wrong. An agent run is spread over many stateless job
executions, possibly on different runtime instances. Tool calls are BPMN activities that can take
days (user tasks). Parallel tool results produce partial-result jobs that do no agent work. Jobs fail,
retry, time out, and are superseded. A design that holds spans open, re-emits spans, or assumes one
process sees the whole run either loses data or produces duplicates and broken trees in the backends
the customers named: Langfuse requires each span to be exported once and complete, and Phoenix keeps
the first write for a span id.

Reconstructing traces from the history records, on the cluster side, was assessed as an alternative.
The records lack data the traces need: model call start times, stop reasons, response models, request
parameters, model rejections and errors, failed and retried attempts, tool dispatch times, and trace
identities. Closing those gaps needs engine schema changes. The question is how the AI Agent should
emit traces that these backends render correctly, without enabling OpenTelemetry in every runtime.

## Decision Drivers

* **LLM observability backends are the acceptance bar**: strict OpenTelemetry GenAI semantic
  conventions, so Langfuse and Phoenix render span types, model input and output, tool calls, tokens,
  and sessions. A valid span tree for generic backends follows from the same shape.
* **The run is the evaluation unit**: backends attach scores to a trace, so one trace should be one
  agent run from an input to the final answer.
* **Every model call visible, each tool execution recorded once**: failed activations, and superseded
  ones that called the model, keep their spans so wasted spend shows; spans are never emitted twice.
* **Best-effort telemetry**: an export failure or slow backend never fails a job.
* **The default application does not enable OpenTelemetry**: the customer enables Spring's
  OpenTelemetry support first, and agent tracing is off by default.
* **Self-managed and hybrid runtimes first**: configuration uses the standard runtime properties
  customers already operate.
* **Content is sensitive**: prompts and tool results are captured only on explicit request.
* **Reuse the lifecycle listeners of [ADR 018](018-agent-lifecycle-listeners.md)** instead of tracing
  code inside the request handler.

## Considered Options

1. Reconstruct traces on the cluster side from agent instance history records.
2. Emit one trace per job, grouped only by a session identifier.
3. Emit one trace per run, with a root span emitted after the fact when the run completes.
4. Emit one trace per run with an anchor root span that the run-starting job exports early, and later
   jobs attach their spans to it.

## Decision Outcome

Chosen option: **Option 4 — one trace per run with an early-exported anchor root, emitted by a
tracing consumer of the agent lifecycle listeners**, because it keeps a run in one trace without
holding any span open across jobs, avoids orphaned children when a run is cancelled, and keeps the
sampling decision consistent across jobs.

The decision comprises:

* **D1 — Tracing is a consumer module in the bundle that does nothing until the customer enables
  Spring's OpenTelemetry support.** The `aiagent-otel` module implements the listener interface of ADR
  018 and uses the raw OpenTelemetry API, because agent spans need explicit start timestamps, remote
  parents, and status without exceptions, which the Micrometer Observation API cannot express.
  The module depends only on the OpenTelemetry API. It registers its listener only when agent tracing
  is enabled and an `OpenTelemetry` bean exists, so the default application, which ships no
  OpenTelemetry SDK, exporter, or Spring OpenTelemetry module, is unchanged. A customer adds Spring's
  OpenTelemetry tracing module and an OTLP exporter to their runtime, configures them with Spring's
  standard properties, and excludes the Observation-to-span bridge so that other connectors, inbound
  webhooks, and scheduled work stay untraced.

* **D2 — One trace is one agent run.** A run starts with the first job after an input (first
  activation, user message, or event) and ends with the turn whose model response has no tool calls.
  The next input starts a new run. The run-starting job starts an anchor root span, which it exports when
  it ends. Later jobs attach their spans to the anchor through the trace identity kept in the consumer's
  state slot (ADR 018 D3), and inherit its sampling decision, so a run is all-in or all-out. Trace and span
  identities are assigned by the OpenTelemetry SDK. Persisting the trace identity of a run and reattaching
  later jobs to it is the way the OpenAI Agents SDK resumes a run, and it is the reason the state exists.
  Runs are not linked to each other: the session groups the runs of one agent instance, and neither
  target backend stores or renders span links. A job continues the run when the stored state is open and
  its iteration key equals the last iteration key of the agent context. When the state is missing, closed,
  or stale, for example after a rolling upgrade, the next job starts a new run and succeeds. The stored
  state holds no process instance key: the agent instance identifies the execution, so a job from another
  ad-hoc sub-process element instance that re-enters the same agent context continues the open run, or
  starts a new one if the previous run closed.

* **D3 — Four span kinds.**
  * The **anchor root** is a Camunda-named internal span, `<element id> run`, carrying the agent name,
    session, run trigger, and Camunda identity. With content capture on, its name continues with the first
    60 characters of the run input, and it carries the input as `input.value` and
    `openinference.span.kind=AGENT`, so that a backend that takes the input of a trace from its root shows
    the run input. A run that starts with a tool discovery dispatch has no input yet and keeps the plain
    name. It has no GenAI operation, so it does not distort `invoke_agent` durations or backend type mapping.
    It ends with the job that started the run, so its duration carries no meaning.
  * **`invoke_agent {element id}`** spans cover one job doing agent work (tool discovery or a turn), with
    the job's real duration.
  * **`chat {model}`** spans cover every model call round, including provider continuation rounds, with
    the provider, requested and response model, token usage, finish reason, and response id. Token usage is
    set only on `chat` spans, so that summing over a trace or a session counts every model call once and
    covers the same calls as the metrics of the agent instance.
  * **`execute_tool {tool}`** spans cover one consumed tool result, from the time the tool calls were
    issued to the engine's completion of the tool, and are emitted by the job that consumes the result. The
    issue time is derived from the time-ordered id of the assistant message that issued the calls (ADR
    012); an id that is not time-based yields a zero-length span at completion with a marker attribute.

* **D4 — The session is the agent instance, and only Sub-process agents with an agent instance are
  traced.** Every span carries the agent instance key, prefixed with an operator-configured cluster id when
  one is set, as both the conversation id and the session id. It is the same for every job and every run of
  the agent instance, and links to Operate. The prefix prevents unrelated sessions from merging when several
  clusters, whose keys restart at zero, export to one backend project. Agents without an agent instance and
  the Task flavor emit no spans.

* **D5 — Every model call is visible and each tool execution is recorded once.** A job creates its spans
  lazily: at its first model round, at a tool discovery dispatch, or at a failure. A job that completes
  without a model round, such as a partial-results job, exports nothing. A failed activation, including one
  that fails before its first model round (for example at the model call limit), ends its `invoke_agent`
  span, and the anchor when it started the run, with its outcome and error type, and its model calls and
  token usage remain visible. A superseded activation is not a failure of the agent. The write that fences a
  superseded activation runs before the first model round, so a duplicate that never started a model round
  exports nothing and the activation that took over is the only one that exports the spans. A job that is
  superseded after a model round, or whose completion the engine ignores, ends its spans with outcome
  `superseded` and `error.type=job_superseded` and leaves the status unset, so its wasted spend stays
  visible. `chat` spans are exported when their model call ends, so the token usage of a call counts even
  when the turn it belongs to is rejected afterwards. `execute_tool` spans are emitted only by the job that
  consumes the results and completes successfully, so a retry never repeats them. A worker that crashes after
  committing a job loses that job's spans: the guarantee is at most once, not exactly once.

* **D6 — Errors follow the semantic conventions.** A failed operation gets error status and a
  low-cardinality error type, and no output content is synthesized for a failed operation. The error type
  is the canonical class name of the first exception in the cause chain that is not a connector exception,
  which is the provider SDK exception when the provider wraps it; otherwise the error code, and otherwise
  the class name of the failure. Exception messages and status descriptions are attached only when content
  capture is on, because provider error bodies can contain prompt or tool content.

  | Source | Span | Status and error type |
  |---|---|---|
  | Model rejection (content filter, context window, guardrail) | `chat` and its `invoke_agent` | Error; the class of the rejection |
  | Other provider or transport exception | `chat` and its `invoke_agent` | Error; the class of the provider SDK exception, else the error code |
  | Limit hit, other agent failure | `invoke_agent`, and the anchor of a run-starting job | Error; error code, or the class name of the failure |
  | BPMN error raised by the agent | `invoke_agent`, and the anchor of a run-starting job | Error; outcome `bpmn_error`; the BPMN error code |
  | Failure of the job completion in the runtime | `invoke_agent`, and the anchor of a run-starting job | Error; outcome `failed`; the class of the cause |
  | Job superseded, or completion ignored by the engine | `invoke_agent`, and the anchor of a run-starting job | Outcome `superseded`; `job_superseded`; status unset; only if the job started a model round |
  | Tool completed or cancelled | `execute_tool` | Outcome only; status unset |
  | Tool failed | `execute_tool` | Not distinguishable today (see Follow-up) |
  | Tool incident | none | The result never arrives |

* **D7 — Content capture is off by default and owned by the consumer.** A runtime property turns on
  capture of system instructions, messages, tool definitions, tool descriptions, arguments, results, and
  the run input on the anchor as GenAI content parts and span text; Camunda documents are represented as references with metadata, never as bytes. The capture
  policy is resolved once per job, so a later per-agent override needs no change at the emit sites. A
  redaction hook and a size cap belong in the production milestone.

* **D8 — Provider SDK built-in tracing is disabled when agent tracing is enabled.** The Anthropic SDK
  emits its own GenAI client spans, with token usage and optional content, and propagates trace context to
  the provider, whenever a global OpenTelemetry instance is registered, which a Java agent does and
  Spring's OpenTelemetry module does not. With agent tracing on, those spans would duplicate the generation
  data and token counts of the `chat` spans in the backends and add trace headers to provider requests, so
  the connector builds its Anthropic client with SDK tracing off, and only then. With agent tracing off, the
  SDK keeps its default behavior. The other provider clients carry no OpenTelemetry instrumentation.
  Provider HTTP child spans are a production-milestone decision.

* **D9 — The attribute vocabulary is pinned in one place, and attribute names track upstream.** The
  GenAI conventions are in Development status. One mapping class pinned to a commit of the upstream
  semantic-conventions-genai repository is the only place with attribute names, so an upstream rename
  is a one-file change. Where a backend still reads a name that upstream has renamed (Phoenix and the
  cache write count), both names are emitted. Token usage follows
  the conventions' totals rule: input tokens include cached tokens, with cache and reasoning counts as
  subsets. The mechanism (enablement, configuration, trace shape, failure semantics) is supported;
  attribute names are documented as following upstream.

* **D10 — A proof of concept validates the topology first.** The proof of concept covers the Sub-process
  flavor and validates the shape in local Langfuse and Phoenix. Exit criteria: backends render a run
  whose child spans arrive hours or days after the anchor, including tool spans that start before their
  `invoke_agent` parent; spans stay findable when a parent was never exported (a lost job or a lost
  anchor); runs of one conversation group into one session; span types, model input and output, tool calls,
  tokens, and finish reasons render correctly with content on; a single run can be scored in both backends
  with its triggering input and final answer in view; and the effect of enabling Spring's
  OpenTelemetry support on other connectors is measured with and without the bridge exclusion. If
  trace-level scoring cannot reach the final answer, or the shape renders badly in both backends, the
  topology is revisited.

### Worked example

A Sub-process agent `travel_helper` with an agent instance, session `prod-eu:2251799813685260` (cluster id
set), tracing on, content capture off. Tool A is a script task, tool B is a user task, and gateway tool
discovery is on.

| Job | Trigger | Persists the context | Spans exported | Run state after |
|---|---|---|---|---|
| 1, Mon 09:00:00 | Activation, tool discovery dispatched | Yes | Anchor (0.4 s), `invoke_agent` (discovery) | Open, new trace T1 |
| 2, Mon 09:00:01 | Discovery results, first turn | Yes | `invoke_agent`, `chat` round 1 (4 s, tokens, finish reason tool call) | Open |
| 3, Mon 09:00:08 | Tool A completes | No (deferred) | None | Unchanged |
| 4, Wed 09:00 | Tool B completes after two days | Yes | `invoke_agent`, `chat` round 1 (final answer), `execute_tool` A (Mon 09:00:06 to 09:00:08), `execute_tool` B (Mon 09:00:06 to Wed 09:00) | Closed |
| 5, Wed 09:30 | Event message | Yes | New anchor, `invoke_agent`, `chat` | Open, new trace T2, same session |

```text
Session prod-eu:2251799813685260
Trace T1  "travel_helper run" (anchor)
├─ invoke_agent travel_helper  job 1 (tool discovery)
├─ invoke_agent travel_helper  job 2
│  └─ chat  round 1  (tokens, finish=tool call)
└─ invoke_agent travel_helper  job 4 (Wed)
   ├─ execute_tool  Search flights   (Mon 09:00:06 → 09:00:08)
   ├─ execute_tool  Manager approval (Mon 09:00:06 → Wed 09:00)
   └─ chat  round 1  (finish=stop, final answer)
Trace T2  "travel_helper run"  (same session, no link)
└─ invoke_agent travel_helper  job 5 → chat
```

`chat` spans are exported when each model round ends. `invoke_agent` and the anchor are exported when the
job ends: after the engine accepted the job completion, or when the job fails or its completion is
rejected. If job 4's first attempt fails after its model call, it exports `invoke_agent` (failed) and the
`chat` span with its tokens and persists nothing; the retry exports a second `invoke_agent` and `chat` and
the two `execute_tool` spans once. If job 1's first attempt fails, it exports its own failed trace in the
same session, and the retry starts the run's trace. A duplicate activation of job 4 that is superseded
before its first model round exports nothing.

### What the consumer persists

The tracing state is one small entry in the consumer's namespace of the agent context, written at the
two persist points of ADR 018 and only there:

| Field | Used for |
|---|---|
| Version | Read only state of the current layout; anything else counts as missing. |
| Trace id and anchor span id | Rebuild the remote parent so later jobs' spans join the run's trace. |
| Sampled flag | The sampling decision is made once on the anchor and inherited. |
| Run status (open or closed) | A closed run means the next job starts a new run. |
| Iteration key | Detect stale state after a migration or an older connector: a run continues only when it equals the last iteration key of the agent context. |

The session identifier, the process instance key, span identities of the other span kinds, attributes,
content, tool timing, and the lease token are not stored. A closed entry stays until the next run replaces it.

### Conformance with the GenAI conventions and other frameworks

**What the conventions say.** The GenAI semantic conventions define one span per operation: `chat` for a
model call, `invoke_agent` for an agent invocation (internal kind when the agent runs in the same process),
and `execute_tool` for a tool execution, with `create_agent`, `invoke_workflow`, and `plan` next to them. They
ask that a span covers the duration of its operation and that the attributes important for sampling, such as
the operation, the agent name, and the tool name, are set when the span is created. `gen_ai.conversation.id` is
set when the instrumented library or the application has an identifier readily available, and never from a
generated fallback such as a new UUID or the trace id. Message content, system instructions, tool definitions,
and tool arguments and results are opt-in and off by default. Input token counts include cached tokens, and a
failed operation gets an `error.type` and no synthesized output. The conventions have no notion of trace-level
input or output, and they say nothing about a wrapper root span, span links, or a run that is resumed in a later
process.

**What other frameworks do.** The behavior below is as these frameworks document it at the time of writing.

* **OpenAI Agents SDK**: one trace per run, with a group id that ties the traces of a conversation together. It
  persists the trace id in the run state and reattaches to it when a run is resumed, and it creates a
  synthetic trace root that is not a GenAI operation.
* **Vercel AI SDK**: an `invoke_agent` root span per call that carries the final answer, and `execute_tool`
  spans built from the before and after hooks of a tool.
* **Strands**: an `invoke_agent` root span, the session carried through user attributes, and a span per tool
  built from its before and after events.
* **Pydantic AI**: an `invoke_agent` root span per run with the conversation id. A run that resumes after a
  deferred tool is a separate run, correlated with the first by the conversation id.
* **LangSmith**: one trace per turn, with a thread id on every run of the thread.
* **OpenInference with Arize Phoenix, and Langfuse v4**: the input and output of a trace come from the root
  span or root observation, not from a trace-level attribute.

The pattern is the same everywhere: a trace is one run or turn, a conversation is a session attribute on every
run, and the trace-level input and output are those of the root. The AI Agent follows it with a root that it has
to create itself, because no single process sees a run from the input to the final answer.

**Deviations.** Where the AI Agent differs from the conventions, and why:

| Area | Conventions or common practice | AI Agent | Reason |
|---|---|---|---|
| Root of a run | `invoke_agent` is the root and carries the input and the final answer | A synthetic anchor `<element id> run` without `gen_ai.operation.name` and without an output; the final answer is on the last `invoke_agent` span and on the `chat` span of the last round | The anchor is created by the first job and ends with it, so its duration carries no meaning and the final answer is not known to it. A GenAI operation on it would distort the durations of `invoke_agent` |
| Agent invocations | One `invoke_agent` span per invocation | One `invoke_agent` span per job, so a run has several | A run is spread over several stateless jobs, and each span covers one job's real duration |
| Tool span timing | The span covers the real execution, and its attributes are set at creation | The span is created by the job that consumes the result, with its name and attributes, and an explicit start derived from the id of the assistant message that issued the call; the end is the engine's completion time | The connector sees neither the start nor the end of a tool, because the engine runs it. The start is early by the time it takes to store the conversation and complete the job |
| Tool failures | A failed operation has error status and `error.type` | Not distinguishable: a tool result shows `completed`, or `cancelled` when interrupted, and a tool incident has no span | A tool result carries no structured error signal today (see Follow-up) |
| Agent name | `gen_ai.agent.name` is the name of the agent, and a stable agent id is recommended only for hosted agents | `gen_ai.agent.name` is the BPMN element id, and there is no `gen_ai.agent.id` | The element id is the stable name in the process model. Agent instance keys are transient, so they are no agent id |
| Conversation id | Set from an identifier that is readily available | The agent instance key, prefixed with the operator-set cluster id when one is configured | Conforms: the engine assigns the key, and it links to Operate. The prefix keeps clusters apart in one backend project |
| Content | Opt-in | Opt-in through one switch for all content | Conforms, with a coarser granularity than per attribute; redaction is a production-milestone item |
| Token usage | Usage attributes are defined for model calls | Only on `chat` spans, never on `invoke_agent` or the anchor | Sums over a trace or session count each call once |
| Span links | Not addressed | None. Runs are connected by the session only | Neither target backend stores or renders span links |
| Cache write count | `gen_ai.usage.cache_write.input_tokens` | Emitted next to the legacy `gen_ai.usage.cache_creation.input_tokens` | Phoenix reads only the legacy name |
| Session | `gen_ai.conversation.id` | `session.id` is set next to it with the same value | OpenInference-native tools read `session.id`, and Langfuse reads either attribute |

### Non-goals

* Export from the SaaS-hosted connectors runtime, which needs per-cluster exporter configuration and
  credentials.
* Runtime-wide trace propagation into tool activities that run as other connector jobs.
* A shared event pipeline that also feeds the history API.
* Cluster-side reconstruction of traces from history records, which stays a candidate complement for
  SaaS and external agents once the engine records the missing data.

### Positive Consequences

* A run is one trace that backends can score, grouped into sessions per conversation.
* Failed jobs and the token spend of every model call are visible, and no span is emitted twice.
* The default application and the AI Agent connector take on no OpenTelemetry SDK; customers opt in
  with Spring's standard configuration.
* The same lifecycle events serve other consumers.

### Negative Consequences

* Customers on the stock image must add and version-match the OpenTelemetry and Spring OpenTelemetry
  libraries, and exclude the Observation bridge to keep other connectors untraced.
* The anchor root's duration is meaningless, and tool spans can start days before their `invoke_agent`
  parent, which backends may render awkwardly (an exit criterion of the proof of concept).
* The start of a tool span is approximate, and the anchor sets no output, so a backend that shows the output
  of the root span has nothing to show for a run.
* A run that starts with a tool discovery dispatch has no input on its anchor.
* A worker crash after a job commits loses that job's spans, and a lost run-starting job leaves later
  spans pointing at a root that never arrives.
* A run that ends because the process or the sub-process is terminated has no closing marker.
* Backends with an ingest window shorter than a tool's duration drop the long `execute_tool` spans (for
  example, Datadog rejects spans that started more than 18 hours ago); they are not acceptance
  backends.
* Attribute names may change when the upstream conventions change.

## Pros and Cons of the Options

### Option 1: Reconstruct traces from agent instance history

* Good, because it needs no change in the connector runtime, covers external agents through the same
  API, and sees process cancellation.
* Bad, because the history records lack data the traces need, and closing that gap needs engine schema
  changes owned by another team.

### Option 2: One trace per job, grouped by session

* Good, because each job's spans are emitted once and complete.
* Bad, because a run is scattered over traces, so a backend cannot score a run or show its end-to-end
  shape.

### Option 3: One trace per run, root emitted at completion

* Good, because the trace shows the whole run under a root with its real duration.
* Bad, because the root must be emitted by the last job, so children arrive before their parent, and a
  cancelled run never gets a root and leaves orphaned children.

### Option 4: One trace per run with an early-exported anchor root (chosen)

* Good, because the run stays in one trace, nothing is held open across jobs, and sampling is
  consistent.
* Good, because a cancelled run keeps its anchor and the spans of completed jobs.
* Bad, because the anchor's duration is not the run's duration, and it depends on how backends render
  late children.

## Follow-up

* **Tool error signal**: a failed tool cannot be told from a successful one, because a tool result
  carries only free content and an interruption flag; the MCP error flag is dropped when the gateway
  result is converted, A2A failure states end up as content, and a failure mapped by an error boundary
  is only text. Until it exists, tool spans show only completed and cancelled. It closes with an
  additive error marker on tool results, set by the MCP and A2A gateway handlers and documented for BPMN
  tools, after which the tracing consumer sets error status and error type on tool spans. The marker
  would also let the history API show failed tools.
* **Production milestone**, planned after the proof of concept holds and product has prioritized it:
  a redaction hook and content size cap, AI Agent Task coverage (including its per-pass element
  instances), provider HTTP child spans, trace context reserved in tool call metadata for a later
  runtime feature, tool spans for gateway tool discovery calls, the full attribute mapping, a sampling
  policy, and cluster id auto-detection as the default session prefix.
* **Per-agent content capture override** through an element template property.
