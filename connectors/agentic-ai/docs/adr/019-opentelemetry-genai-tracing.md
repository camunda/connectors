# Trace AI Agent runs with OpenTelemetry GenAI spans

* Deciders: Agentic AI Team
* Date: Oct 19, 2026

## Status

**Proposed**. Delivered as a proof of concept first. The trace shape is validated against LLM observability backends
before the production hardening is planned, and the topology can change if the
validation shows it cannot work.

## Context and Problem Statement

Customers ask to see Camunda AI Agent executions in LLM observability backends (for example Langfuse or
Arize Phoenix), the tools their AI teams already use for tracing and evaluation, and the team needs the same view to
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
the customers named: some require each span to be exported once and complete, and some keep the first
write for a span id.

Reconstructing traces from the history records, on the cluster side, was assessed as an alternative.
The records lack data the traces need: model call start times, stop reasons, response models, request
parameters, model rejections and errors, failed and retried attempts, tool dispatch times, and trace
identities. Closing those gaps needs engine schema changes. The question is how the AI Agent should
emit traces that these backends render correctly, without enabling OpenTelemetry in every runtime.

## Decision Drivers

* **LLM observability backends are the acceptance bar**: strict OpenTelemetry GenAI semantic
  conventions, so the backends render span types, model input and output, tool calls, tokens,
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
4. Emit one trace per run with a zero-duration run-start marker as root span that the run-starting job
   exports at the start of the run, and later jobs attach their spans to it.

## Decision Outcome

Chosen option: **Option 4 — one trace per run with a run-start marker as root, emitted by a
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
  The next input starts a new run. The run-starting job exports an anchor root span at once, as a marker
  without duration at the start of the run. Later jobs attach their spans to the anchor through the trace identity kept in the consumer's
  state slot (ADR 018 D3), and inherit its sampling decision, so a run is all-in or all-out. Trace and span
  identities are assigned by the OpenTelemetry SDK. The state exists so that a later job can reattach to
  the trace of a run that an earlier job started.
  Runs are not linked to each other: the session groups the runs of one agent instance, and the
  target backends neither store nor render span links. A job continues the run when the stored state is open and
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
    It is a marker without duration: it starts and ends at the start of the run-starting job, and it carries
    only what is known at the start of the run (name, input, trigger, identities). The outcome, the error, and
    the final answer are on the `invoke_agent` and `chat` spans. The extent of a run is shown by its child
    spans and by the trace window.
  * **`invoke_agent {element id}`** spans cover one job doing agent work (tool discovery or a turn), with
    the job's real duration.
  * **`chat {model}`** spans cover every model call round, including provider continuation rounds, with
    the provider, requested and response model, token usage, finish reason, and response id. Token usage is
    set only on `chat` spans, so that summing over a trace or a session counts every model call once and
    covers the same calls as the metrics of the agent instance. Input tokens include the cache read and
    cache write tokens, as the conventions define them, whereas the agent instance metrics report the input
    tokens apart from their cache counts. The totals of a trace and a session therefore equal the sum of all
    token categories of the agent instance metrics (input, output, cache read and cache write); only the
    place where cache and reasoning tokens are displayed differs.
  * **`execute_tool {tool}`** spans cover one consumed tool result, from the time the issuing job recorded
    its turn to the engine's completion of the tool, and are emitted by the job that consumes the result.
    Each is a child of the `invoke_agent` span of the job that issued the call (for a gateway tool
    discovery call, the job that dispatched the discovery), so it starts inside its parent and ends after
    it, the shape of an asynchronous operation. The issuing job records the time when it persists its run
    state at the end of the turn, and the consuming job starts the span at that time. The start never
    lies after the end, because the clocks of worker and engine can differ, and a state without a
    recorded time starts the span at the start of the consuming job. When the state does not name the
    issuing job, the span is a child of the `invoke_agent` span of the consuming job.

* **D4 — The session is the agent instance, and only Sub-process agents with an agent instance are
  traced.** Every span carries the agent instance key, prefixed with an operator-configured cluster id when
  one is set, as both the conversation id and the session id. It is the same for every job and every run of
  the agent instance, and links to Operate. The prefix prevents unrelated sessions from merging when several
  clusters, whose keys restart at zero, export to one backend project. Agents without an agent instance and
  the Task flavor emit no spans.

