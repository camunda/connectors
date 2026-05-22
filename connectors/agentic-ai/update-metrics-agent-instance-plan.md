# Plan: AgentInstance status & metric updates (alpha, no idempotency)

## Context

Follow-up to the just-landed agent-instance creation work on `agentic-ai/update-agent-instance-metrics-on-engine`. Today the connector calls `AgentInstanceClient.create()` once on the first `INITIALIZING` activation; the engine then has no further visibility into the agent's lifecycle. This increment wires the connector to drive **status transitions** and **metric deltas** (model calls, input/output tokens, tool calls) through the lifecycle so Operate can render live agent state and cumulative usage. Scope is intentionally minimal — a demoable happy-path with no idempotency guarantees; safer semantics ship as a follow-up.

**Constraints (per user):**
- No idempotency-key / API safeguards yet — separate follow-up after API-team discussion.
- No engine read API for AgentInstance exists yet — E2E round-trip tests are written but `@Disabled` until a read endpoint ships.
- Implementation must be simple and demoable on happy paths.
- Code flow must stay readable; efficient (fewer PATCHes) preferred over Operate granularity.

## Engine client API surface (verified on local `camunda-client-java:8.10.0-SNAPSHOT`)

Java client (`io.camunda.client.CamundaClient`):
```java
UpdateAgentInstanceCommandStep1 newUpdateAgentInstanceCommand(long agentInstanceKey);
```
Step 1 requires `elementInstanceKey(long)` → Step 2. Step 2 optional fluent setters:
- `.status(AgentInstanceUpdateStatus)` — enum at `io.camunda.client.api.command.AgentInstanceUpdateStatus`. Values: `TOOL_DISCOVERY`, `IDLE`, `THINKING`, `TOOL_CALLING`. No `INITIALIZING` (engine sets it on create), no `COMPLETED` (terminal; engine sets it).
- `.modelCalls(int)`, `.toolCalls(int)` — int deltas.
- `.inputTokens(long)`, `.outputTokens(long)` — **long** deltas. Connector-side `AgentMetrics.TokenUsage` holds `int`; upcast at the SDK call.
- `.tools(List<AgentTool>)` — out of scope here.
- `.execute()` returns `UpdateAgentInstanceResponse`.

## Status state machine (reuses client enum)

| `AgentInstanceUpdateStatus` | Set when                                                            |
|-----------------------------|---------------------------------------------------------------------|
| `INITIALIZING`              | Implicit — engine sets it during `create()`. Not PATCHed by us.     |
| `TOOL_DISCOVERY`            | Gateway-tool discovery tool calls dispatched.                       |
| `THINKING`                  | About to call the LLM.                                              |
| `TOOL_CALLING`              | LLM returned tool calls; activating elements.                       |
| `IDLE`                      | No further tools (`completionConditionFulfilled = true`).           |

No parallel connector enum — the client's `AgentInstanceUpdateStatus` is used directly, consistent with how the existing code already reuses other camunda-client types.

## PATCH cadence (two writes per LLM iteration; all sync)

| Activation kind                                  | PATCHes emitted                                                          |
|--------------------------------------------------|--------------------------------------------------------------------------|
| AHSP entry, no gateway tools                     | pre-LLM `THINKING` (status only); post-LLM `TOOL_CALLING\|IDLE` + all LLM deltas |
| AHSP entry, gateway tools needing discovery      | `TOOL_DISCOVERY` (status only)                                           |
| Tool results arrived (complete)                  | pre-LLM `THINKING` (status only); post-LLM `TOOL_CALLING\|IDLE` + LLM deltas + `toolCalls = assistantMessage.toolCalls().size()` |
| Tool results arrived (partial)                   | none (no-op completion)                                                   |
| Discovery results arrived (complete)             | pre-LLM `THINKING` (status only); post-LLM `TOOL_CALLING\|IDLE` + LLM deltas |
| Discovery results arrived (partial)              | none                                                                      |
| Migrated agent missing `agentInstanceKey`        | none — silent skip in client                                             |

All deltas (`modelCalls`, `inputTokens`, `outputTokens`, `toolCalls`) ride on a single post-LLM PATCH per iteration. `toolCalls` is counted from `assistantMessage.toolCalls().size()` — the tool call requests emitted by the LLM in its response, symmetric with how `modelCalls` is incremented per invocation.

**Sync timing chosen deliberately.** Under completion failure (incl. AHSP supersession), this PATCHes for the superseded attempt's deltas still land on the engine. Net effect: engine view matches real LLM/provider spend, at the cost of inflating relative to the persisted `agentContext`. The inverse (listener-after-completion, which under-reports real spend) was rejected because supersession is common in the job-worker flavor and provider-cost fidelity matters more than engine-vs-BPMN consistency for alpha. Idempotency-key follow-up resolves both inaccuracies.

