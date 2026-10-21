# Publish agent lifecycle events to registered listeners

* Deciders: Agentic AI Team
* Date: Oct 19, 2026

## Status

**Proposed**. The extension point is experimental: it ships marked as such, and its event set may
change after the first consumer ([ADR 019](019-opentelemetry-genai-tracing.md)) has been validated.

## Context and Problem Statement

Users of the AI Agent, and features built on it, need to see what an agent does while it runs:
distributed tracing, metrics, audit logs, and later hooks that inspect or change behavior. Today the
only structured record of an agent's execution is the agent instance history, which the request
handler pushes inline to the engine for Operate. It is Camunda-internal, fenced against superseded
job activations, and fails the job when the push fails. Nothing lets another component observe the
same moments.

The execution model shapes what such an extension point can promise. An agent run is spread over many
stateless job executions that can run on different runtime instances. Tool calls are BPMN activities
that can take days. Jobs fail, retry, time out, and are superseded. Only a few code paths persist the
agent context: the dispatch of gateway tool discovery and the completion of a turn. A consumer that
needs state across jobs, such as a trace identity, has no place to keep it that survives a job
boundary.

An earlier tracing design for the AI Agent already enumerated the lifecycle points a consumer needs
(tool discovery, tool results received, model call started, completed or failed, tool calls issued,
iteration completed, limits), and the history client's call sites mark the same moments. The question
is how the AI Agent should expose those moments to consumers, what each kind of consumer may do at
them, and where consumers live in the module layout of [ADR 015](015-split-agentic-ai-into-modules.md).

## Decision Drivers

* **A consumer cannot break or delay a job**: observation is best-effort, and the agent must complete
  with the same result whether or not a consumer is registered or misbehaves.
* **Stateless jobs**: a consumer that needs cross-job state must reuse persistence that already
  exists, without a new store or an extra write on paths that do not persist today.
* **Small, stable extension API**: custom consumers compile against `aiagent-api` and `aiagent-model`
  only, as ADR 015 intends for extension points.
* **First consumer without core dependencies**: tracing must not add its libraries to the AI Agent
  connector or to the default application.
* **Honest contracts**: observing what happened and changing what happens need different guarantees,
  and mixing them in one interface hides which one a callback is.
* **Reuse of an existing vocabulary**: one event taxonomy in the repository, not two.

## Considered Options

1. Embed the first consumer, tracing, directly in the request handler.
2. A read-only observer interface with notifications only.
3. A listener interface with three callback categories (notifications, state contribution at persist
   points, and model-round pairing), with behavior-changing hooks deferred.
4. An interceptor interface that can both observe and change requests, responses, and tool calls.
5. Use the agent instance history client as the event seam and add consumers behind it.

## Decision Outcome

Chosen option: **Option 3 — an experimental listener interface with three callback categories**,
because tracing needs more than notifications (durable state and scoped spans around the model call),
and a separate, explicit contract for each need keeps observers from quietly becoming interceptors.

The decision comprises:

* **D1 — The extension point lives in `aiagent-api`, emission in `connector-aiagent`, consumers in
  their own modules.** A consumer is a Spring bean that implements the listener interface. At the start
  of a job the listener returns an observer for that job, which receives the job's callbacks. The AI
  Agent connector emits the events from its shared request handler for both flavors and carries the
  flavor in the event. Two concrete classes in the connector carry the emission: a dispatcher announces a
  job to the listeners, and the object it returns carries the later callbacks of that job. That object is
  a shared no-op instance when no listener takes part in the job. A consumer module depends on the API and model libraries only, never on the AI
  Agent connector, and is shipped in the agentic AI bundle when it is part of the product.

* **D2 — Callbacks fall into three categories, each with its own contract.**

  | Category | Callbacks | Contract |
  |---|---|---|
  | Notifications | job started, tool discovery dispatched, job deferred, turn started, model round started and outcome, job failed, job completed, job completion failed | Failure-isolated: the framework catches and logs every consumer exception, keeps registration order, and runs consumers on the job thread. A notification cannot change the job. |
  | State contribution | tool discovery dispatched, turn completed | Returns a value only. The framework checks it and writes it into the consumer's own namespace of the agent context, only on paths that already persist the context. An exception or an invalid value drops the contribution. |
  | Model-round pairing | model round started, then completed or failed | Owned by the framework with try/finally on the same thread. A consumer can neither skip nor alter the model call, and a throwing consumer still gets its round closed. |

  Tool discovery dispatched and turn started are reported after the lease-fenced agent instance write of
  that step succeeded, so they identify a job that held the lease at that point. A job that fails before
  that write, because it reached the model call limit or was superseded, reports job failed without them.

  Events are built lazily: the framework builds an event only when at least one listener takes part in the
  job, so a job without listeners does no work for them. Nothing in the lifecycle layer can fail a job. A
  failure while building an event, in a listener, or in the layer itself is logged and the affected callback
  is skipped. Events and exception messages can contain prompts and tool results, so the log names only the
  listener, the callback and the exception class at WARN, and carries the throwable at DEBUG.

