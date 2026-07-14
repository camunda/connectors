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
public enum ThinkingDisplay { SUMMARIZED, OMITTED }   // verified: BetaThinkingConfigAdaptive.Display

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

## 3. Capability matrix — reasoning descriptor + config-binding alignment

**Ratified refinement (2026-07-14, post-R0).** R0 made the *runtime* capability type provider-specific
but left the *config-binding* shape (`ModelCapabilitiesProperties`, one type spanning all families)
and the projection DTO still carrying provider flags flat/shared. R1 finishes that alignment **and
prunes the dead flags.**

### 3a. Prune the unused `supports-*` flags
All four `supports-*` flags had **zero production consumers**. Ratified decision — drop them now, each
returns with its own feature chunk:
- `supports-prompt-caching` → dropped (caching is #7668; re-added with a real cache-dimension descriptor then).
- `supports-parallel-tool-calls` → dropped from Anthropic (an OpenAI-only concept; belongs on a future
  `OpenAiProviderCapabilities`).
- `supports-reasoning-signature-roundtrip` → dropped: native Anthropic **must always** replay thinking
  blocks (API 400s otherwise) and both `thinking` (signature) and `redacted_thinking` (data) are always
  re-emittable, so the toggle is a no-op (see §4).
- `supports-reasoning` → **derived, not stored**: `AnthropicModelCapabilities.supportsReasoning()` ≡
  `reasoning descriptor present`. Removes the duplication between the flag and the descriptor.

After the prune, the **only** provider-specific capability datum is the `reasoning` descriptor.

### 3b. Config-binding alignment — typed agnostic core + opaque provider bag
The shared binding type becomes provider-agnostic (mirrors `CoreModelCapabilities`) plus one opaque
seam; each provider *types* the seam in its own projection DTO. Spring's binder can't pick a Java type
per family map-key, so the seam must be a declared `Map<String,Object>` (not a Jackson `@JsonAnySetter`).

```java
// capabilities pkg — provider-AGNOSTIC (mirrors CoreModelCapabilities + one opaque seam)
record ModelCapabilitiesProperties(
    @Nullable InputModalities inputModalities, @Nullable OutputModalities outputModalities,
    @Nullable Integer contextWindow, @Nullable Integer maxOutputTokens,
    @Nullable Map<String, Object> provider) {}      // opaque provider-specific bag

// anthropic pkg — runtime: core + the one provider datum; supportsReasoning derived
record AnthropicModelCapabilities(CoreModelCapabilities core,
    @Nullable AnthropicReasoningCapabilities reasoning) implements ModelCapabilities {
  public boolean supportsReasoning() { return reasoning != null; }
}

// anthropic pkg — projection DTO reads the agnostic core fields + the typed provider bag
record AnthropicModelCapabilitiesData(
    @Nullable InputModalities inputModalities, @Nullable OutputModalities outputModalities,
    @Nullable Integer contextWindow, @Nullable Integer maxOutputTokens,
    @Nullable AnthropicProviderCapabilities provider)
    implements ModelCapabilitiesData<AnthropicModelCapabilities> { /* toModelCapabilities() */ }
record AnthropicProviderCapabilities(@Nullable AnthropicReasoningCapabilities reasoning) {}
```

```yaml
anthropic-messages:
  models:
    claude-opus-4-6-plus:
      pattern: [claude-opus-4-6*, claude-opus-4-7*, claude-opus-4-8*]
      capabilities:
        context-window: 1000000
        max-output-tokens: 128000
        provider:                                       # provider-specific keys nest here
          reasoning:
            thinking-modes: [adaptive, disabled]        # NO 'enabled' → manual budget 400s here
            effort-levels:  [low, medium, high, xhigh, max]
    claude-opus-4-5:
      capabilities:
        provider:
          reasoning: { thinking-modes: [enabled, disabled], effort-levels: [low, medium, high, max] }
    claude-sonnet-4-5:
      capabilities:
        provider:
          reasoning: { thinking-modes: [enabled, disabled] }   # no effort-levels ⇒ effort unsupported
```

**Keys are provider-specific per API family (decided).** The Anthropic descriptor is Anthropic's own
enums; a future OpenAI family puts different keys in its own `OpenAiProviderCapabilities`:

```java
public record AnthropicReasoningCapabilities(
    @JsonProperty("thinking-modes") List<ThinkingMode> thinkingModes,   // ENABLED|ADAPTIVE|DISABLED
    @JsonProperty("effort-levels")  List<AnthropicEffort> effortLevels) {}  // Anthropic effort enum
```

### 3c. Merge correctness (verified)
- `reasoning` is an **object** → `deepMerge` recurses: an entry overlaying only `effort-levels` keeps the
  base's `thinking-modes` (existing sibling-preservation, one level deeper under `provider.reasoning`).
- `thinking-modes` / `effort-levels` are **lists** → overlay **replaces** wholesale (never unions) —
  correct for capability lists.
- Conservative base has no `provider` → unknown family/model → `reasoning == null` → unsupported
  (identical to the old `supports-reasoning: false`).
- **Trap:** `deepMerge` cannot un-set a key (overlay-null = keep base). So `provider`/`reasoning` must
  **never** appear in a family `defaults:` block — else a non-reasoning sibling model could not clear it.
  Guideline: declare `reasoning` per-model/glob only; "absent = unsupported." Enforced by a bundled-matrix test.
- **Opaque-bag key case:** Spring binds `Map` keys **literally** (no relaxed transform inside the bag),
  so the bag standardises on kebab-case (matching the rest of the YAML) and the typed descriptor uses
  kebab `@JsonProperty` (`thinking-modes`/`effort-levels`). The typed shared fields (`input-modalities`
  etc.) keep relaxed binding + `@JsonNaming(SnakeCase)` as before.
- **Sourcing:** `reasoning` presence follows models.dev `reasoning` (bool); models.dev `reasoning_options`
  is **empty for all Claude models**, so the descriptor is hand-curated (as the rest of the Anthropic
  matrix already is). OpenAI's family descriptor can later map from models.dev `reasoning_options`.

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

### 4b. Request-side re-emission (closes the one-directional gap) — UNCONDITIONAL
`AnthropicContentConverter.toContentBlockParams` — replace `case ReasoningContent ignored -> {}`:
```java
case ReasoningContent rc -> {
  if (rc.providerPayload() != null) {
    blocks.add(ObjectMappers.jsonMapper()
        .convertValue(rc.providerPayload(), BetaContentBlockParam.class));
  }
  // else: no raw payload to replay (e.g. bridge-produced ReasoningContent) — skip so replay stays valid
}
```
- **No capability gate** (ratified §3a): `supports-reasoning-signature-roundtrip` was dropped. On the
  native Anthropic path re-emission is *always* required — the API 400s if consecutive thinking blocks
  are not replayed — and both `thinking` (with `signature`) and `redacted_thinking` (with `data`) are
  always re-emittable. The only skip case is a `null` `providerPayload` (the existing null-guard,
  mirroring `ProviderContent`). The converter therefore needs **no** extra flag threaded through.
- **Why Option A / byte-identical:** the *"cannot be modified/rearranged"* rule means a rebuilt block
  (Option B) risks a 400 from field-order/whitespace drift; replaying the stored bytes cannot drift.
  Also generalises to OpenAI's encrypted reasoning items with zero new vocabulary on the neutral type.
- **Ordering** already correct (`assistantParam` emits content before tool_use, §1d). Multiple
  consecutive thinking blocks and `redacted_thinking` are preserved as ordered `ReasoningContent`s.

### 4c. Round-trip is a correctness requirement (always on)
On adaptive-thinking + tool use (the mainline for flagship models), the signed thinking block **must**
be replayed with the tool_use or the next call 400s. §4b now does this unconditionally for every
`ReasoningContent` carrying a raw payload — no per-model toggle.

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

Before the API call, validate config against `AnthropicModelCapabilities.reasoning` (the typed
descriptor, §3). Validation keys off the **descriptor**, not a `supports-reasoning` boolean (dropped,
§3a — `supportsReasoning()` is now just `reasoning != null`). Each failure throws a `ConnectorException`
with a clear, actionable message (no opaque API 400s):

1. `thinking` or `effort` set but `reasoning == null` (a **known** model with no descriptor) → fail
   (reasoning unsupported by `<model>`).
2. `thinking.mode ∉ reasoning.thinkingModes` → fail (e.g. `ENABLED` on Opus 4.8 →
   *"manual thinking budget not supported by <model>; use ADAPTIVE + effort"*).
3. `thinking.mode == ENABLED && budgetTokens == null` → fail (budget required).
4. `effort` set (non-`CUSTOM`) but `reasoning.effortLevels` empty/absent → fail (effort unsupported).
5. `effort` set (non-`CUSTOM`) and `effort ∉ reasoning.effortLevels` → fail.
6. `effort == CUSTOM` → **bypass** validation (sent verbatim via `Effort.of`).

**Unknown/unmatched model** (matrix miss → conservative defaults, `reasoning` absent): the resolver
signals a fall-through (it already logs one). Ratified: **pass-through** — skip client-side validation
and let the API decide — so custom/unknown models stay usable (consistent with the capability-override
escape hatch). Distinguishing "known model, no descriptor → fail rule 1" from "unmatched model →
pass-through" requires the resolver to expose whether the model matched; thread that signal (a small
`matched` boolean alongside the resolved capabilities, or a resolver method) into validation. If that
signal is judged too invasive for R1, fall back to **pass-through in both cases** (rule 1 downgraded to
pass-through) and document that a known non-reasoning model surfaces the API's own 400 — decide during
implementation, defaulting to pass-through-both if the matched-signal is non-trivial.