## Design

### 1. Extend `AgentMetrics` — single record for cumulative state and per-PATCH delta

`model/AgentMetrics.java`. Add a `toolCalls` field plus minimal arithmetic helpers so the same record represents both the cumulative state held on `AgentContext` and the per-PATCH delta.

```java
public record AgentMetrics(int modelCalls, TokenUsage tokenUsage, int toolCalls) {
  // existing validators + factories...
  public AgentMetrics incrementToolCalls(int delta) { ... }
  public AgentMetrics minus(AgentMetrics other) { ... }   // for snapshot/diff
}
```

`TokenUsage` (nested) gains `minus(TokenUsage)` to support `AgentMetrics.minus`. Jackson deserializes legacy `agentContext` payloads with missing `toolCalls` as `0` (primitive int default) — no migration shim required.

**No separate `MetricsDelta` record** — earlier draft's parallel type is unnecessary once `AgentMetrics` carries `toolCalls`.

### 2. New connector type: `AgentInstanceUpdateRequest`

`agentinstance/AgentInstanceUpdateRequest.java`:

```java
@AgenticAiRecord
public record AgentInstanceUpdateRequest(
    @Nullable AgentInstanceUpdateStatus status,
    @Nullable AgentMetrics delta) {
  public static AgentInstanceUpdateRequest statusOnly(AgentInstanceUpdateStatus status) { ... }
  public static AgentInstanceUpdateRequestBuilder builder() { ... }
}
```

`delta` reuses `AgentMetrics` directly. Null `delta` (status-only PATCH) and any zero field on `delta` are skipped at the SDK call site.

### 3. `AgentInstanceClient.update(...)`

`agentinstance/AgentInstanceClient.java`:

```java
void update(AgentExecutionContext executionContext,
            AgentContext agentContext,
            AgentInstanceUpdateRequest request);
```

Both contexts pass through because the engine command needs `agentInstanceKey` (from `agentContext.metadata().agentInstanceKey()`) **and** `elementInstanceKey` (from `executionContext.jobContext().getElementInstanceKey()`, same source `create()` uses at `CamundaAgentInstanceClient.java:46`). `AgentContext` mutates across the LLM call so it must be passed explicitly, not re-read from `executionContext.initialAgentContext()`.

### 4. Parameterize `AgentInstanceErrorClassifier`

`agentinstance/AgentInstanceErrorClassifier.java`. Today 404 is RETRYABLE — appropriate for create (idempotent re-create against a transient lookup race), wrong for update (the instance must exist; 404 = permanent).

Make the classifier operation-aware:

```java
public final class AgentInstanceErrorClassifier implements ErrorClassifier {
  public static final AgentInstanceErrorClassifier FOR_CREATE = new AgentInstanceErrorClassifier(true);
  public static final AgentInstanceErrorClassifier FOR_UPDATE = new AgentInstanceErrorClassifier(false);

  private final boolean notFoundIsRetryable;
  private AgentInstanceErrorClassifier(boolean notFoundIsRetryable) { ... }

  // 404 → notFoundIsRetryable ? RETRYABLE : PERMANENT; everything else unchanged
}
```

Existing `CamundaAgentInstanceClient.create` switches from `INSTANCE` to `FOR_CREATE`.

### 5. `CamundaAgentInstanceClient.update(...)` — real implementation

`agentinstance/CamundaAgentInstanceClient.java`. Mirrors `create()` structure (lines 31-56):

```java
@Override
public void update(AgentExecutionContext executionContext,
                   AgentContext agentContext,
                   AgentInstanceUpdateRequest request) {
  final var metadata = agentContext.metadata();
  if (metadata == null || metadata.agentInstanceKey() == null) {
    return; // expected for migrated agents predating the feature
  }
  CamundaApiRetry.execute(
      () -> executeUpdate(executionContext, metadata.agentInstanceKey(), request),
      AgentInstanceErrorClassifier.FOR_UPDATE,
      retriesProperties.maxRetries(),
      retriesProperties.initialRetryDelay(),
      (cause, attempt, reason) -> buildUpdateException(cause, attempt, reason),
      sleeper);
}

private Void executeUpdate(AgentExecutionContext executionContext,
                           long agentInstanceKey,
                           AgentInstanceUpdateRequest request) {
  var cmd = camundaClient.newUpdateAgentInstanceCommand(agentInstanceKey)
      .elementInstanceKey(executionContext.jobContext().getElementInstanceKey());
  if (request.status() != null) cmd = cmd.status(request.status());
  final var delta = request.delta();
  if (delta != null) {
    if (delta.modelCalls()                    != 0) cmd = cmd.modelCalls(delta.modelCalls());
    if (delta.tokenUsage().inputTokenCount()  != 0) cmd = cmd.inputTokens((long) delta.tokenUsage().inputTokenCount());
    if (delta.tokenUsage().outputTokenCount() != 0) cmd = cmd.outputTokens((long) delta.tokenUsage().outputTokenCount());
    if (delta.toolCalls()                     != 0) cmd = cmd.toolCalls(delta.toolCalls());
  }
  cmd.execute();
  return null;
}
```

