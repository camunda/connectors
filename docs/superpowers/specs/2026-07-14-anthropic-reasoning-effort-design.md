# Capability Reshape + Native Anthropic Reasoning (thinking) & Effort — Design Spec

**Status:** Draft for review · **Date:** 2026-07-14 · **Epic:** #7211 (Own the LLM layer, vertical pilot) · related UX epic #7669, custom-provider SPI #5547

**Goal:** (R1) Add first-class reasoning configuration to the native Anthropic path — the two
orthogonal axes Anthropic exposes (**thinking** and **effort**) on the request side, plus a lossless
provider-neutral reasoning trace on the response side that round-trips back to the API byte-identical.
This forces (R0) a cleaner capability model: turn `ModelCapabilities` into a **neutral interface**
implemented by **provider-specific capability records**, so provider-specific data (reasoning,
caching, parallel-tool-calls) lives where it's consumed.

**Architecture (one sentence):** Provider-specific *request* config (typed to Anthropic's wire shape,
capability-gated), provider-neutral *response* data (`ReasoningContent` carrying the raw block for
lossless replay), and provider-specific *capability records* behind a minimal neutral interface;
the reasoning descriptor drives fail-fast validation.

## Chunking (two stacked chunks)

- **R0 — Capability model reshape** (section "R0" below). Behaviour-identical refactor:
  `ModelCapabilities` becomes a neutral interface + provider-specific records; the resolver's final
  materialisation is parameterised by capability class. No new features; wire output unchanged; tests
  stay green. Lands first as its own PR.
- **R1 — Anthropic reasoning & effort** (§2–§9). Stacks on R0: `thinking` + `effort` config, matrix
  reasoning descriptor as typed fields on the Anthropic capability record, fail-fast validation, and
  the `ReasoningContent` byte-identical round-trip.

---

## 1. Background & grounding

Two **orthogonal** axes, both confirmed against `anthropic-java` 2.48.0 (beta messages client) and the
Anthropic docs (`/build-with-claude/effort`, `/extended-thinking`, `/adaptive-thinking`):

### 1a. Thinking — the reasoning *mechanism*
`BetaThinkingConfigParam` is a **3-way union** (top-level `thinking` request field):
- `ofEnabled(BetaThinkingConfigEnabled)` — manual extended thinking with a fixed `budget_tokens` (long).
- `ofAdaptive(BetaThinkingConfigAdaptive)` — model manages its own budget; optional `display`.
- `ofDisabled(BetaThinkingConfigDisabled)` — off.

### 1b. Effort — a general "how hard should the model work" dial
`BetaOutputConfig.effort`, wire location **`output_config.effort`**, GA (no beta header), **typed**
in the beta client. Enum `BetaOutputConfig.Effort` = `LOW, MEDIUM, HIGH, XHIGH, MAX` (open enum:
`Effort.of(String)` serializes unknown values verbatim). Default `HIGH` (== omitting). Affects **all**
token spend (text, tool calls, and thinking) — **not reasoning-specific**. On adaptive models, effort
is the recommended control for thinking depth.

### 1c. Model-gated support (the reason validation matters)
Manual `thinking:{enabled,budget_tokens}` is a hard **400** on the flagship models; `disabled` is a
**400** on always-on models; effort is unsupported on some models. Curated per-model in the matrix:

| Model (matrix pattern) | thinking modes | effort | notes |
|---|---|---|---|
| `claude-opus-4-6/4-7/4-8`, `claude-sonnet-5` | `adaptive`, `disabled` | `low..max` (+`xhigh`) | manual budget → 400 |
| `claude-sonnet-4-6` | `enabled`(dep.), `adaptive`, `disabled` | `low..max` | budget deprecated but accepted |
| `claude-opus-4-5` | `enabled`, `disabled` | `low,medium,high,max` | manual budget + effort |
| `claude-sonnet-4-5` | `enabled`, `disabled` | — | reasoning via budget; **no effort** |
| `claude-haiku` | `enabled`, `disabled` | — | reasoning via budget; **no effort** |
| `claude-fable-*`, mythos | (always-on) | `low..max` | `disabled` → 400 |

