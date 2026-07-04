# Tool-result history item timestamps

* Deciders: Agentic AI Team
* Date: July 2, 2026

## Status

**Implemented**

## Context and Problem Statement

Tool-result history items are all stamped with `OffsetDateTime.now()` at the moment the *slowest* tool
in a turn finishes. `AgentConversationTurnInputComposerImpl` defers the turn (returns
`CompositionResult.Deferred`, worker no-ops) until *every* expected tool result has arrived; only then
does `BaseAgentRequestHandler.proceed()` call `createHistoryForInputMessages()`, which loops over the
turn's results and writes each one with `producedAt(OffsetDateTime.now())`
(`CamundaAgentInstanceClient.executeCreateHistoryItem`). A 2-second REST call and a 4-hour user task in
the same turn end up with identical `producedAt` timestamps, which is misleading in the history UI and
useless for deriving per-tool execution time.

## Decision Drivers

* **Accuracy**: `producedAt` on a `TOOL_RESULT` history item should reflect when that specific tool
  finished, not when the whole turn's slowest tool finished.
* **Coverage**: the fix must work for the AI Agent Sub-process (AHSP) flavor, but also degrade
  gracefully for the Task flavor and for diagrams still bound to older element template versions that
  never emit a per-tool completion timestamp.
* **Robustness**: the AHSP flavor deals with job supersession, redelivery and no-op/deferred jobs; any
  worker-side timestamp source must survive these without regressing or double-counting.
* **Simplicity**: prefer a stateless, engine-sourced timestamp where available; only fall back to
  worker-side bookkeeping where the engine cannot provide one.

## Considered Options

| Dimension | A — engine `now()` in outputElement | B — worker-observed, persisted first-seen |
|---|---|---|
| Measures | True tool-completion moment | When the worker first *received* a job carrying the result |
| Accuracy | High, exact per tool | Approx.; lags by job-create + activation + poll latency |
| Co-arriving results | Distinguished precisely | Distinguished only if seen on different jobs |
| Clock | Engine (Zeebe) | Connector host (same as existing `producedAt`) |
| State | Stateless — rides the result | Stateful — persisted first-seen map, written on no-op jobs |
| Robustness (supersession/redelivery/no-op) | Immune (fixed at completion) | Sensitive — needs keep-earliest + superseded-write care |
| Template change | Yes (sub-process only) | None |
| Coverage | AHSP tool elements (+AHSP gateway); **not** Task flavor | Broad, incl. Task flavor & gateway |
| Serialization risk | FEEL `now()` zone form may not parse to `OffsetDateTime` | None |
| Complexity | Low–medium | Medium–high |

**B (persisted first-seen) was implemented, then reverted.** It requires threading state through
`agentContext` and re-emitting it on the no-op/`Deferred` AHSP path (which previously emitted no
variables at all) purely so a later job can recover an earlier job's observation. That is a
meaningful amount of surface area and failure modes (superseded-write tolerance, keep-earliest
correctness, pruning stale entries) for a fallback that only ever applies to cases the recommended
AHSP + v11 template combination doesn't hit (Task flavor, non-AHSP gateway results, pre-v11
diagrams). Decided the stateless fallback (plain `now()` at resolution time, no persistence) is the
right tradeoff — see Decision Outcome.