---

## 7. Scope

**In scope — R0 (reshape chunk):** `ModelCapabilities` → neutral interface; `CoreModelCapabilities`
shared component; `AnthropicModelCapabilities` provider record; parameterised resolver
(`resolve(..., Class<T>)`) + per-provider conservative defaults; LangChain4j bridge neutral impl;
rewire `CapabilityAwareToolCallResultStrategy` + `capabilities()` producers to the interface; the
three custom-provider invariants. Behaviour-identical — existing resolver/strategy tests stay green,
no wire change.

**In scope — R1 (reasoning + effort):**
- **Prune** the 3 dead `supports-*` flags + derive `supportsReasoning` (§3a) across matrix YAML, the
  binding/projection/runtime types, `ModelCapabilitiesOverride`, and tests (behaviour-identical — no
  consumers).
- **Config-binding alignment** (§3b): `ModelCapabilitiesProperties` → typed agnostic core + opaque
  `provider` bag; `AnthropicModelCapabilitiesData` reads the typed `provider.reasoning`;
  `AnthropicModelCapabilities` = `core` + `@Nullable AnthropicReasoningCapabilities`.
- **Matrix reasoning descriptor** hand-curated per reasoning-capable model (never in family `defaults`,
  §3c) + `AnthropicReasoningCapabilities` typed record + enums.