> **Design principle (unchanged from the pilot):** provider-specific *in*, neutral *out*. There is no
> honest neutral request-config shape across providers (Anthropic = budget|adaptive|off; OpenAI =
> effort levels; Gemini = budget|dynamic). Neutrality lives only on the response side.

### 1d. Interleaved thinking — no work required on request structure
Interleaved thinking (new thinking between tool calls) is **automatic** on the adaptive models and
**beta-header-gated** (`interleaved-thinking-2025-05-14`) on Opus 4.5 / earlier Claude 4.
**Decision: we do NOT add the beta header.** Newer models get adaptive thinking (automatic
interleaving); older models get plain manual-budget extended thinking. No header logic in the pilot.

The Anthropic rule — *"the entire sequence of consecutive thinking blocks must match the model's
output; you can't rearrange or modify them; signatures required; modification → 400"* — is satisfied
by our architecture without a structural change, because:
- Interleaving manifests **across** our per-call `AssistantMessage`s (each API call = one assistant
  message; `thinking₂` depends on `tool_result₁`, which only exists after the next call). Within a
  single response the order is always `thinking* → text → tool_use*`, never `tool_use → thinking`.
- `AnthropicMessageRequestConverter.assistantParam` already emits content blocks (reasoning, text)
  **before** tool_use blocks — the required order.
- The only thing missing is the **byte-identical round-trip** of thinking blocks (§4), which we add.

---

## R0 — Capability model reshape (prerequisite chunk)

**Problem.** `ModelCapabilities` is today a flat *neutral* record carrying provider-specific flags
(`supportsReasoningSignatureRoundtrip`, `supportsParallelToolCalls`, `supportsPromptCaching`, …). But
the only **cross-provider** consumer is `CapabilityAwareToolCallResultStrategy`, and it reads
**modalities only**. Every other field is consumed inside exactly one provider impl:

| Consumer | Reads | Generic? |
|---|---|---|
| `CapabilityAwareToolCallResultStrategy` | modalities | **yes** (native + bridge) |
| `AnthropicMessageRequestConverter` | `maxOutputTokens` (+ reasoning, R1) | provider-internal |
| generic request handler | nothing (`capabilities()` unused above SPI) | — |

Forcing R1's provider-specific reasoning data through this neutral record would need an opaque
`Map` escape hatch — a smell. Instead: **split the neutral contract from provider-specific data.**

**Design.** `ModelCapabilities` becomes a **neutral interface** exposing only the generic contract;
each provider ships its **own record** implementing it plus its provider-specific fields:

```java
// Neutral — the cross-provider contract generic code depends on (today: modalities only)
public interface ModelCapabilities {
  List<Modality> userMessageModalities();
  List<Modality> toolResultModalities();
  List<Modality> assistantMessageModalities();
}

// Common non-contract data (provider-agnostic, but no generic consumer yet) — shared component
public record CoreModelCapabilities(
    List<Modality> userMessageModalities, List<Modality> toolResultModalities,
    List<Modality> assistantMessageModalities,
    @Nullable Integer contextWindow, @Nullable Integer maxOutputTokens) {}

// Anthropic-owned — implements the contract + everything only Anthropic consumes
public record AnthropicModelCapabilities(
    CoreModelCapabilities core,                       // delegate the interface methods to core
    boolean supportsReasoning,
    @Nullable AnthropicReasoningCapabilities reasoning,   // R1: typed fields, NOT an opaque Map
    boolean supportsPromptCaching) implements ModelCapabilities { /* delegate to core */ }
```

**Resolver.** `ModelCapabilitiesResolverImpl` already does the whole deep-merge cascade in `JsonNode`
and only materialises a typed object at the very last step. Parameterise **just that step** by a
provider capability class + a per-provider conservative-default tree:
`resolve(family, model, backend, override, Class<T extends ModelCapabilities>)` →
`treeToValue(merged, T.class)`. The merge cascade stays provider-agnostic; only the leaf schema is
provider-specific. Matrix YAML is already family-namespaced, so each family carries its own keys.