Rejected / deferred: **Eager push** (write each history item as it arrives, rather than only once the
turn is complete). This is heavier (needs history-item idempotency, since the dominant duplicate source
is the accumulating `toolCallResults` list across genuinely distinct jobs, which the job-lease concept
(camunda/camunda#54840) does not dedupe). Timestamp-on-result fixes *accuracy* but not *visibility*
(the item is still created only once the slow tool finishes); eager push is the visibility fix,
deferred to parent epic #7595.

## Decision Outcome

**A primary → stateless `now()` fallback**, resolved at the earliest ingestion point so every
`ToolCallResult` carries a concrete completion time (`completedAt`) before it flows into the history
path. The mapper and `CamundaAgentInstanceClient` take a required (non-null) `producedAt` — no nullable
plumbing and no `now()` fallback scattered downstream.

- **A — engine timestamp via AHSP `outputElement`**: the tool element's output mapping FEEL expression
  now also emits `completedAt: now()`. The engine stamps the true completion moment; it rides the
  `ToolCallResult`. Primary source, ships in the AI Agent Sub-process element template v11.
- **`now()` fallback**: for results lacking an engine timestamp (Task flavor, non-AHSP gateway
  results, migrated/in-flight AHSP instances, cancelled results, events, and diagrams still bound to
  v10 or earlier), the worker stamps `OffsetDateTime.now()` at the moment ingestion normalization
  resolves the result — computed fresh on every job, **not persisted anywhere** (no `agentContext`
  state, no no-op-path plumbing). See Negative Consequences for what this costs.

### Serialization risk (A) — confirmed and resolved

Traced end to end (feel-scala + camunda/camunda sources, plus a local JVM repro): FEEL's `now()`
resolves through `ZeebeFeelEngineClock.getCurrentTime()`
(`clock.instant().atZone(ZoneId.systemDefault())`). `ZoneId.systemDefault()` resolves to a named region
(e.g. `Etc/UTC`, `Europe/Berlin`) on essentially every real broker deployment, never a bare
`ZoneOffset`. The resulting value is serialized via
`FeelToMessagePackTransformer.writeStringValue(ISO_ZONED_DATE_TIME.format(...))`, so the string that
lands in `toolCallResult.completedAt` looks like
`2026-07-02T11:55:00.522622+02:00[Europe/Berlin]`. Bare `OffsetDateTime.parse(text)` throws
`DateTimeParseException` on that string on every non-degenerate deployment.
`OffsetDateTime.parse(text, DateTimeFormatter.ISO_ZONED_DATE_TIME)` parses it correctly, dropping the
zone id and keeping the offset. `ToolCallResult.completedAt` is deserialized with this formatter; no
raw-`String` fallback field or FEEL-expression normalization is needed.

### Considered and rejected: patching already-released element template versions in place

Investigated and rejected editing v10's already-released JSON in place instead of (or in addition to)
bumping to v11. Element templates are baked into the BPMN's `extensionElements` at model time; the
engine never re-resolves the template at runtime. Editing v10's file has zero effect on any
already-deployed, already-running, or already-completed process — only a diagram (re-)modeled after
the edit would pick it up, and since v11 (carrying the real fix) ships in the same change, the only
window an in-place v10 edit could matter is between the patch landing and v11 being available to
modelers, i.e. never. It would also break the version-immutability contract a released template's file
is otherwise frozen (see `AGENTS.md` §"Element templates"), which Web Modeler's version diff/upgrade UX
relies on. Decision: do not touch v10's file; ship `completedAt` only in the new v11. No backfill of
historical `producedAt` data on already-persisted history items is attempted.

### Positive Consequences

- `TOOL_RESULT` history items carry the true per-tool completion time on the (recommended) AHSP flavor,
  and a reasonable approximation everywhere else, instead of a shared turn-end timestamp.
- No nullable timestamp plumbing: `producedAt` is resolved once, early, and carried as a non-null value
  through the mapper and client.

### Negative Consequences

- Task flavor and pre-v11 AHSP diagrams never get the accurate (A) timestamp. For an AHSP round that
  spans multiple no-op jobs, results without an engine timestamp can still collapse onto the
  resolving job's shared `now()` — the same inaccuracy this ADR set out to fix, just for a narrower
  set of cases (everything not covered by A). Accepted in exchange for zero added state.

## Out of Scope

- Backfilling `producedAt` on history items already persisted before this fix ships — those records
  keep their (inaccurate) turn-end timestamp permanently.
- Editing v10 (or any other already-released template version) in place — see rejected alternative
  above.
- Persisting worker-observed timestamps across jobs (the reverted B design above) — the fallback path
  is intentionally stateless.
- Eager push + history-item idempotency (the visibility fix) — parent epic #7595, revisit with the job
  lease concept (camunda/camunda#54840).
- Deriving completion time from queryable engine element-instance data per tool, if it becomes
  available.
