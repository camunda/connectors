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
* **Every attempt visible, each tool execution recorded once**: failed and superseded attempts keep
  their spans so wasted spend shows; spans are never emitted twice.
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
  parents, links, and status without exceptions, which the Micrometer Observation API cannot express.
  The module depends only on the OpenTelemetry API. It registers its listener only when agent tracing
  is enabled and an `OpenTelemetry` bean exists, so the default application, which ships no
  OpenTelemetry SDK, exporter, or Spring OpenTelemetry module, is unchanged. A customer adds Spring's
  OpenTelemetry tracing module and an OTLP exporter to their runtime, configures them with Spring's
  standard properties, and excludes the Observation-to-span bridge so that other connectors, inbound
  webhooks, and scheduled work stay untraced.

* **D2 — One trace is one agent run.** A run starts with the first job after an input (first
  activation, user message, or event) and ends with the turn whose model response has no tool calls.
  The next input starts a new run. The run-starting job starts an anchor root span, which each attempt
  of that job exports when it ends. Later jobs attach their spans to the anchor through the trace
  identity kept in the consumer's state slot (ADR 018 D3), and inherit its sampling decision, so a run
  is all-in or all-out. Trace and span identities are assigned by the OpenTelemetry SDK. Consecutive
  runs of a conversation are linked from the new anchor to the previous one. When the state is missing
  or stale, for example after a rolling upgrade, the next job starts a new run and succeeds.

* **D3 — Four span kinds.**
  * The **anchor root** is a Camunda-named internal span carrying the agent name, session, run trigger,
    and Camunda identity. It has no GenAI operation, so it does not distort `invoke_agent` durations or
    backend type mapping. Its duration is only the first job's and carries no meaning.
  * **`invoke_agent`** spans cover one job doing agent work (tool discovery or a turn), with the job's
    real duration.
  * **`chat`** spans cover every model call round, including provider continuation rounds, with the
    provider, requested and response model, token usage, finish reason, and response id.
  * **`execute_tool`** spans cover one consumed tool result, from the time the tool calls were issued to
    the engine's completion of the tool, and are emitted by the job that consumes the result. The issue
    time is derived from the time-ordered id of the assistant message that issued the calls (ADR 012);
    an id that is not time-based yields a zero-length span at completion with a marker attribute.

* **D4 — The session is the agent instance, and only agents with an agent instance are traced.** Every
  span carries the agent instance key, prefixed with an operator-configured cluster id when one is set,
  as both the conversation id and the session id. It is the same for every attempt and every run of the
  agent instance, and links to Operate. The prefix prevents unrelated sessions from merging when several
  clusters, whose keys restart at zero, export to one backend project. Agents without an agent
  definition emit no spans.

* **D5 — Every attempt is visible and each tool execution is recorded once.** Each job activation
  produces its own spans. A failed, timed-out, or superseded attempt ends its `invoke_agent` span, and
  the anchor when it started the run, with its outcome, and its model calls and token usage remain
  visible. `execute_tool` spans are emitted only by the job that consumes the results and completes
  successfully, so a retry never repeats them. A worker that crashes after committing a job loses that
  job's spans: the guarantee is at most once, not exactly once.

* **D6 — Errors follow the semantic conventions.** A failed operation gets error status and a
  low-cardinality error type, and no output content is synthesized for a failed operation. Exception
  messages and status descriptions are attached only when content capture is on, because provider error
  bodies can contain prompt or tool content.

  | Source | Span | Status and error type |
  |---|---|---|
  | Model rejection (content filter, context window, guardrail) | `chat` and its `invoke_agent` | Error; value derived from the existing error code |
  | Other provider or transport exception | `chat` | Error; HTTP status when known, else exception class name |
  | Limit hit, agent failure, BPMN error raised by the agent | `invoke_agent` | Error; error code or exception class name |
  | Job superseded or completion rejected | `invoke_agent`, and the anchor of a run-starting job | Outcome superseded; error with a job-superseded type so wasted spend stays visible |
  | Tool completed or cancelled | `execute_tool` | Outcome only; status unset |
  | Tool failed | `execute_tool` | Not distinguishable today (see Follow-up) |
  | Tool incident | none | The result never arrives |

* **D7 — Content capture is off by default and owned by the consumer.** A runtime property turns on
  capture of system instructions, messages, tool definitions, arguments, and results as GenAI content
  parts; Camunda documents are represented as references with metadata, never as bytes. The capture
  policy is resolved once per job, so a later per-agent override needs no change at the emit sites. A
  redaction hook and a size cap belong in the production milestone.

* **D8 — Provider SDK built-in tracing is disabled.** The Anthropic SDK emits its own GenAI client
  spans, with token usage and optional content, and propagates trace context to the provider, whenever a
  global OpenTelemetry instance is registered, which a Java agent does and Spring's OpenTelemetry module
  does not. Left on, those spans would duplicate the generation data and token counts of the `chat`
  spans in the backends and add trace headers to provider requests. The connector builds its Anthropic
  client with SDK tracing off. The other provider clients carry no OpenTelemetry instrumentation.
  Provider HTTP child spans are a production-milestone decision.

