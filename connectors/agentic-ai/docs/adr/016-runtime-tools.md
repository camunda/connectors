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
connector ships, such as the tools of the document flow. This is a convention: no prefix is checked or reserved, and the
check of the exact name below is the only enforcement. Reserving a whole prefix is a possible follow-up.

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
offered. The rejection of a call of a tool that is not offered applies to every agent instance, because the process never
activates an element for such a call and an instance that hands it to the process waits for a result forever, without
an incident. This is a deliberate exception to the rule that a behavior with a persisted shape is gated on a feature.

### Admission, result sources, and mixed batches

The agent admits every tool call of an assistant message exactly once: when it receives the message from the model,
before the conversation takes the message in. Like an admission controller, admission sets what the rest of the agent
reads from the call and can reject it. Every call, of every agent instance, is matched against the tools offered in the
job, and the decision is persisted with the call and never made again, so it may appear in the state of any agent
instance, also of one created before runtime tools existed.

Admission sets the kind of a call, the kind of the tool with its name: process or runtime. The definition of a runtime
tool declares the kind runtime and the registration rejects other tools at start-up; the kind of the call, not the
definition, is what the rest of the agent reads.

Admission rejects a call that must not be carried out, such as a call whose name is not offered, as a model may invent
one or a tool may have been disabled. The rejection is the error text that becomes the tool call result of the call,
persisted with the call. A call of a name that is not offered keeps the kind process. A rejected call is never executed,
also when a runtime tool with that name is enabled by the time its tool call result is composed. A rejection is a step of
the agent, not of a tool, and it may apply to a call of any kind: a check of the arguments of a call belongs to
admission too. A rejection is decided before a call runs; an error result is the outcome of a call that ran.

The kind and the rejection give the **result source** of a call, who produces its tool call result:

* the **process** for a call of a process tool that is not rejected; only these calls are activated;
* a **runtime** tool for a call of a runtime tool that is not rejected;
* the agent for a **rejected** call.

A **local tool call** is a call whose tool call result the agent produces itself: the call of a runtime tool and the
rejected call. A **process tool call** gets its tool call result from the process. The tool calls of an assistant
message and their results form a batch. A rejected call has its result from the moment the batch is created: its
rejection, decided at admission. After admission, the result source is read in two places only: when the batch is
created, for the rejected calls, and in the step that executes the calls of runtime tools. The input of the next turn
is assembled from the batch without knowing where a result came from.

The composer of the next turn never executes a tool or changes the context. If the message has local tool calls only,
the agent adds their tool call results in the same job and calls the model again; this round counts against the
model-call limit. If the message also has process calls, **only the process calls are activated**. The agent produces
the tool call results of the local tool calls in the job that receives the process results, and the input of the next
turn has all results in call order. When an event interrupts the tool calls, every call of the batch that has no
result yet is cancelled: the missing process calls and the calls of runtime tools, which were not executed yet. A
runtime tool therefore never runs once the agent has told the model that the tool executions were canceled. A rejected
call keeps its rejection.

The alternatives for mixed batches were:

* *Execute the runtime calls in the issuing job and persist their results until the process results arrive.* Rejected:
  it needs a persisted field of pending results, a rule that makes them reach exactly one turn, and a separate history
  write.
* *Admit the calls again in the later job.* Rejected: what a tool enables can change between the two jobs, and so can
  the runtime tool beans of the worker. The agent would then wait for a result of a call that the process never runs, or
  execute a tool whose element already ran.
* *Persist the admission on the tool call.* **Chosen**: the kind of the named tool and a rejection for a call that must
  not be carried out, and no pending-results state. A call keeps its kind and its
  rejection for the life of the conversation, so a change of what a tool enables can neither send a call to the process
  that is a local tool call, nor execute a call that was rejected. A call of a runtime tool that is no longer
  enabled when it is executed gets an error result. The results are a function of the conversation and the persisted
  decisions, so a retried job computes them again. The price is that every conversation store must keep both.
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

A rejected call gets its rejection as its error result from the agent and is never executed, and a runtime call
whose tool is no longer enabled when it is executed gets the error result that the tool is not available from the
executor. Neither goes to the process.

The executor keeps the start and completion time of every call, so later metrics and events with durations need no
change of the contract.

The history reports runtime tool calls and results like other tool calls, without an element id, because a runtime tool
has no element; a rejected call has none either. In a mixed batch the results carry the turn start of the job that composes them.

### Positive Consequences

* A runtime tool call costs no job round trip, and a batch of runtime calls only costs no extra job.
* A tool author implements one interface and needs no core class.
* Mixed batches need the persisted kind and rejection and no pending results. A retried job computes the results again.
* A change of what a tool enables cannot send a call to the wrong place. At worst, a call that would have succeeded gets
  an error result.
* The framework can add members to the types a tool receives without breaking tools.

### Negative Consequences

* **Every conversation store must keep the kind and the rejection.** A store that drops them turns a runtime call or a
  rejected call into a process call that never gets a result, for an agent instance with or without the `runtimeTools`
  feature. A missing kind reads as a process call and a missing rejection as none, so existing data keeps working.
* **Workers of different versions can mishandle a runtime call or a rejected call in flight.** A worker that predates
  the kind ignores the unknown properties of a stored call and treats such a call as a call of the process. It raises no
  incident and the instance waits for a result that never comes. The JSON of every other call is unchanged, so an agent
  instance that calls only tools of the process is not affected. A rejected call names a tool that does not exist, which
  hung the agent before too. While a runtime call is in flight, the agent instances are drained before a rolling upgrade,
  or only workers of one version run.
* A tool call that names a tool that is not offered, or a runtime tool that is not enabled when it is executed, gets an
  error result, and the model has to recover.
* A model that keeps calling tools that are not offered, or keeps calling only runtime tools, gets a new model call in
  the same job for every message, up to the model-call limit. The job then fails before the conversation is stored, so
  a retry starts from the same state and repeats these model calls and their cost, while the agent instance history
  already shows the rounds that were not stored. A paused turn that is continued again and again behaves the same way.
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
  executed. Like a durable execution, this is a new result source: it changes admission and the step that produces the
  results of the local tool calls, not the composition of the next turn.
* **State of a tool per agent instance.** A tool that holds a resource across calls, such as a sandbox session, needs
  data of its own in the agent context. The tool would read it from its context and change it only through an effect of
  its result, applied after the batch in call order, so a retried job applies it again. It needs a namespace per tool, a
  size limit, JSON values only and a migration story.
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
* **An error flag on the tool result.** The tool result model has no error flag, so the error that is the tool call
  result of a rejected call, like every error of a runtime tool, is plain text that the model reads. A flag would let
  the model and the history tell an error from a result.