* **D3 — Consumer state rides on the two existing persist points and nowhere else.** The agent context
  is persisted by the completion command of exactly two paths: the dispatch of gateway tool discovery,
  and the completion of a turn. Jobs that complete without an agent response (deferred partial
  results, no input) persist nothing, and a failed or superseded job's completion never lands. A
  consumer's state is therefore written only where the framework already writes, and is lost together
  with the job that produced it. A consumer must treat lost state as "start over", which for tracing
  means starting a new run. State is a map of plain JSON values (maps with string keys, lists, strings,
  booleans, finite numbers and null) under a key derived from an explicit consumer id, so renaming a bean
  cannot orphan persisted state. Its serialization is limited to 4096 bytes of UTF-8. The framework checks
  every contribution and stores a deep copy. A contribution that is not plain JSON, nests too deeply, or
  exceeds the limit is dropped: the previously stored value stays, and one warning names the listener, the
  callback and the reason, never the state. Older connector versions keep unknown context entries, so the
  slot is additive.

  | Path | Persists the agent context | State contribution |
  |---|---|---|
  | Tool discovery dispatched | Yes | Yes |
  | Turn completed (tool calls issued or final answer) | Yes | Yes |
  | Job deferred (partial tool results) | No | No |
  | No input | No | No |
  | Job failed or completion rejected | No | No |

* **D4 — Events carry complete data; policy belongs to the consumer.** Events include prompts, tool
  arguments, and tool results, because only the consumer knows what it may keep. Capture switches,
  redaction, and size limits are the consumer's job. The documentation of the extension point says
  that every registered listener sees this data. Events never carry the job's lease token, which is a
  fencing credential.

* **D5 — The event surface follows the earlier taxonomy and the history client's call sites, limited
  to what the first consumer needs.** A small public job-kind enumeration replaces the internal
  initialization and composition result types, so the internal state machine does not leak into the
  API. Events fire for both flavors; the flavor is part of the event so that a consumer can restrict
  itself. A deferred partial-result job has its own event, which most consumers ignore, so that "open
  lazily" decisions stay in the consumer. The tool results consumed by a turn carry the engine's
  completion time and no start time, because the engine runs tools as BPMN activities and the connector
  sees neither their start nor their end. A consumer that needs the time the tool calls were dispatched
  records it itself when it contributes state at the completion of the issuing turn. Tools that the
  connector runtime will answer itself inside the job are not BPMN activities: they will need their own
  event with measured start and end, emitted by the job that runs them.

* **D6 — The extension point is experimental.** A marker annotation in `aiagent-api` flags every type
  of it. The types are excluded from the planned API compatibility check of `aiagent-api` and
  `aiagent-model`, and changes to them are listed in the breaking changes document without a migration
  guarantee. The event set may change once the first consumer has been validated against real
  backends.

* **D7 — The agent instance history client is not a consumer.** Its call sites correspond one to one
  to the lifecycle points, and the points are named and placed to match them. It stays on its own
  path because it takes internal turn types, is fenced against superseded activations, and fails the
  job on error, which would break the failure-isolation contract of notifications. Whether it can
  become a consumer is a separate decision once the extension point has stabilized.

* **D8 — Interceptors are deferred.** Hooks that change requests, responses, or tool calls
  (guardrails, compaction, prompt rewriting) need their own failure contract, ordering rules, and a
  decision on where in the continuation loop they run. They are a separate design, and nothing in this
  decision lets a listener alter agent behavior.

### Non-goals

* Behavior-changing hooks.
* Any change to the agent instance history API or Operate.
* Delivery of events to external systems (queues, webhooks); a consumer can do that itself.
* Central filtering or redaction of event content.

### Positive Consequences

* Tracing, metrics, and audit consumers can be built without touching the agent handler.
* The AI Agent connector and the default application take on no consumer dependencies.
* The failure and persistence behavior of consumers is defined once instead of per consumer.
* The event vocabulary is shared with the earlier tracing design and the history client.

### Negative Consequences

* A public, experimental API surface in `aiagent-api` that may change after the first consumer is
  validated, and that early adopters must track.
* Every registered listener bean sees prompts and tool results, so installing a listener is a
  data-handling decision for the operator.
* Consumer state grows the agent context by a small entry per stateful consumer.
* The history client keeps a parallel path, so two code paths mark the same lifecycle points until
  that decision is revisited.

## Pros and Cons of the Options

### Option 1: Embed tracing in the request handler

* Good, because it is the smallest change for the first consumer.
* Bad, because every later consumer repeats the work and widens the handler.
* Bad, because the connector takes on the consumer's dependencies.

### Option 2: Read-only observer with notifications only

* Good, because the contract is the simplest.
* Bad, because a consumer cannot keep state across jobs or scope its work around a model call, so
  tracing would need a workaround outside the contract.

### Option 3: Listener with three callback categories (chosen)

* Good, because each need has its own explicit guarantee, and observers cannot alter behavior.
* Good, because state reuses the existing persist points.
* Bad, because the contract has three categories to document and test.

### Option 4: Interceptor interface

* Good, because it serves the pro-code hooks that change behavior as well.
* Bad, because it needs a failure and ordering contract that is not settled, which would delay the
  first consumer.
* Bad, because observing and changing behavior in one interface weakens the best-effort guarantee
  that observation needs.

### Option 5: History client as the seam

* Good, because it reuses existing call sites.
* Bad, because the client is fenced and fails jobs, and it takes internal types, so consumers would
  inherit semantics they cannot afford.

## Follow-up

* **Interceptors**: a separate decision for hooks that change behavior, built on the continuation
  loop of the agent request handler. Closes when that design is accepted.
* **History client as a consumer**: revisit after the first consumer has run against real backends
  and the event set has been adjusted. Closes with a decision that supersedes D7.
* **Stabilization**: once the event set has settled, remove the experimental marker and include the
  types in the API compatibility check.
