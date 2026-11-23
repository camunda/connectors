# Agent lifecycle listener SPI (experimental)

An agent lifecycle listener observes the execution of the AI Agent: the jobs, the model calls and their
outcomes. It cannot change what the agent does. The first listener is the OpenTelemetry tracing of
[`aiagent-otel`](../../aiagent-otel/docs/opentelemetry-tracing.md). The decision and its alternatives are in
[ADR 019](../../docs/adr/019-agent-lifecycle-listeners.md).

## The SPI

Implement `AgentLifecycleListener` (package `aiagent.api.lifecycle`) and register it as a Spring bean. It has a
stable `id()`, which names the listener's state in the agent context, and one callback,
`onJobStarted(JobStarted)`, which returns the `AgentJobObserver` for that job, or `AgentJobObserver.NOOP`. The
observer receives the callbacks of the job.

In `connector-aiagent`, `AgentLifecycleDispatcher` announces each job of both flavors to all listeners, in the
order in which Spring resolves the beans (`@Order` is honored), and hands the request handler an
`AgentJobLifecycle` for the job. That object is `AgentJobLifecycle.NOOP` when no listener takes part.

Every type of the SPI is marked `@Experimental`: it can change in any release without a migration path.

## Terms

The events use the terms of the [glossary](../../AGENTS.md#glossary). A job is one job activation. A turn is
one call to the chat model, which the agent records as one `AgentConversationTurn`, and the iteration key
identifies it over the lifetime of the agent instance. A job runs several turns when the provider pauses a
response or when the model calls only runtime tools, which the agent answers in the same job. A job without a
model call has no turn. The agent response is what the job returns once its last turn is done.

## Contract

- Events are built lazily, only when at least one listener takes part in the job, so a job without listeners
  does no work for them.
- The lifecycle layer never fails a job. A failure while an event is built, a listener that throws, and a
  failure in the layer itself are logged and the callback is skipped. The warning names the listener id, the
  callback and the exception class only, because events and exception messages can contain prompts and tool
  results; the throwable is logged at DEBUG. A listener that throws in `onJobStarted` gets no observer for that
  job.
- Every listener sees the complete event data: prompts, tool arguments and tool results. Capture, redaction and
  size limits are the listener's job. Events never carry the lease token of the job.
- `onToolDiscoveryDispatched` and the first `onTurnStarted` of a job are reported after the agent instance write
  of that step succeeded, so they identify a job whose write was accepted. A job that fails before that write,
  because it reached the model call limit or because the write rejected it as superseded, reports `onJobFailed`
  without them.
- Only `onToolDiscoveryDispatched` and `onAgentResponseCreated` can return state, and they run on the two paths
  that persist the agent context. The state is stored under `lifecycle:<listener id>` in the context
  properties, and `JobStarted.previousState()` hands it back to the listener in the next job. It must be plain
  JSON: maps with string keys, lists, strings, booleans, finite numbers and null, at most 32 levels deep, and at
  most 4096 bytes when serialized as UTF-8 JSON. A contribution that breaks these rules is dropped, the stored
  value stays, and one WARN names the listener id, the callback and the reason, never the state. Returning
  `null` leaves the stored state unchanged. Treat missing state as "start over": a failed, superseded or
  deferred job never persists its contribution.
- Every started turn is closed with `onTurnCompleted` or `onTurnFailed`, and a failure is rethrown unchanged. A
  model rejection reaches `onTurnFailed` as the coded connector exception that the job fails with, so
  `errorCode` is set and the provider's rejection is its cause.
- `onJobFailed` reports an exception that the agent work of the job threw; the handler rethrows it unchanged.
  `onJobCompleted` and `onJobCompletionFailed` are called after the engine resolved the completion command, but
  only for a job that runs a turn or dispatches tool discovery. They also report a failure after the agent work
  returned, for example a result that exceeds the size limit. A job whose agent work threw gets `onJobFailed`
  and no completion callback.

## Lifecycle events and agent instance history

Some lifecycle events accompany a write to the agent instance history
([`agent-instance.md`](../../connector-aiagent/docs/agent-instance.md)). The two are separate mechanisms.

| Lifecycle event           | Agent instance history call | Order                                                                                                    |
|---------------------------|-----------------------------|----------------------------------------------------------------------------------------------------------|
| `TurnStarted`             | `applyTurnStart`            | the event follows the write; a turn that continues a paused response has no write                        |
| `TurnCompleted`           | `applyTurnCompletion`       | the write follows the event, once per turn                                                               |
| `ToolDiscoveryDispatched` | `applyToolDiscoveryStart`   | the event follows the write                                                                              |
| `JobDeferred`             | `applyToolCallResults`      | the event follows the write, which is skipped when there is nothing to report                            |

`JobStarted`, `TurnFailed`, `AgentResponseCreated`, `JobFailed` and the completion callbacks have no history
counterpart. The history calls are synchronous writes, and a failing write fails the job. The listener
callbacks are isolated and never fail the job.

## How do I see X?

| I want to see… | Where |
|---|---|
| The agent decided to call tools | `AgentResponseCreated.toolCalls()` (`finalAnswer()` is true when it is empty); `TurnCompleted.assistantMessage()` has the calls of each turn, admitted: each `ToolCall` carries its kind and, for a call that is not carried out, its rejection |
| Gateway tool discovery was dispatched | `ToolDiscoveryDispatched.toolCalls()` |
| A tool result arrived while others are pending | `JobDeferred.arrivedResults()` |
| The tool results a turn consumes | `TurnStarted.toolExecutions()`: a `ToolExecution` per result with its originating `call` (null for results that match no call), the `result` and a `cancelled` flag. `call().resultSource()` tells whether the process, a runtime tool or the rejection of the call produced it |
| When a tool started and finished | `ToolExecution.result().completedAt()`, the completion time, which can be absent. `ToolExecution.startedAt()` is the measured start of a runtime tool. A process tool has no start in the event: a listener that needs the time the tool calls were issued records it itself in `onAgentResponseCreated` and keeps it in its state |
| A model call started, finished, or failed | `TurnStarted` (iteration key, chat model, the messages and tool definitions sent to the model, the input of the turn), `TurnCompleted` (the admitted assistant message, the metrics, and `anotherTurnFollows`, true when the job calls the model again) and `TurnFailed(iterationKey, cause, errorCode)` |
| The token usage of a model call | `TurnCompleted.metrics().tokenUsage()`, one per turn, as in the agent instance history; input tokens exclude the cache read and cache creation counts, which are separate fields. `AgentResponseCreated.jobMetrics()` adds up the turns of the job |
| The job failed | `JobFailed(cause, errorCode)`; the error code `AGENT_INSTANCE_SUPERSEDED` marks a job whose agent instance write was rejected because a newer activation took over |
| The engine accepted or rejected the job completion | `onJobCompleted()` and `onJobCompletionFailed(JobCompletionFailure)`; a job whose completion the engine ignores because it was superseded shows as `CommandIgnored` |

The engine runs the process tools, so the connector sees no tool start or end, and there is no tool-started or
tool-ended event. A process tool shows up in `TurnStarted.toolExecutions()` of the turn that consumes its result.
The agent answers the calls of runtime tools and the rejected calls in the job: their results are in
`TurnStarted.toolExecutions()` of the next turn, in the same job when the model called only such tools, and in
the job that consumes the results of the process tools otherwise. A job that only waits for gateway tool
discovery results gets no events. A job that is deferred, or that finds no input in the Sub-process flavor, has
no turn and persists nothing: it receives `onJobStarted` and, when deferred, `onJobDeferred`, but no completion
callback.