- **Config** (§2): `thinking` + `effort` + `customEffort` on `AnthropicModelParameters`; element-template
  properties with conditional visibility; template regen.
- **Request mapping** (§5): thinking union + effort → SDK.
- **Validation** (§6): matrix-descriptor-driven fail-fast; CUSTOM bypass; unknown-model pass-through.
- **Response round-trip** (§4): `ReasoningContent.providerPayload` = raw block Map (Option A) +
  **unconditional** request re-emission (closes `case ReasoningContent ignored -> {}`).
- **Tests:** unit + native e2e (WireMock) covering enabled/adaptive/disabled, effort levels + CUSTOM,
  round-trip replay, and each validation failure.

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
- Matrix + binding restructure (drop 3 `supports-*` flags, move `reasoning` under a `provider` bag) is
  **BC-free**: the matrix is classpath data on the unreleased 8.10 native path, capability-override
  config is unreleased, and element templates are untouched. No persisted state involved.
- No change to any persisted wire shape of `AssistantMessage` / `Content` beyond the (unreleased)
  `providerPayload` semantics.

---

## 9. Assumptions — verified against anthropic-java 2.48.0 (2026-07-14)

1. ✅ `BetaThinkingConfigAdaptive.Display` = **`SUMMARIZED`, `OMITTED`** (open enum, `Display.of(String)`).
   `ThinkingDisplay` config enum = `{ SUMMARIZED, OMITTED }`.
2. ✅ `BetaThinkingConfigParam` union factories `ofEnabled(BetaThinkingConfigEnabled)` /
   `ofAdaptive(BetaThinkingConfigAdaptive)` / `ofDisabled(BetaThinkingConfigDisabled)`;
   `BetaThinkingConfigEnabled.builder().budgetTokens(long)`.
3. ✅ `BetaOutputConfig.Effort` = `LOW/MEDIUM/HIGH/XHIGH/MAX` + `Effort.of(String)` (open enum, no
   `minimal`). Lowercase mapping (`XHIGH` → `"xhigh"`) to confirm at impl via a serialization round-trip.
4. **Still to verify at impl:** a pure-reasoning assistant turn (thinking only, no text/tool_use) replays
   without a 400 (C7 review note M-c7-5); `redacted_thinking` block round-trips Map →
   `BetaContentBlockParam` via the SDK mapper (same spike that validated `ProviderContent`).
5. ~~Threading `supportsReasoningSignatureRoundtrip`~~ — **N/A**: flag dropped (§3a, §4); re-emission is
   unconditional, no flag to thread.