**Failure semantics (alpha):** retry exhaustion / permanent error → `ConnectorException` with `ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED`. Job fails → Zeebe retries → duplicate deltas on next attempt. Accepted now; resolved by the planned idempotency-key follow-up.

### 6. Partitioning stays internal to `AgentMessagesHandlerImpl`

The matching of incoming `toolCallResults` against the last assistant message's expected tool calls (producing ordered/missing lists, handling cancellation) is kept inline in `AgentMessagesHandlerImpl`. No external partition type is exposed; the result is either a `ToolCallResultMessage` added to memory or a no-op (partial wait).

`AgentMessagesHandler.addUserMessages` returns `List<Message>` — the messages added to runtime memory. No wrapper type.

**Deferred:** surfacing the partition to the outer layer (e.g. for the idempotency-key follow-up that needs processed-result IDs) is left for when that follow-up actually ships. Early extraction had no consumer once the metric source moved to the LLM response.

### 7. PATCH call sites — snapshot/diff in the handler (no framework interface change)

Per-call metrics are derived from `AgentContext.metrics()` before/after the LLM call. **No new method on `AiFrameworkChatResponse`** — the existing cumulative-increment inside `Langchain4JAiFrameworkAdapter.executeChatRequest` is sufficient.

#### 7a. `AgentInitializerImpl.initiateGatewayToolDiscovery` (`agent/AgentInitializerImpl.java:109-132`)

Just before returning the `TOOL_DISCOVERY` `AgentResponseInitializationResult` with discovery tool calls, emit:

```java
agentInstanceClient.update(executionContext, agentContext,
    AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.TOOL_DISCOVERY));
```

Thread `executionContext` into `initiateGatewayToolDiscovery` (currently takes only `agentContext` + tool defs + results — extend the signature). `AgentInstanceClient` is already a constructor dep on `AgentInitializerImpl` — no extra wiring here.

#### 7b. `BaseAgentRequestHandler.processConversation` (`agent/BaseAgentRequestHandler.java:120-197`)

- Inject `AgentInstanceClient` as a new constructor param. Cascades through `JobWorkerAgentRequestHandler` + `OutboundConnectorAgentRequestHandler` + both bean methods in `AgenticAiConnectorsAutoConfiguration.java:264-280` & `298-314`.

- **Pre-LLM** (after `handleAddedUserMessages`, before the framework call). Snapshot baseline metrics, emit a **status-only** `THINKING` PATCH (no metrics ride on this one):

  ```java
  final var preLlmSnapshot = agentContext.metrics();
  agentInstanceClient.update(executionContext, agentContext,
      AgentInstanceUpdateRequest.statusOnly(AgentInstanceUpdateStatus.THINKING));
  ```

  Status-only ⇒ no delta = zero double-count risk on this PATCH under retry.

- **Post-LLM** (after `agentContext = frameworkChatResponse.agentContext()`). Read the LLM's tool call requests, bump cumulative `toolCalls` on `agentContext`, compute the full per-iteration delta via snapshot/diff, PATCH status + delta in one call:

  ```java
  final var assistantToolCalls = frameworkChatResponse.assistantMessage().toolCalls();
  final int toolCallsDelta = assistantToolCalls == null ? 0 : assistantToolCalls.size();
  if (toolCallsDelta > 0) {
    agentContext = agentContext.withMetrics(
        agentContext.metrics().incrementToolCalls(toolCallsDelta));
  }
  final var postLlmDelta = agentContext.metrics().minus(preLlmSnapshot);
  final var nextStatus = assistantToolCalls == null || assistantToolCalls.isEmpty()
      ? AgentInstanceUpdateStatus.IDLE
      : AgentInstanceUpdateStatus.TOOL_CALLING;
  agentInstanceClient.update(executionContext, agentContext,
      AgentInstanceUpdateRequest.builder().status(nextStatus).delta(postLlmDelta).build());
  ```

  `postLlmDelta` carries `modelCalls=1`, the call's `inputTokens`/`outputTokens`, AND `toolCalls=N` where N is the count of tool calls the LLM emitted — one consolidated PATCH per iteration.