**`capabilities()` returns the neutral interface** (unchanged signature). Generic code (the strategy)
depends on the interface; each provider's internal converter receives its concrete record. The
LangChain4j bridge gets a trivial neutral impl (its current `BRIDGE_CAPABILITIES`, modalities only).

**Custom-provider extensibility (design goal).** A consumer registers their own `ChatModelApiFactory`
bean (`getOrder() < 1000` to beat the bridge); `create()` returns their `ChatModelApi`, whose
`capabilities()` returns a capability class of *their own* implementing the neutral interface — with
whatever provider-specific fields they need, resolved however they like (our resolver is opt-in, not
a chokepoint). Three **invariants** this reshape must hold (verified in R0 review):

1. **Neutral interface stays minimal** — only what a generic consumer reads today (modalities). Every
   custom provider must implement it, so every added method is a tax on all of them.
2. **No generic code downcasts `ModelCapabilities`** — generic code uses interface methods only; an
   `instanceof AnthropicModelCapabilities` in generic code would break custom providers.
3. **Resolver stays optional** — a provider can supply capabilities without our resolver/YAML.

SPI-evolution rule: anything added to the neutral interface later is a `default` method with a
sensible fallback, so existing custom impls keep compiling. (This does not deliver the full
custom-provider SPI #5547 — config-discriminator registration, template story — it makes the
capability half clean and doesn't preclude it.)

**What R0 cleans up:** the R1 opaque-`Map` reasoning slot disappears (typed fields instead); the
provider-specific flags move to where they're consumed (dissolving landing follow-up #3's
"declared-but-unused neutral fields"); the framework-agnostic-core invariant is honored (no provider
vocabulary on the neutral type). Behaviour-identical: same resolved values, same wire output.

---

## 2. Config surface (request side)

Lives on **`AnthropicModelParameters`** (nested `anthropic.model.parameters`, alongside
`maxTokens`/`temperature`/`topP`/`topK`). Two new fields, both optional and capability-gated.

```java
public record AnthropicModelParameters(
    @Nullable Integer maxTokens,
    @Nullable Double temperature,
    @Nullable Double topP,
    @Nullable Integer topK,
    @Valid @Nullable AnthropicThinking thinking,   // NEW — the reasoning mechanism
    @Nullable AnthropicEffort effort,              // NEW — general effort dial (sibling, not nested)
    @Nullable String customEffort) {}              // NEW — free-text when effort == CUSTOM

// Thinking sub-object — 1:1 with BetaThinkingConfigParam
public record AnthropicThinking(
    @NotNull ThinkingMode mode,             // ENABLED | ADAPTIVE | DISABLED
    @Min(1024) @Nullable Integer budgetTokens,   // required + only used when ENABLED
    @Nullable ThinkingDisplay display) {}   // ADAPTIVE only; optional (default omit)

public enum ThinkingMode { ENABLED, ADAPTIVE, DISABLED }
public enum ThinkingDisplay { /* TODO(verify): BetaThinkingConfigAdaptive.Display enum values */ }

// Effort enum — Anthropic-specific (NOT shared with OpenAI). CUSTOM = escape hatch.
public enum AnthropicEffort { LOW, MEDIUM, HIGH, XHIGH, MAX, CUSTOM }
```

**Modeler / element-template behaviour** (`@TemplateProperty`):
- `thinking.mode`: dropdown `{ENABLED, ADAPTIVE, DISABLED}`, optional (unset ⇒ send no `thinking`
  param ⇒ model default).
- `thinking.budgetTokens`: number, shown only when `mode == ENABLED` (`PropertyCondition`).
- `thinking.display`: dropdown, shown only when `mode == ADAPTIVE`, optional.
- `effort`: dropdown `{LOW,MEDIUM,HIGH,XHIGH,MAX,CUSTOM}`, optional.
- `customEffort`: free-text, shown only when `effort == CUSTOM` (mirrors the `webSearchVersion`
  conditional-field pattern).

