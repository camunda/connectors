# Data-Driven Agent Initialization Verdicts and the `AgentConversation` Aggregate

* Deciders: Agentic AI Team
* Date: Jun 5, 2026

## Status

**Proposed**

## Context and Problem Statement

The AI Agent connector's per-turn initialization was complex and procedural. `AgentInitializer`
returned a sealed `AgentInitializationResult` whose three variants
(`AgentContextInitializationResult`, `AgentResponseInitializationResult`,
`AgentDiscoveryInProgressInitializationResult`) were named after their payload shape rather than any
lifecycle meaning, and two of the three collapsed to the same handler action. More importantly, the
result carried **technical objects** the initializer had no business building: an `AgentResponse`
(connector-output shaping) and an `AgentJobCompletionListener` (engine status-update concern). So the
initializer reached into response assembly (`AgentResponse.builder()`, `ToolCallProcessVariable`) and
agent-instance status patching — concerns the handler already owns for the sibling transitions
(`THINKING`, `TOOL_CALLING`, `IDLE`, metrics).

Separately, the agent's working state for a turn was scattered across three layers with no domain
object tying them together: `AgentContext` (durable, persisted state), the inbound
`toolCallResults` (transient per-turn input from the engine), and `RuntimeMemory` (the message
working-set). The orchestration threaded these as loose locals through `BaseAgentRequestHandler`.

How should we express the beginning of the agent lifecycle in a domain-oriented way, without leaking
technical response/engine concerns into the initializer, and without changing runtime behavior or the
serialized `agentContext` contract?

## Decision Drivers

* **Domain-oriented expression**: the lifecycle should read as agent concepts — provision the agent
  instance, resolve tools, discover gateway tools, then converse — not as a payload-shaped state
  switch.
* **No layer leak**: the initializer should return *what it determined* (domain data), and the
  handler should own the *technical translation* (responses, listeners, the conversation phase).
* **A cohesive turn aggregate**: one object should represent "the agent's conversation as it stands
  this turn," restored from durable state + stored messages and primed with the engine's new results.
* **Behavior + contract preservation**: identical engine calls, listeners, no-op semantics, and
  migration handling; the serialized `AgentContext` / `AgentState` enum must not change.
* **Incremental adoption**: land the structural/state changes first; migrate turn behavior into the
  aggregate later to keep the change reviewable.

## Considered Options

1. **Keep** the three payload-shaped variants and the procedural orchestration.
2. **Collapse to two variants** that still carry `AgentResponse` + listener (a `ReadyToConverse` /
   `DeferConversation` pair). Simpler switch, but the technical-object leak remains.
3. **Data-driven verdicts + an `AgentConversation` aggregate**: the initializer returns pure-data
   verdicts; the handler builds responses/listeners; a transient `AgentConversation` aggregate carries
   the turn's working state.

A `JobData` wrapper bundling `agentContext` + `toolCallResults` was also considered and **rejected**:
the two are at different layers (durable persisted state vs. transient per-turn engine input), so
bundling them would unify them only superficially.

## Decision Outcome

Chosen option: **Option 3 — data-driven verdicts + an `AgentConversation` aggregate**, adopted in
**stages** (state aggregate now, turn behavior later).

### Core changes (Stage 1)

- `AgentInitializationResult` becomes three pure-data verdicts:
  - `ReadyToConverse(AgentContext agentContext, List<ToolCallResult> engineToolCallResults)`
  - `DiscoverTools(AgentContext agentContext, List<ToolCall> toolDiscoveryToolCalls)`
  - `DeferConversation()`  (no-op this turn while awaiting more results)
- `AgentResponse` construction and the `TOOL_DISCOVERY` completion listener move from
  `AgentInitializerImpl` into `BaseAgentRequestHandler`, co-located with the other agent-instance
  status transitions. The initializer keeps `agentInstanceClient` only for provisioning.
- `AgentInitializerImpl` reads as a lifecycle narrative — `provisionAgentInstance` → `beginToolDiscovery`
  → `dispatchGatewayToolDiscovery` → `completeToolDiscovery` → `resumeReadyAgent` — keeping the
  serialized `AgentState` enum (`INITIALIZING`/`TOOL_DISCOVERY`/`READY`) unchanged.
- New transient, non-serialized aggregate `AgentConversation`, built by
  `rehydrate(context, messageMemory, engineToolCallResults)`: `context` + `messageMemory` are
  *restored* from persisted state, while `engineToolCallResults` is *this turn's new engine input*
  primed to be folded into the conversation. `AgentContext` remains the sole serialized projection;
  the aggregate reduces back to it.
- `BaseAgentRequestHandler` switches over the three verdicts; the conversation phase rehydrates the
  aggregate inside the `ConversationSession` try-with-resources (the handler continues to own the
  session lifecycle).

### Consequences

**Positive:**
- The initializer is pure domain logic; technical response/engine concerns live in one place (the
  handler).
- The verdicts are self-describing and exhaustively switchable (sealed, no `default`).
- A single aggregate represents the turn's working state, replacing scattered locals and giving turn
  behavior a future home.
- No change to the serialized contract, migration detection, or the write-ahead/pointer memory
  invariant.

**Negative:**
- Listener-behavior tests move from the initializer layer to the handler layer.
- Two "conversation" vocabularies now coexist — `ConversationStore`/`Session`/`Context` (message
  persistence) vs. `AgentConversation` (the live turn aggregate); the distinction must be kept clear.

### Future improvements

- **Stage 2**: migrate turn behavior onto `AgentConversation` (folding `engineToolCallResults` into
  messages, the model call, persistence, response building), thinning `BaseAgentRequestHandler` to a
  driver; reconsider whether the aggregate should then own the `ConversationSession`.
- Update `docs/reference/ai-agent.md` and `connectors/agentic-ai/CLAUDE.md` to the new vocabulary.