* **D9 — The attribute vocabulary is pinned in one place, and attribute names track upstream.** The
  GenAI conventions are in Development status. One mapping class pinned to a dated upstream revision is
  the only place with attribute names, so an upstream rename is a one-file change. Token usage follows
  the conventions' totals rule: input tokens include cached tokens, with cache and reasoning counts as
  subsets. The mechanism (enablement, configuration, trace shape, failure semantics) is supported;
  attribute names are documented as following upstream.

* **D10 — A proof of concept validates the topology first.** The proof of concept covers the Sub-process
  flavor and validates the shape in local Langfuse and Phoenix. Exit criteria: backends render a run
  whose child spans arrive hours or days after the anchor, including tool spans that start before their
  `invoke_agent` parent; spans stay findable when a parent was never exported (a lost job or a lost
  anchor); runs of one conversation group into one session; span types, model input and output, tool
  calls, tokens, and finish reasons render correctly with content on; a single run can be scored in both
  backends with its triggering input and final answer in view; and the effect of enabling Spring's
  OpenTelemetry support on other connectors is measured with and without the bridge exclusion. If
  trace-level scoring cannot reach the final answer, or the shape renders badly in both backends, the
  topology is revisited.

### Worked example

A Sub-process agent with an agent instance, session `prod-eu:2251799813685260` (cluster id set),
tracing on, content capture off. Tool A is a script task, tool B is a user task, and gateway tool
discovery is on.

| Job | Trigger | Persists the context | Spans exported | Run state after |
|---|---|---|---|---|
| 1, Mon 09:00:00 | Activation, tool discovery dispatched | Yes | Anchor (0.4 s), `invoke_agent` (discovery) | Open, new trace T1 |
| 2, Mon 09:00:01 | Discovery results, first turn | Yes | `invoke_agent`, `chat` round 1 (4 s, tokens, finish reason tool call) | Open |
| 3, Mon 09:00:08 | Tool A completes | No (deferred) | None | Unchanged |
| 4, Wed 09:00 | Tool B completes after two days | Yes | `invoke_agent`, `chat` round 1 (final answer), `execute_tool` A (Mon 09:00:06 to 09:00:08), `execute_tool` B (Mon 09:00:06 to Wed 09:00) | Closed |
| 5, Wed 09:30 | Event message | Yes | New anchor linked to the first, `invoke_agent`, `chat` | Open, new trace T2, same session |

```text
Session prod-eu:2251799813685260
Trace T1  "Travel helper run" (anchor)
├─ invoke_agent  job 1 (tool discovery)
├─ invoke_agent  job 2
│  └─ chat  round 1  (tokens, finish=tool call)
└─ invoke_agent  job 4 (Wed)
   ├─ execute_tool  Search flights   (Mon 09:00:06 → 09:00:08)
   ├─ execute_tool  Manager approval (Mon 09:00:06 → Wed 09:00)
   └─ chat  round 1  (finish=stop, final answer)
Trace T2  "Travel helper run"  --link--> T1 anchor
└─ invoke_agent  job 5 → chat
```

`chat` spans are exported when each model round ends. `invoke_agent` and the anchor are exported only
after the job completion is confirmed. If job 4's first attempt fails after its model call, it exports
`invoke_agent` (failed) and the `chat` span with its tokens and persists nothing; the retry exports a
second `invoke_agent` and `chat` and the two `execute_tool` spans once. If job 1's first attempt fails,
it exports its own failed trace in the same session, and the retry starts the run's trace.

### What the consumer persists

The tracing state is one small entry in the consumer's namespace of the agent context, written at the
two persist points of ADR 018 and only there:

| Field | Used for |
|---|---|
| Trace id and anchor span id | Rebuild the remote parent so later jobs' spans join the run's trace. |
| Sampled flag | The sampling decision is made once on the anchor and inherited. |
| Run status (open or closed) | A closed run means the next job starts a new run. |
| Iteration key and process instance key | Detect stale state after a migration, an older connector, or another process instance. |

A closed entry keeps the trace and anchor ids so that the next run can link to them before it replaces
the entry. The session identifier, span identities of the other span kinds, attributes, content, tool
timing, and the lease token are not stored.

### Non-goals

* Export from the SaaS-hosted connectors runtime, which needs per-cluster exporter configuration and
  credentials.
* Runtime-wide trace propagation into tool activities that run as other connector jobs.
* A shared event pipeline that also feeds the history API.
* Cluster-side reconstruction of traces from history records, which stays a candidate complement for
  SaaS and external agents once the engine records the missing data.

### Positive Consequences

* A run is one trace that backends can score, grouped into sessions per conversation and linked across
  runs.
* Failed attempts and their token spend are visible, and no span is emitted twice.
* The default application and the AI Agent connector take on no OpenTelemetry SDK; customers opt in
  with Spring's standard configuration.
* The same lifecycle events serve other consumers.

### Negative Consequences

* Customers on the stock image must add and version-match the OpenTelemetry and Spring OpenTelemetry
  libraries, and exclude the Observation bridge to keep other connectors untraced.
* The anchor root's duration is meaningless, and tool spans can start days before their `invoke_agent`
  parent, which backends may render awkwardly (an exit criterion of the proof of concept).
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
