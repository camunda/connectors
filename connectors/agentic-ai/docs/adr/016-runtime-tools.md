# Runtime tools: let the connector runtime run tool calls itself

* Deciders: Agentic AI Team
* Date: Oct 19, 2026

## Status

**Proposed**. The initial implementation marks the runtime tool SPI as experimental: it may change in an incompatible
way in a minor release. Before the 8.11 release we decide whether to remove the marker.

## Context and Problem Statement

Every tool of an agent is a BPMN element: an ad-hoc element of the sub-process, or a gateway tool such as MCP or A2A
that the agent maps to elements. The model calls the tool, the agent activates the element, and the result comes back
with a later job.

Some capabilities do not fit that shape. Loading a skill, saving a memory or listing the entries of a registry work on
state that lives in the agent context. They are cheap functions of the conversation.
A BPMN element for each of them costs a job round trip per call, cannot reach the agent context, and has to be modeled
in every process that uses an agent.

We build a framework for Camunda agents, and the AI Agent connector is its out-of-the-box agent. The framework needs a
way for the connector runtime, and for operators and vendors who build on it, to offer a tool that the agent runs
itself, inside the job, without a BPMN element.

## Decision Drivers

* **Pro-code extensibility**: an operator or vendor adds a tool through a small API in `aiagent-api`, without touching
  the core.
* **One public path**: the tools the connector ships use the same API as any other tool, with no name checks and no
  helpers that only the core can call. A custom tool can replace a built-in one.
* **Distributed execution**: an agent runs one job after another, possibly on different workers, and a failed job is
  retried. Nothing may live outside the agent context between jobs.
* **Mixed batches**: one assistant message can call runtime tools and process tools. The process calls are valid work
  and must not cost an extra model call.
* **Running processes keep running**: an agent instance that exists before the facility, and a process that has an
  element named like a runtime tool, must not break.
* **An honest contract and room to evolve**: a tool author must know what the framework guarantees and what it does
  not. The SPI is new and will change.

## Considered Options

1. Offer the capabilities as BPMN elements that every process models.
2. Special-case the built-in capabilities in the agent loop.
3. A runtime tool SPI: a tool is a definition, an enabled check and a call. The agent runs it in the job.

## Decision Outcome

Chosen option: **Option 3, the runtime tool SPI**, because it is the only option that is fast (no job round trip),
works on the agent context without exposing it, and gives built-in and custom tools one path.

### The tool and its API

A runtime tool is a Spring bean of one interface with three members: the definition the model sees, a check whether the
tool is enabled, and the call. The call receives the tool call, with its arguments bound to a type the author chooses, and a
read-only context: the execution context and the agent context. It is not the conversation, so a tool cannot read or
change messages. The types a tool receives are interfaces that the framework implements, so members can be added
without breaking tools. The result is the content the model reads, or an error.

A tool does not change the agent context. The alternatives were:

* *A general update of the agent context* (a function from the context to the context). Rejected: a tool could rewrite
  anything, including metrics and persisted tool definitions, and the SPI would be bound to the whole shape of the
  context.
* *State per tool.* Rejected: it needs a namespace in the agent context and a migration story, and no tool needs it. The
  author keeps no state in the bean, because the agent runs on any worker.

**Errors.** An expected failure is an error result, or a `RuntimeToolException` as sugar for it, and the model reads the
text. Any other exception fails the job. A runtime tool is code of the connector or of the operator, and an unexpected
exception must not turn into text the model acts on. The job is retried like any job.

### Names

Tool names are unique. A custom tool uses a name with a prefix of its own. The prefix `c8__` is for the tools that the
connector ships, such as the one that loads a skill.

A tool element of a process must not have the name of a registered runtime tool, whether the tool is enabled or not.
Such an element would be indistinguishable to the model and lose against the runtime tool without notice, and what a
tool enables can change. Tool discovery and process migration fail with an error code that names the conflict, and so
does an agent job whose tools contain such a name.

### Agent instance features

An agent instance records the features it was created with in its agent context, as a list of names. Behavior that
changes the wire format or the persisted shape is gated on a feature, so an instance that was created before the
behavior existed keeps the behavior it started with. A feature is never added to an existing instance.

The first feature is `runtimeTools`. An instance without it is offered no runtime tool and checks no tool name, because
its process, which may already contain an element with such a name, must keep running.

### Tagging, one execution site, and mixed batches