Centralizing in the base handler covers both flavors (`JobWorkerAgentRequestHandler`, `OutboundConnectorAgentRequestHandler`) without per-handler code.

### 8. Wiring

- `AgentErrorCodes`: add `ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED`.
- `AgenticAiConnectorsAutoConfiguration.aiAgentOutboundConnectorAgentRequestHandler(...)` (line 264) and `aiAgentJobWorkerAgentRequestHandler(...)` (line 298): add `AgentInstanceClient agentInstanceClient` arg; forward to the new handler constructors.
- Both handler constructors accept the extra param and pass it to `super(...)`.

## Critical files

| File                                                              | Change                                                    |
|-------------------------------------------------------------------|-----------------------------------------------------------|
| `model/AgentMetrics.java` (+ nested `TokenUsage`)                 | add `toolCalls`; add `incrementToolCalls` + `minus` (and `TokenUsage.minus`) |
| `agentinstance/AgentInstanceUpdateRequest.java`                   | **new** record + builder + `statusOnly()`                 |
| `agentinstance/AgentInstanceClient.java`                          | add `update(...)`                                          |
| `agentinstance/AgentInstanceErrorClassifier.java`                 | parameterize: `FOR_CREATE` / `FOR_UPDATE`                 |
| `agentinstance/CamundaAgentInstanceClient.java`                   | implement `update(...)`; switch `create()` to `FOR_CREATE` |
| `aiagent/agent/AgentErrorCodes.java`                              | add `ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED`             |
| `agent/AgentMessagesHandler.java`                                 | `addUserMessages` returns `List<Message>` (partitioning stays internal) |
| `agent/AgentMessagesHandlerImpl.java`                             | partitioning logic inlined in `buildToolCallResultMessage` |
| `agent/AgentInitializerImpl.java`                                 | emit `TOOL_DISCOVERY` PATCH; thread `executionContext` into `initiateGatewayToolDiscovery` |
| `agent/BaseAgentRequestHandler.java`                              | inject `AgentInstanceClient`; `toolCallsDelta` from `assistantMessage.toolCalls()`; pre-/post-LLM PATCHes via snapshot/diff |
| `agent/JobWorkerAgentRequestHandler.java`                         | constructor passthrough                                   |
| `agent/OutboundConnectorAgentRequestHandler.java`                 | constructor passthrough                                   |
| `autoconfigure/AgenticAiConnectorsAutoConfiguration.java`         | thread `agentInstanceClient` through both handler beans   |
| `connectors-e2e-test-agentic-ai/.../connector/L4JAiAgentConnectorAgentInstanceRoundtripTests.java`   | **new** `@Disabled` roundtrip (task flavor)        |
| `connectors-e2e-test-agentic-ai/.../jobworker/L4JAiAgentJobWorkerAgentInstanceRoundtripTests.java`   | **new** `@Disabled` roundtrip (sub-process flavor) |
| `connectors-e2e-test-agentic-ai/.../BaseAgenticAiTest.java`                                          | shared `assertAgentInstanceStatusAndMetrics(...)` helper |

**Notable shifts vs earlier draft:** no separate `MetricsDelta` record (reuses extended `AgentMetrics`); no `perCallMetrics()` on `AiFrameworkChatResponse` (handler snapshots cumulative metrics before LLM and diffs after); `toolCalls` counts LLM-requested tool calls (`assistantMessage.toolCalls().size()`) not resolved results — partition extraction was reverted as it had no consumer once the metric source moved to the LLM response.

## Unit tests

- `AgentMetricsTest` — `toolCalls` validation, `incrementToolCalls`, `minus` (and `TokenUsage.minus`).
- `AgentMessagesHandlerImplTest` — `addUserMessages` returns `List<Message>`; partitioning behavior (complete/partial/interrupt) verified end-to-end through message construction.
- `AgentInstanceUpdateRequestTest` — builder + `statusOnly()` shape.
- `AgentInstanceErrorClassifierTest` — extend with `FOR_UPDATE`: 404 → PERMANENT; rest of matrix identical to `FOR_CREATE`.
- `CamundaAgentInstanceClientTest`:
  - `update(...)` short-circuits silently when `agentContext.metadata().agentInstanceKey()` is null.
  - `update(...)` builds command with status + non-zero delta fields only; calls `execute()`.
  - retry-exhausted path throws `ConnectorException` with `ERROR_CODE_AGENT_INSTANCE_UPDATE_FAILED` (mirror existing `create()` retry tests).