**Rationale for CUSTOM + free-text:** `BetaOutputConfig.Effort` is an open enum, so a future level
(or a value the enum doesn't know) can be sent verbatim via `Effort.of(customEffort)` — same
forward-compat escape hatch we use for web-tool versions. `CUSTOM` bypasses matrix validation.

> **Config keys are provider-specific.** OpenAI (later chunk) gets its own `reasoning_effort`-shaped
> config; there is deliberately **no** generic reasoning-config abstraction.

---

## 3. Capability matrix — reasoning descriptor

Add a `reasoning` descriptor per model under the `anthropic-messages` family, **alongside** (not
replacing) the existing `supports-reasoning` / `supports-reasoning-signature-roundtrip` booleans
(kept — data model unchanged, additive only).

```yaml
anthropic-messages:
  models:
    claude-opus-4-6-plus:
      pattern: [claude-opus-4-6*, claude-opus-4-7*, claude-opus-4-8*]
      capabilities:
        supports-reasoning: true
        supports-reasoning-signature-roundtrip: true
        reasoning:
          thinking-modes: [adaptive, disabled]        # NO 'enabled' → manual budget 400s here
          effort-levels:  [low, medium, high, xhigh, max]
    claude-opus-4-5:
      capabilities:
        reasoning:
          thinking-modes: [enabled, disabled]
          effort-levels:  [low, medium, high, max]
    claude-sonnet-4-5:
      capabilities:
        reasoning:
          thinking-modes: [enabled, disabled]         # no effort-levels ⇒ effort unsupported
```

**Keys are provider-specific per API family (decided).** A generic `{thinking-modes, effort-levels}`
shape shared across providers was rejected: it won't stay future-proof as providers add reasoning
concepts that don't generalise (e.g. Gemini's `thinkingBudget` with `-1` = dynamic, or a novel
control). Each provider models exactly its own concepts. Given **R0**, this is clean: the
`anthropic-messages` family's `reasoning:` block deserialises **directly into typed fields** on
`AnthropicModelCapabilities` via the parameterised resolver — no opaque `Map` slot, no per-provider
`convertValue` hop:

```java
// Anthropic package — R1 adds this field to AnthropicModelCapabilities (see R0), Anthropic's OWN enums
public record AnthropicReasoningCapabilities(
    @JsonProperty("thinking-modes") List<ThinkingMode> thinkingModes,   // ENABLED|ADAPTIVE|DISABLED
    @JsonProperty("effort-levels")  List<AnthropicEffort> effortLevels) {}  // Anthropic effort enum
```

- A future OpenAI family defines **different keys** in its own capability record (e.g.
  `reasoning-effort`, `reasoning-budget`) — no shared vocabulary imposed, no change to the neutral
  interface. That is exactly what R0's provider-specific records buy.
- The resolver's deep-merge is unchanged (the `reasoning` object merges recursively; a user
  `capabilityOverride` can override it too); only the final `treeToValue` targets the provider record.
- **Sourcing:** `supports-reasoning` comes from models.dev `reasoning` (bool); models.dev's
  `reasoning_options` is **empty for all Claude models**, so the Anthropic descriptor is hand-curated
  (as the rest of the Anthropic matrix data already is). OpenAI's family descriptor can later map from
  models.dev `reasoning_options`.

---

## 4. Response side — `ReasoningContent` & lossless round-trip

### 4a. `ReasoningContent` shape (unchanged fields, new payload semantics)
`ReasoningContent(text, providerPayload, metadata)` stays as-is structurally. Change what
`providerPayload` holds: **the full raw thinking / redacted_thinking block as a plain JSON `Map`**
(Option A — mirrors `ProviderContent`), not today's bare signature string.

- `text` = neutral human-readable surface (the thinking text; `null` for `redacted_thinking`). Used
  for display and agent-instance history.