* **D5 — Every model call is visible and each tool execution is recorded once.** A job creates its spans
  when it reports the start of a turn or the dispatch of a tool discovery. Both are reported only after the
  lease-fenced write of the agent instance history succeeded, so they identify a job that held the lease at
  that point. A job that completes without either, such as a partial-results job, exports nothing. A failed
  activation ends its `invoke_agent` span with its outcome and error type, and exports the anchor when it
  started the run, and its model calls and token usage remain visible. This includes a job that fails before
  its turn starts (for example at the model call limit), which reports no turn start. A superseded activation
  is not a failure of the agent. The fenced write rejects a superseded duplicate before it reports a turn
  start, so a duplicate that fails there exports nothing, not even an anchor, and the activation that took
  over is the only one that exports the spans. The anchor of a run is exported when the run-starting job
  reports its turn start or tool discovery dispatch, or fails. A job that is
  superseded after its turn started, or whose completion the engine ignores, ends its spans with outcome
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
  | Limit hit, other agent failure | `invoke_agent` | Error; error code, or the class name of the failure |
  | BPMN error raised by the agent | `invoke_agent` | Error; outcome `bpmn_error`; the BPMN error code |
  | Failure of the job completion in the runtime | `invoke_agent` | Error; outcome `failed`; the class of the cause |
  | Job superseded, or completion ignored by the engine | `invoke_agent` | Outcome `superseded`; `job_superseded`; status unset; only if the job reported a turn start or a tool discovery dispatch |
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
  is a one-file change. Token usage follows
  the conventions' totals rule: input tokens include cached tokens, with cache and reasoning counts as
  subsets. The mechanism (enablement, configuration, trace shape, failure semantics) is supported;
  attribute names are documented as following upstream.

* **D10 — A proof of concept validates the topology first.** The proof of concept covers the Sub-process
  flavor and validates the shape in two local LLM observability backends. Exit criteria: backends render a run
  whose child spans arrive hours or days after the anchor, including tool spans that end long after their
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
| 1, Mon 09:00:00 | Activation, tool discovery dispatched | Yes | Anchor (marker without duration), `invoke_agent` (discovery) | Open, new trace T1, issuer job 1 |
| 2, Mon 09:00:01 | Discovery results, first turn | Yes | `invoke_agent`, `chat` round 1 (4 s, tokens, finish reason tool call) | Open, issuer job 2, issued 09:00:06 |
| 3, Mon 09:00:08 | Tool A completes | No (deferred) | None | Unchanged |
| 4, Wed 09:00 | Tool B completes after two days | Yes | `invoke_agent`, `chat` round 1 (final answer), `execute_tool` A (Mon 09:00:06 to 09:00:08), `execute_tool` B (Mon 09:00:06 to Wed 09:00), both below job 2's `invoke_agent` | Closed |
| 5, Wed 09:30 | Event message | Yes | New anchor, `invoke_agent`, `chat` | Open, new trace T2, same session |

```text
Session prod-eu:2251799813685260
Trace T1  "travel_helper run" (run-start marker, no duration)
├─ invoke_agent travel_helper  job 1 (tool discovery)
├─ invoke_agent travel_helper  job 2
│  ├─ chat  round 1  (tokens, finish=tool call)
│  ├─ execute_tool  Search flights   (Mon 09:00:06 → 09:00:08)
│  └─ execute_tool  Manager approval (Mon 09:00:06 → Wed 09:00)
└─ invoke_agent travel_helper  job 4 (Wed)
   └─ chat  round 1  (finish=stop, final answer)
Trace T2  "travel_helper run"  (same session, no link)
└─ invoke_agent travel_helper  job 5 → chat
```