The agent tags every tool call of an assistant message with the kind of its tool, process or runtime, exactly once: when
it receives the message from the model, before the conversation takes the message in. A call is a runtime call if the
agent instance has the `runtimeTools` feature and a registered runtime tool owns the name, enabled or not. The tag is
persisted with the message and never computed again. Only process calls are activated.

Runtime calls run in one place, an executor that the request handler calls, and the composer of the next turn never runs
a tool or changes the context. If the model calls runtime tools only, the agent runs them in the same job and calls the
model again; this round counts against the model-call limit. If the model calls both kinds, **only the process calls are
activated**. The agent runs the runtime calls when it composes the next turn, and merges their results with the process
results in call order. An interrupted turn still runs them.

The alternatives for mixed batches were:

* *Run the runtime calls in the issuing job and persist their results until the process results arrive.* Rejected: it
  needs a persisted field of pending results, a rule that makes them reach exactly one turn, and a separate history
  write.
* *Classify the calls again in the later job.* Rejected: what a tool enables can change between the two jobs, and so can
  the runtime tool beans of the worker. The agent would then wait for a result of a call that the process never runs, or
  run a tool whose element already ran.
* *Persist the classification on the tool call.* **Chosen**: a small persisted tag and no pending-results state. A call
  keeps its kind for the life of the conversation, so a change of what a tool enables cannot misroute it. The results are
  a function of the conversation and the tags, so a retried job computes them again. The price is that every
  conversation store must keep the tag.
* *Run every runtime call in its own round before the process calls.* Rejected: one more model call for every mixed
  batch, and it changes what the model sees in the middle of a batch.
* *Make the model split its batches.* Rejected: not controllable.

### The execution contract

The contract is stated in the javadoc of the SPI. A tool author can rely on it, and must honor it:

* **At least once.** A failed job is retried and runs the call again. Side effects outside the agent, such as a request
  to another system, are not undone, so they must be idempotent.
* **Concurrency.** The calls of one assistant message may run concurrently. Their results are applied in call order.
  A tool may be called from different threads. The executor runs the calls one after the other today.
* **Below the job timeout.** The framework neither cancels nor times out a call.
* **Failures** as described above.

A call that names a registered runtime tool that is not enabled when it runs gets an error
result and never goes to the process.

The executor keeps the start and completion time of every call, so later metrics and events with durations need no
change of the contract.

The history reports runtime tool calls and results like other tool calls, without an element id, because a runtime tool
has no element. In a mixed batch the results carry the turn start of the job that composes them.

### Positive Consequences

* A runtime tool call costs no job round trip, and a batch of runtime calls only costs no extra job.
* A tool author implements one interface and needs no core class.
* Mixed batches need a small persisted tag and no pending results. A retried job computes the results again.
* A change of what a tool enables cannot misroute a call. At worst, a call that would have succeeded gets an error result.
* The framework can add members to the types a tool receives without breaking tools.

### Negative Consequences

* **Every conversation store must keep the tag.** A store that drops it turns a runtime call into a process call that
  never gets a result. A missing tag reads as a process call, so existing data keeps working.
* A tool call that targets a registered runtime tool that is not enabled when it runs gets an error result, and the model
  has to recover.
* The model receives the results of the runtime calls of a mixed batch with the next request, together with the process
  results. A process call that never completes holds that request back, as it does for every result of the batch.
* A tool runs in the thread of the job without a timeout, so a slow or blocking tool holds the job.
* Every enabled runtime tool adds its definition to every model request, and reserves its name in every process.

## Follow-up

* **Tools executed as jobs.** A tool that runs long, or that must run on its own worker, is executed as a job instead of
  in the thread of the agent job. The job timeout of the agent is then extended while such tools run.
* **A per-agent allow and deny list of runtime tools.** Every enabled tool is offered to every agent instance. The
  list becomes necessary as the number of tools grows, because each tool costs input tokens in every call and can change
  tool selection.
* **A schema derived from a class.** Tool authors write the input schema by hand; deriving it from the arguments type
  would remove the need to know the schema format from every tool.
* **A test fixture for tool authors.** A fixture that runs a tool the way the agent does, so authors need not copy the
  tests of the framework.
* **An engine and Operate marker for runtime tools in the history.** The history API has no tool kind and no result
  status, so a runtime tool is recognizable only by its missing element id and an error result is plain text.