- `AgentInitializerTest` — `update(_, _, statusOnly(TOOL_DISCOVERY))` invoked when gateway discovery dispatches; not invoked on non-gateway paths.
- Handler-level test — extend existing `BaseAgentRequestHandler` tests with Mockito `InOrder` on `AgentInstanceClient`: pre-LLM PATCH (`THINKING` + correct `toolCalls` delta) precedes post-LLM PATCH (`TOOL_CALLING`/`IDLE` + correct LLM deltas); partial-result activations emit zero PATCHes.

## E2E roundtrip tests (disabled — gated on engine read API)

Dedicated test classes that drive the full BPMN flow and then use `camundaClient` to fetch the AgentInstance from the engine and assert its final status + cumulative metrics. Mirrors the previous structure of `L4JAiAgent{Connector,JobWorker}AgentInstanceTests` (collapsed by commit c362fbf) but with a stronger contract — engine round-trip, not just `agentResponse.context().metadata().agentInstanceKey()` presence.

**New files:**
- `connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/langchain4j/connector/L4JAiAgentConnectorAgentInstanceRoundtripTests.java`
- `connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/langchain4j/jobworker/L4JAiAgentJobWorkerAgentInstanceRoundtripTests.java`

**Pattern per class:**
- Extends the existing `BaseL4JAiAgent{Connector,JobWorker}Test` (inherits mocked LLM driven by `Langchain4JAiAgentToolSpecifications` and BPMN fixtures).
- One happy-path test: process triggers (i) one tool call and (ii) a final LLM response. After process completion via `CamundaAssert`/`ZeebeTest`:
  1. Read `agentInstanceKey` from final `agentResponse.context().metadata()`.
  2. Use `camundaClient` to fetch the AgentInstance via the read API once it ships (e.g. `camundaClient.newGetAgentInstanceCommand(agentInstanceKey).execute()`). Leave `// TODO(agent-instance-read)` markers.
  3. Assert: `status == IDLE`, `modelCalls > 0`, `inputTokens > 0`, `outputTokens > 0`, `toolCalls == 1`.
- Class-level `@Disabled("gated on engine AgentInstance read API — pending API team merge (camunda/camunda follow-up to #53528)")`.

**Helper:** add a single shared assertion helper on `BaseAgenticAiTest`:
```java
protected void assertAgentInstanceStatusAndMetrics(long agentInstanceKey,
    ThrowingConsumer<? /* AgentInstance DTO from engine */> assertions) { /* TODO(agent-instance-read) */ }
```
DTO type wires in once the engine read command ships.

## Verification

SDK surface verified locally on `camunda-client-java:8.10.0-SNAPSHOT` (built from `camunda/camunda` main after #53528 merged): `CamundaClient.newUpdateAgentInstanceCommand(long)`, `UpdateAgentInstanceCommandStep1`/`Step2`, `AgentInstanceUpdateStatus`, `UpdateAgentInstanceResponse` all resolve. No further dependency bump needed.

1. **Unit tests** — `mvn test -pl connectors/agentic-ai`. All new + updated tests pass; existing tests pass.
2. **E2E sanity** — `mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -Dtest='*L4JAiAgent*'`. New `*AgentInstanceRoundtripTests` skip cleanly (`@Disabled`); pre-existing L4J tests still pass with real PATCHes hitting the test engine.
3. **Manual happy-path** — run one e2e L4J test with DEBUG logging on `io.camunda.connector.agenticai.aiagent.agentinstance` to observe the PATCH sequence (`TOOL_DISCOVERY`? → `THINKING` → `TOOL_CALLING|IDLE`).
4. **Follow-up readiness** — when the engine read API ships:
   - Wire the engine read command into `assertAgentInstanceStatusAndMetrics(...)`.
   - Remove the class-level `@Disabled` from the roundtrip tests.

## Out of scope (explicit follow-ups)

- **Idempotency-Key on PATCH** — deduplicates phantom deltas under job retry / supersession. Sync PATCHes today **over-count** on retried/superseded attempts (engine reports more than `agentContext` persisted, but matches real provider spend — chosen deliberately for cost fidelity). Idempotency-key resolves the inflation while preserving cost fidelity. The idempotency follow-up will also need to surface the partition (processed-result IDs) from `AgentMessagesHandlerImpl` to persist a "last-reported" ledger in `AgentContext`.
- **"Last-reported" ledger in `AgentContext`** for delta reconciliation across retries.
- **Engine-side AgentInstance read API** — required to enable the roundtrip tests.
- **Pushing `tools(...)` on the update command** — engine supports it; not needed yet.
