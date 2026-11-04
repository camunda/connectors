# Runtime tools: let the connector runtime execute tool calls itself

* Deciders: Agentic AI Team
* Date: Oct 19, 2026

## Status

**Proposed**. The initial implementation marks the runtime tool SPI as experimental: it may change in an incompatible
way in a minor release. Before the 8.11 release we decide whether to remove the marker.

## Context and Problem Statement

Every tool of an agent is a BPMN element: an ad-hoc element of the sub-process, or a gateway tool such as MCP or A2A
that the agent maps to elements. The model calls the tool, the agent activates the element, and the result comes back
with a later job.

Some capabilities do not fit that shape. Loading a document, selecting a document for the response or listing the entries of a
registry work on state that lives in the agent context. They are cheap functions of the conversation.
A BPMN element for each of them costs a job round trip per call, cannot reach the agent context, and has to be modeled
in every process that uses an agent.

We build a framework for Camunda agents, and the AI Agent connector is its out-of-the-box agent. The framework needs a
way for the connector runtime, and for operators and vendors who build on it, to offer a tool that the agent executes
itself, inside the job, without a BPMN element.

## Decision Drivers

* **Pro-code extensibility**: an operator or vendor adds a tool through a small API in `aiagent-api`, without touching
  the core.
* **One public path**: the tools the connector ships use the same API as any other tool, with no name checks and no
  helpers that only the core can call.
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
3. A runtime tool SPI: a tool is a definition, an enabled check and a call. The agent executes its calls in the job.

## Decision Outcome

Chosen option: **Option 3, the runtime tool SPI**, because it is the only option that is fast (no job round trip),
works on the agent context without exposing it, and gives built-in and custom tools one path.

### The tool and its API

A runtime tool is a Spring bean of one interface with three members: the definition the model sees, a check whether the
tool is enabled, and the call. The call receives the tool call, with its arguments bound to a type the author chooses, and a
read-only context: the execution context and the agent context. It is not the conversation, so a tool cannot change
messages. Both contexts still hold the conversation context, so with the in-process store a tool can read the messages
stored before the job. The types a tool receives are interfaces that the framework implements, so members can be added
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
connector ships, such as the tools of the document flow.

A tool element of a process must not have the name of an enabled runtime tool. Such an element would be
indistinguishable to the model and lose against the runtime tool without notice. The check runs in every agent job
against the tools the job offers, so a tool that is not enabled reserves no name. An agent job whose tools contain a
name twice, once as a process tool and once as an enabled runtime tool, fails with an error code that names the conflict.

### Agent instance features

An agent instance records the features it was created with in its agent context, as a list of names. Behavior that
changes the wire format or the persisted shape is gated on a feature, so an instance that was created before the
behavior existed keeps the behavior it started with. A feature is never added to an existing instance.

The first feature is `runtimeTools`. An instance without it is offered no runtime tool and checks no tool name, because
its process, which may already contain an element with such a name, must keep running. The feature gates only what is
offered. The answer for a call of a tool that is not offered applies to every agent instance, because the process never
activates an element for such a call and an instance that hands it to the process waits for a result forever.

### Tagging, one execution site, and mixed batches

The agent tags every tool call of an assistant message with the kind of its tool, process or runtime, exactly once: when
it receives the message from the model, before the conversation takes the message in. Every call, of every agent instance,
is matched against the tools offered in the job: it takes the kind of the tool with its name. A call whose name is not offered, as a model may invent one or a tool may have been disabled, is a runtime call: the
tag says that the agent answers the call and that the process never gets it. The agent answers a call of a tool that is
not offered with an error result that says the tool does not exist; this is a step of the agent, not of a tool. The tag
is persisted with the message and never computed again, so it may appear in the state of any agent instance, also of
one created before runtime tools existed. Only process calls are activated. The definition of a runtime tool
declares the kind runtime and the registration rejects other tools at start-up; the tag of a call, not the definition,
decides how a call is answered.