`chat` spans are exported when each model round ends. The `execute_tool` spans are exported by job 4, the job
that consumes the results, but they hang below job 2, which issued the calls, and start after it started.
`invoke_agent` is exported when the job ends: after the engine accepted the job completion, or when the job fails or its completion is
rejected. The anchor is exported when the run-starting job reports its turn start or tool discovery dispatch. If job 4's first attempt fails after its model call, it exports `invoke_agent` (failed) and the
`chat` span with its tokens and persists nothing; the retry exports a second `invoke_agent` and `chat` and
the two `execute_tool` spans once. If job 1's first attempt fails, it exports its own failed trace in the
same session, and the retry starts the run's trace. A duplicate activation of job 4 that is superseded
by the fenced write of its turn start exports nothing.

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
| Issuer span id | The span id of the `invoke_agent` span of the job that wrote the state, which is the job that issued the tool calls the next consuming job reports. It is the parent of their `execute_tool` spans. Optional: a missing or invalid id, or a state of a run that ended, makes the tool spans children of the consuming job's `invoke_agent` span. |
| Issued at | The time, in epoch milliseconds, at which the job that issued tool calls persisted its state. It is the start of the `execute_tool` spans of the next consuming job, limited to their end. Written only by a turn that issues tool calls, so a final answer and a tool discovery dispatch carry none, and the later jobs that consume partial results keep it. Optional: a missing or invalid value, or a state of a run that ended, makes the tool spans start at the start of the consuming job. |

The session identifier, the process instance key, span identities of the other span kinds, attributes,
content, the completion times of tools, and the lease token are not stored. A closed entry stays until the next run replaces it.

### Conformance with the GenAI conventions

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

**Deviations.** Where the AI Agent differs from the conventions, and why:

| Area | Conventions | AI Agent | Reason |
|---|---|---|---|
| Root of a run | One `invoke_agent` span covers an agent invocation, and no wrapper root span is defined | A synthetic anchor `<element id> run`, a zero-duration marker at the start of the run, without `gen_ai.operation.name` and without an output; the final answer is on the last `invoke_agent` span and on the `chat` span of the last round | A module that depends only on the OpenTelemetry API cannot export a span under an id it handed out earlier and cannot extend a span it exported, so the root cannot cover the run: it marks the start. The final answer is not known to it. A GenAI operation on it would distort the durations of `invoke_agent` |
| Agent invocations | One `invoke_agent` span per invocation | One `invoke_agent` span per job, so a run has several | A run is spread over several stateless jobs, and each span covers one job's real duration |
| Tool span timing | The span covers the real execution, and its attributes are set at creation | The span is created by the job that consumes the result, with its name and attributes, as a child of the `invoke_agent` span of the job that issued the call, with an explicit start that the issuing job recorded when it persisted its turn; the end is the engine's completion time | The connector sees neither the start nor the end of a tool, because the engine runs it. The start is early by the time it takes to store the conversation and complete the job, and the span outlives its parent |
| Tool failures | A failed operation has error status and `error.type` | Not distinguishable: a tool result shows `completed`, or `cancelled` when interrupted, and a tool incident has no span | A tool result carries no structured error signal today (see Follow-up) |
| Agent name | `gen_ai.agent.name` is the name of the agent, and a stable agent id is recommended only for hosted agents | `gen_ai.agent.name` is the BPMN element id, and there is no `gen_ai.agent.id` | The element id is the stable name in the process model. Agent instance keys are transient, so they are no agent id |
| Conversation id | Set from an identifier that is readily available | The agent instance key, prefixed with the operator-set cluster id when one is configured | Conforms: the engine assigns the key, and it links to Operate. The prefix keeps clusters apart in one backend project |
| Content | Opt-in | Opt-in through one switch for all content | Conforms, with a coarser granularity than per attribute; redaction is a production-milestone item |
| Token usage | Usage attributes are defined for model calls | Only on `chat` spans, never on `invoke_agent` or the anchor | Sums over a trace or session count each call once |
| Span links | Not addressed | None. Runs are connected by the session only | The target backends neither store nor render span links |
| Session | `gen_ai.conversation.id` | `session.id` is set next to it with the same value | Some tools read `session.id` and others the conversation id, so both are set |

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
* The token totals of a run and of a session equal the sum of all token categories that the agent instance
  metrics report, so the backend and Operate agree on the spend.
* The default application and the AI Agent connector take on no OpenTelemetry SDK; customers opt in
  with Spring's standard configuration.
* The same lifecycle events serve other consumers.

### Negative Consequences

* Customers on the stock image must add and version-match the OpenTelemetry and Spring OpenTelemetry
  libraries, and exclude the Observation bridge to keep other connectors untraced.