- `providerPayload` = the raw block Map (`{type, thinking, signature}` or `{type:"redacted_thinking",
  data}`) — the authoritative source for byte-identical replay. (Minor: the thinking text is
  duplicated in `text`; the payload is the round-trip source of truth.)

Response converter (`AnthropicMessageResponseConverter`) change:
```java
} else if (block.isThinking()) {
  final var t = block.thinking().orElseThrow();
  final Map<String,Object> raw = ObjectMappers.jsonMapper().convertValue(block, MAP_TYPE);
  content.add(new ReasoningContent(t.thinking(), raw, null));
} else if (block.isRedactedThinking()) {
  final Map<String,Object> raw = ObjectMappers.jsonMapper().convertValue(block, MAP_TYPE);
  content.add(new ReasoningContent(null, raw, null));
}
```

### 4b. Request-side re-emission (closes the one-directional gap)
`AnthropicContentConverter.toContentBlockParams` — replace `case ReasoningContent ignored -> {}`:
```java
case ReasoningContent rc -> {
  if (signatureRoundtripSupported && rc.providerPayload() != null) {
    blocks.add(ObjectMappers.jsonMapper()
        .convertValue(rc.providerPayload(), BetaContentBlockParam.class));
  }
  // else: skip (model has no signature round-trip, or no payload) — history replay stays valid
}
```
- `signatureRoundtripSupported` = `capabilities.supportsReasoningSignatureRoundtrip()`. The converter
  needs access to this flag (thread it through from the request converter / capabilities).
- **Why Option A / byte-identical:** the *"cannot be modified/rearranged"* rule means a rebuilt block
  (Option B) risks a 400 from field-order/whitespace drift; replaying the stored bytes cannot drift.
  Also generalises to OpenAI's encrypted reasoning items with zero new vocabulary on the neutral type.
- **Ordering** already correct (`assistantParam` emits content before tool_use, §1d). Multiple
  consecutive thinking blocks and `redacted_thinking` are preserved as ordered `ReasoningContent`s.

### 4c. Round-trip is required, not optional
On adaptive-thinking + tool use (the mainline for flagship models), the signed thinking block **must**
be replayed with the tool_use or the next call 400s. So §4b is a correctness requirement for those
models, gated by `supportsReasoningSignatureRoundtrip` (true for all reasoning-capable Claude models
in the matrix).

---

## 5. Request mapping (thinking + effort → SDK)

In `AnthropicMessageRequestConverter.toMessageCreateParams`, after validation (§6):
```java
// thinking
switch (thinking.mode()) {
  case ENABLED  -> builder.thinking(BetaThinkingConfigParam.ofEnabled(
                      BetaThinkingConfigEnabled.builder().budgetTokens(thinking.budgetTokens()).build()));
  case ADAPTIVE -> builder.thinking(BetaThinkingConfigParam.ofAdaptive(
                      adaptiveBuilder(thinking.display())));   // display optional
  case DISABLED -> builder.thinking(BetaThinkingConfigParam.ofDisabled(
                      BetaThinkingConfigDisabled.builder().build()));
}
// effort → output_config.effort
final BetaOutputConfig.Effort eff = (effort == CUSTOM)
    ? BetaOutputConfig.Effort.of(customEffort)
    : BetaOutputConfig.Effort.of(effort.name().toLowerCase());   // LOW→"low", XHIGH→"xhigh", …
builder.outputConfig(BetaOutputConfig.builder().effort(eff).build());
```
Unset thinking ⇒ no `thinking` param. Unset effort ⇒ no `output_config` ⇒ model default (`high`).

