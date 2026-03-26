# Consolidate AI Agent job worker into Connectors SDK OutboundConnectorFunction

* Deciders: Agentic AI Team
* Date: Mar 24, 2026

## Status

**Implemented** in [#6606](https://github.com/camunda/connectors/pull/6606).

## Context and Problem Statement

The AI Agent Sub-process (job worker flavor) had its own custom handler infrastructure (`AiAgentJobWorkerHandlerImpl`,
`AiAgentJobWorkerValueCustomizer`, `JobWorkerAgentExecutionContextFactory`, etc.) that bypassed the Connectors SDK to
implement job completion with ad-hoc sub-process directives. This duplicated SDK concerns like error expression
evaluation, retry handling, and metrics — any SDK improvement had to be manually mirrored.

Should we keep the custom handler or consolidate into the SDK's `OutboundConnectorFunction` pattern?

## Decision Drivers

* **Duplication**: Error expression handling, retry backoff, secret masking, and metrics were duplicated between the
  custom handler and `SpringConnectorJobHandler`
* **Maintenance burden**: SDK improvements (new error types, improved retry logic) required manual updates in the
  agentic-ai module
* **Consistency**: Both AI Agent flavors should follow the same connector patterns where possible
* **Extensibility**: The SDK should support connectors that need custom job completion without requiring a full bypass

## Considered Options

1. Keep the custom `AiAgentJobWorkerHandlerImpl` and manually sync SDK changes
2. Introduce a `ConnectorResponse` sealed interface hierarchy in the SDK and make the job worker a standard `OutboundConnectorFunction`

## Decision Outcome

Chosen option: **Option 2 — Introduce a `ConnectorResponse` sealed interface hierarchy in the SDK**.

A sealed interface hierarchy was added to the SDK (`io.camunda.connector.api.outbound`):

- **`ConnectorResponse`** (sealed): Base interface for connector responses. Wraps return values for result expression
  evaluation. Provides `getVariables(resultVariables)` to control which variables are sent with the complete command,
  and `rejectIgnoreError()` for connectors where the `IgnoreError` error expression path is not applicable.
- **`StandardConnectorResponse`** (non-sealed): Standard job completion — the default for connectors returning plain
  objects.
- **`AdHocSubProcessConnectorResponse`** (non-sealed): Ad-hoc sub-process completion with `elementActivations()`,
  `completionConditionFulfilled()`, and `cancelRemainingInstances()`. The runtime translates this into the Zeebe
  complete command with `.withResult().forAdHocSubProcess()` configuration. No Camunda client dependencies on the
  response interface.

`AiAgentJobWorker` becomes a standard `@OutboundConnector`-annotated function that returns `AiAgentSubProcessResponse`
(implementing `AdHocSubProcessConnectorResponse`) from `execute()`. The SDK handles everything else.

### Consequences

**Positive:**
- Error expressions, retry handling, secret masking, and metrics are now handled by the SDK for both flavors
- The custom handler infrastructure was deleted (~500 lines), along with `AgentJobContext` and
  `OutboundConnectorAgentJobContext` (replaced by SDK's `JobContext` directly)
- Future SDK improvements automatically apply to both AI Agent flavors

**Negative:**
- Job completion callbacks (success/error notification) are not yet supported — the callback mechanism on
  `CommandWrapper` needs to be implemented in a follow-up
- `ConversationStore.compensateFailedJobCompletion()` exists as a placeholder but is currently not invoked

### Future improvements

- **Completion callbacks**: Extend `ConnectorResponse` with `onCompletionSuccess`/`onCompletionError` callbacks once
  `CommandWrapper` supports command outcome notification
- **`cancelRemainingInstances` flag**: The mutable flag on `JobWorkerAgentExecutionContext` (set in
  `handleAddedUserMessages()`, read in `completeJob()`) can be simplified by moving interrupted-tool-call detection
  into `completeWithResponse()` via `executionContext.initialToolCallResults()`, eliminating the mutable state