Runtime calls are executed in one place, an executor that the request handler calls, and the composer of the next turn
never executes a tool or changes the context. If the model calls runtime tools only, the agent executes them in the same
job and calls the model again; this round counts against the model-call limit. If the model calls both kinds, **only the
process calls are activated**. The agent executes the runtime calls when it composes the next turn, and merges their
results with the process results in call order. An interrupted turn still executes them.

The alternatives for mixed batches were:

* *Execute the runtime calls in the issuing job and persist their results until the process results arrive.* Rejected:
  it needs a persisted field of pending results, a rule that makes them reach exactly one turn, and a separate history
  write.
* *Classify the calls again in the later job.* Rejected: what a tool enables can change between the two jobs, and so can
  the runtime tool beans of the worker. The agent would then wait for a result of a call that the process never runs, or
  execute a tool whose element already ran.
* *Persist the classification on the tool call.* **Chosen**: a small persisted tag and no pending-results state. A call
  keeps its kind for the life of the conversation, so a change of what a tool enables cannot misroute it. The results are
  a function of the conversation and the tags, so a retried job computes them again. The price is that every
  conversation store must keep the tag.
* *Execute every runtime call in its own round before the process calls.* Rejected: one more model call for every mixed
  batch, and it changes what the model sees in the middle of a batch.
* *Make the model split its batches.* Rejected: not controllable.

### The execution contract

The contract is stated in the javadoc of the SPI. A tool author can rely on it, and must honor it:

* **At least once.** A failed job is retried and executes the call again. Side effects outside the agent, such as a
  request to another system, are not undone, so they must be idempotent.
* **Concurrency.** The calls of one assistant message may be executed concurrently. Their results are applied in call
  order. A tool may be called from different threads. The executor executes the calls one after the other today.
* **Below the job timeout.** The framework neither cancels nor times out a call.
* **Failures** as described above.

A runtime call whose tool is not offered gets an error result from the agent, and one whose tool is no longer enabled
when it is executed gets an error result from the executor. Neither goes to the process.

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
  never gets a result, for an agent instance with or without the `runtimeTools` feature. A missing tag reads as a process
  call, so existing data keeps working.
* A tool call that names a tool that is not offered, or a runtime tool that is not enabled when it is executed, gets an
  error result, and the model has to recover.
* The model receives the results of the runtime calls of a mixed batch with the next request, together with the process
  results. A process call that never completes holds that request back, as it does for every result of the batch.
* A tool is executed in the thread of the job without a timeout, so a slow or blocking tool holds the job.
* Every enabled runtime tool adds its definition to every model request, and a tool of the process cannot use its name
  while the tool is enabled. A change of what a tool enables changes the request and invalidates the prompt cache of the
  provider.

## Follow-up

* **Durable execution of tool calls.** A failure after a tool call executes the tool again, so a tool must accept
  at-least-once execution and idempotent side effects. A durable execution makes the call be executed once and replaces
  this requirement in the `RuntimeTool` contract.
* **Tools executed as jobs.** A tool that takes long, or that must be executed on its own worker, is executed as a job
  instead of in the thread of the agent job. The job timeout of the agent is then extended while such tools are
  executed.
* **A per-agent allow and deny list of runtime tools.** Every enabled tool is offered to every agent instance. The
  list becomes necessary as the number of tools grows, because each tool costs input tokens in every call and can change
  tool selection.
* **A schema derived from a class.** Tool authors write the input schema by hand; deriving it from the arguments type
  would remove the need to know the schema format from every tool.
* **A test fixture for tool authors.** A fixture that executes a tool the way the agent does, so authors need not copy
  the tests of the framework.
* **No access to stored messages.** The agent and execution contexts a tool receives hold the conversation context, so a
  tool can read the stored messages when the store keeps them in the agent context (the in-process store). A view of
  both contexts without the conversation context closes this access.
* **An engine and Operate marker for runtime tools in the history.** The history API has no tool kind and no result
  status, so a runtime tool is recognizable only by its missing element id and an error result is plain text.