**`max_tokens` interaction (document, no code):** effort affects tokens spent within `max_tokens`
(thinking + output share the budget). At `xhigh`/`max`, Anthropic recommends a large `max_tokens`
(≈64k+). No clamping (per the pilot's "no validation" call for max_tokens); documented in the
element-template tooltip.

---

## 6. Validation (matrix-driven, fail-fast)

Before the API call, validate config against the `AnthropicReasoningCapabilities` deserialized from
`ModelCapabilities.reasoning` (§3). Each failure throws a `ConnectorException` with a clear,
actionable message (no opaque API 400s):

1. `thinking` set but `supports-reasoning == false` → fail (reasoning unsupported).
2. `thinking.mode ∉ reasoning.thinkingModes` → fail (e.g. `ENABLED` on Opus 4.8 →
   *"manual thinking budget not supported by <model>; use ADAPTIVE + effort"*).
3. `thinking.mode == ENABLED && budgetTokens == null` → fail (budget required).
4. `effort` set (non-`CUSTOM`) but `reasoning.effortLevels` empty/absent → fail (effort unsupported
   on this model).
5. `effort` set (non-`CUSTOM`) and `effort ∉ reasoning.effortLevels` → fail.
6. `effort == CUSTOM` → **bypass** validation (sent verbatim via `Effort.of`).

Unknown/unmatched model (matrix miss → conservative defaults, `reasoning` absent): treat as "no
descriptor" — either pass-through (let the API decide) or fail on any reasoning config. **Recommend
pass-through** here so custom/unknown models remain usable (consistent with the capability-override
escape hatch).

---

## 7. Scope

**In scope — R0 (reshape chunk):** `ModelCapabilities` → neutral interface; `CoreModelCapabilities`
shared component; `AnthropicModelCapabilities` provider record; parameterised resolver
(`resolve(..., Class<T>)`) + per-provider conservative defaults; LangChain4j bridge neutral impl;
rewire `CapabilityAwareToolCallResultStrategy` + `capabilities()` producers to the interface; the
three custom-provider invariants. Behaviour-identical — existing resolver/strategy tests stay green,
no wire change.

**In scope — R1 (reasoning + effort):** Anthropic `thinking` + `effort` request config;
`AnthropicReasoningCapabilities` typed fields on the R0 record + hand-curated matrix descriptor;
fail-fast validation; `ReasoningContent` payload change (Option A) + byte-identical request
round-trip gated by `supportsReasoningSignatureRoundtrip`; unit tests + native e2e (WireMock)
covering enabled/adaptive/disabled, effort levels + CUSTOM, round-trip replay, and each validation
failure.

**Out of scope / deferred:** OpenAI `reasoning_effort` (C8, reuses the descriptor shape + its own
config); Anthropic **task budgets** (advisory loop-level token budget — separate feature); reasoning
UX polish (#7669); interleaved-thinking beta header for Opus 4.5/older (dropped, §1d); prompt-caching
`cache_control` (#7668). L4J bridge branches for `ReasoningContent` keep their current throw/skip
behaviour (unchanged).

---

## 8. Backward compatibility

- The **native path is unreleased** (8.10 v2), so changing `ReasoningContent.providerPayload` from
  today's bare signature string to a full-block Map costs **no** persisted-data BC.
- **Verify at impl:** that no *released* path (L4J bridge) persists a `providerPayload` shape we'd
  break. If the bridge never populates `providerPayload`, we're clear.
- Matrix change is additive (`reasoning` descriptor + kept booleans) — no persisted or template BC.
- No change to any persisted wire shape of `AssistantMessage` / `Content` beyond the (unreleased)
  `providerPayload` semantics.

---

## 9. Assumptions to verify during implementation

1. `BetaThinkingConfigAdaptive.Display` enum values (for the `display` config field) — inspect SDK.
2. A pure-reasoning assistant turn (thinking only, no text/tool_use) replays without a 400
   (C7 review note M-c7-5).
3. Effort enum lowercase mapping matches the wire values exactly (`XHIGH` → `"xhigh"`).
4. Threading `supportsReasoningSignatureRoundtrip` into `AnthropicContentConverter` (constructor vs
   method param) fits the existing wiring cleanly.
5. `redacted_thinking` block round-trips Map → `BetaContentBlockParam` via the SDK mapper (same spike
   that validated `ProviderContent`).