* The root of a run is a marker without duration, so a backend shows it as a point, and the latency columns
  of a trace come from the span window and not from the root. Tool spans can end days after their
  `invoke_agent` parent, which backends may render awkwardly (an exit criterion of the proof of concept).
* The start of a tool span is approximate, and the anchor sets no output, so a backend that shows the output
  of the root span has nothing to show for a run.
* A run that starts with a tool discovery dispatch has no input on its anchor.
* The recorded dispatch time describes tools that run as BPMN activities. The connector runtime will later
  answer some tools itself inside the job: they are not BPMN activities, their start and end are measured in
  the process, and they will need their own lifecycle event with those timings, emitted by the job that runs
  them. The recorded time does not apply to them.
* A worker crash after a job commits loses that job's spans, and a lost run-starting job leaves later
  spans pointing at a root that never arrives. A lost issuing job does the same to the tool spans that
  answer its calls.
* A run that ends because the process or the sub-process is terminated has no closing marker.
* Backends with an ingest window shorter than a tool's duration drop the long `execute_tool` spans; they
  are not acceptance backends.
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

### Option 4: One trace per run with a run-start marker as root (chosen)

* Good, because the run stays in one trace, nothing is held open across jobs, and sampling is
  consistent.
* Good, because a cancelled run keeps its anchor and the spans of completed jobs.
* Bad, because the root is a point and not the run's extent, and it depends on how backends render late
  children.

## Alternatives for the run root

The root of a run's trace could be built in several ways. The criteria were: token totals per run that match
the agent instance metrics, a dependency on the OpenTelemetry API only, support for the OpenTelemetry Java agent
and any SDK configuration, spans visible while a run is in progress, and simplicity.

1. **Start marker (chosen).** The run-starting job exports a root span without duration at the start of the
   run. It needs only the API, works with the Java agent and any SDK setup, and the spans of a run appear live.
   The extent of a run is visible through its child spans and the trace window. The costs are that the root
   shows as a point and that it carries no trace-level output: backends that read the trace input and output
   from the root span, or the turns of a session from the input and output of its root, show the run input but
   not the answer, which is on the closing `invoke_agent` and `chat` spans.
2. **Deferred root exported when the run closes, with ids decided at run start.** It gives the correct root
   duration, input, and output in the backends. It needs the OpenTelemetry SDK, either a custom id generator
   with a thread-local override or a hand-built span record pushed to an exporter, and in Spring Boot a
   tracer-provider customizer bean. That is ignored when the application defines its own tracer provider,
   competes for the single id generator slot, and does not work with the OpenTelemetry Java agent, whose SDK is
   isolated from the application classpath, so a second export pipeline would be needed. The root is also
   missing while a run is open, and forever for an abandoned run (a terminated process instance, an unresolved
   incident). Rejected as too invasive and brittle for customers.
3. **No anchor, the `invoke_agent` span of the first job as root.** It saves one span, but the root is as
   short as the first job, later jobs are nested under a job they do not run inside, a backend shows the
   output of the first job (the tool call message) as the trace output, the trace name is generic, and the
   session turns show no input or output. The hierarchy is worse.
4. **One trace per job.** Each root has its true duration and no synthetic root exists, but a run is split
   over several traces, so there is no waterfall or token and cost total per run, and the waiting time of a
   tool falls outside any trace window.
5. **One trace per agent instance with a long-lived root.** The root cannot be closed, because the end of the
   instance is not observable by the connector, the trace grows without bound, and runs cannot be told apart.
6. **Re-exporting the first anchor span with the same ids at the end of the run.** Backends treat a second
   export of a span id differently: some keep the first write when the exports are seconds apart, others
   replace or duplicate it depending on timing. It is unreliable.
7. **Span links between runs instead of the shared session.** The target backends neither store nor render
   links, and the session identifier already groups the runs.
8. **Rebuilding the whole trace at the end of the run from the conversation or the agent instance history.**
   The conversation holds no per-round usage and timing, live visibility is lost, failed model calls and
   per-job detail are lost, and every span is lost when the closing job fails. It is a much larger rewrite.
9. **Keeping the span object open in process memory until the run closes.** It breaks as soon as another
   worker or a restart handles the closing job, and it leaks for an abandoned run.

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
