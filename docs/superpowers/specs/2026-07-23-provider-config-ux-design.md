# Provider Configuration UX — Design

**Date:** 2026-07-23
**Epic:** #7211 (own the LLM layer)
**Status:** Design (captured from a grilling session; not yet an ADR)
**Scope:** The shape of the v2 provider configuration (the `V2ProviderConfiguration` sealed union
and its generated element-template dropdowns) so it can accommodate every realistic model host and
model maker without rework — even though only a subset is implemented per PR.

> This doc lives in the pilot worktree only. It does **not** ship in any PR. When the structure is
> ratified for implementation, distil the decisions into an ADR under
> `connectors/agentic-ai/.../docs/adr/` (which does ship).

---

## 1. Problem

A single "provider" (say Anthropic) can be reached many ways, each with different auth, feature
ceiling, and sometimes a different wire format:

- Anthropic API direct (API key) — full features
- AWS Bedrock, native Messages via **Mantle** (`bedrock-mantle`, newest models) — AWS auth, capped
- AWS Bedrock via **Converse** (older/generic) — AWS auth, capped, lower fidelity
- **Claude Platform on AWS** (`aws-external-anthropic`) — Anthropic-operated, AWS Marketplace billing,
  full features, SigV4 or AWS-console API key + workspace-id header
- **Microsoft Foundry** (Azure) — native Anthropic Messages (`/anthropic/v1/messages`), API key or
  Entra ID; two hosting variants (Hosted-on-Azure = capped/latest-only, Hosted-on-Anthropic = full)
- **Google Vertex / Gemini Enterprise Agent Platform** — native Anthropic Messages (`rawPredict`,
  model in URL, `anthropic_version` body field), GCP IAM/ADC; capped

OpenAI has the analogous story (direct Responses/Completions; on Bedrock Mantle as native Responses;
on Azure Foundry as the Azure-OpenAI dialect). Cloud-native models (Amazon Nova, Gemma) and the long
tail of makers (Mistral, DeepSeek, xAI Grok, Cohere-command, Kimi, Llama…) add more.

We must pick a config structure that is (a) intuitive for non-expert users, (b) able to show
model/host-specific options only where they apply, and (c) an extensibility seam we won't have to
break for BC reasons once released.

## 2. Landscape facts (as of mid-2026)

The facts that drive the structure (researched, sourced; see epic notes):

- **Wire format tracks the model maker, not the cloud.** Claude is the *native Anthropic Messages*
  format on direct, Bedrock-Mantle, Claude-Platform-on-AWS, Azure Foundry, and Vertex — only the
  endpoint + auth (+ `anthropic_version` placement) differ. Bedrock Mantle is a single host that
  serves each maker's native format (`/anthropic/v1/messages` and `/openai/v1/responses`).
- **Feature ceiling tracks the operator.** Anthropic-operated inference (direct,
  Claude-Platform-on-AWS, Azure Hosted-on-Anthropic) = full features (server tools, Skills, MCP).
  Cloud-operated (Bedrock either shape, Vertex, Azure Hosted-on-Azure) = capped (no server
  tools/Skills/MCP/Batches).
- **Converse is a distinct mapper that spans makers** (Nova, Llama, Gemma, gpt-oss, older Claude).
- **Proprietary GPT-5.x on Bedrock is Responses-API only** (Mantle host, AWS auth) — an OpenAI-family
  backend, not a Converse thing. Only open-weight gpt-oss is multi-format (Converse/Chat/Responses).
- **The entire chat long tail is OpenAI-Chat-Completions-shaped.** Mistral, DeepSeek, xAI Grok, Cohere-
  command, Kimi, Llama — on their own endpoints AND on Azure Foundry (`/openai/v1/chat/completions`,
  the stock OpenAI SDK; Azure is retiring its bespoke inference SDK for exactly this). One mapper
  covers all of them.
- **Not available:** Gemini is not on Bedrock (only open-weight Gemma); proprietary GPT is not on
  Vertex (only gpt-oss).
- **Google:** target the unified **Gen AI SDK** (`com.google.genai`), one client, two backends
  (Gemini Developer API = key; Vertex = ADC). "Vertex AI" is renamed *Gemini Enterprise Agent
  Platform* (cosmetic; endpoints unchanged). The **old `vertexai.generative_models` SDK was removed
  2026-06-24** — do not target it.

## 3. Architecture: three layers, presentation decoupled from mapper

The central decision: **the template dropdown (what the user picks) is decoupled from the internal
wire-format mapper.** Three layers:

1. **Wire mappers** — internal, finite, where the real code lives:
   `AnthropicMessages`, `OpenAiResponses`, `OpenAiChatCompletions`, `Converse`, `Gemini`.
2. **Transports/backends** — endpoint + auth scheme, reusable across mappers:
   `direct`, `bedrock-mantle`, `claude-platform-aws`, `azure-foundry`, `vertex`, `custom-url`.
   Auth blocks are shared (one SigV4 block for Bedrock / Claude-Platform-on-AWS; one Entra-ID block
   for Azure-Claude / Azure-OpenAI; GCP ADC for Vertex; API-key for direct).
3. **Presentation presets** — the `V2ProviderConfiguration` sealed members = the dropdown entries.
   Each preset is a curated binding: `{ label, icon/branding, default endpoint, allowed transports,
   default+placeholder model, which api mode, feature flags shown }` → resolves to a specific
   **(mapper, transport)**. Adding a preset is a small config record + defaults; **no new converter.**

The sealed-union discriminator (`type`) is the **preset**, not the mapper. Each concrete record
declares its mapper+transport in code (its factory picks the `ChatModelApi`).

### Consequence: marketing-friendly branded entries for free

A maker can be a first-class top-level dropdown entry for marketing/recognizability while reusing an
existing mapper. Example — **Mistral**: its own entry, pre-filled `api.mistral.ai` endpoint, Mistral
model placeholders, no Responses/Completions choice (hardcoded completions) → internally the
**OpenAI-ChatCompletions** mapper. Zero Mistral-specific wire code. Same pattern for DeepSeek, xAI
Grok, etc.

Presets come in three kinds:

- **Branded presets** (curated, pre-filled, marketing value): Anthropic, OpenAI, Google Gemini,
  Mistral, Amazon Bedrock (Nova/others), and any maker we choose to promote (DeepSeek, Grok…).
- **The long tail** rides an existing preset's `compatible` connection — NOT a dedicated top-level
  entry. Any OpenAI-Chat-Completions endpoint (Together, Groq, Fireworks, local vLLM/Ollama,
  self-hosted) = `OpenAI` provider + `Custom / compatible endpoint` connection. Any Anthropic-Messages
  endpoint = `Anthropic` provider + `Custom / compatible endpoint`. A separate "OpenAI-compatible" top
  entry was considered and **dropped as redundant** — it is just `openai` + `compatible`.
- **Custom (self-managed)** (#5547): bespoke wire formats via a user-supplied `ChatModelApiFactory`
  bean (`providerType` + `model` + open `parameters` map).

## 4. Per-preset shape

Each branded preset record carries:

- an optional **`api` enum** (e.g. OpenAI: `responses | completions`) — a plain field, **hidden when
  the preset has a single value**, extensible later (if Anthropic ships a non-Messages API, add an
  enum value; existing configs default to `messages`; no restructuring). This keeps "wire format near
  the top" without a deep nested discriminator.
- a **`backend`/hosting** discriminated sub-union — drives endpoint + auth. This is where the shared
  auth blocks attach.
- **`model`** (dedicated field — feeds the agent-instance history/reporting `chatModel().model()`).
- **feature toggles** (Skills, code execution, web search/fetch, prompt caching, thinking/effort…)
  shown via template `condition` on the backend value.

`api` × `backend` combinations that don't exist are rejected by **validation**, not encoded in the
type structure (e.g. `openai` + `bedrock` + `completions` is invalid — Bedrock GPT is Responses-only).

### Authentication per backend (shared blocks)

Auth is a property of the backend, not the preset. Reusable blocks:

- **API key** (`direct` for Anthropic/OpenAI/Mistral/Gemini-developer): a single key/secret reference.
- **AWS** (`bedrock` on any family, incl. `bedrock-converse`): an auth-method dropdown —
  **Access key & secret · Assumed IAM role · Default credentials chain · Bearer token**. Bedrock
  Converse/InvokeModel/Mantle all accept a Bedrock **bearer token** (`AWS_BEARER_TOKEN_BEDROCK`,
  long- or short-term), so bearer is a first-class option alongside SigV4.
- **Claude Platform on AWS** (`claude-platform-aws`): region + **workspace ID** header + auth-method
  dropdown (AWS SigV4 / AWS-console API key).
- **Azure / Microsoft Foundry** (`azure`): resource endpoint + auth-method dropdown —
  **API key · Microsoft Entra ID (client credentials) · Microsoft Entra ID (managed identity)**.
  Client credentials = OAuth2 `client_credentials` grant (Client ID + Client secret + Tenant ID, scope
  `https://ai.azure.com/.default`); managed identity = keyless on Azure compute (optional user-assigned
  client id). This mirrors the existing v1 Azure OpenAI "authentication strategy" dropdown.
- **Google Cloud** (`vertex`, Gemini `vertex`): project id + region + auth-method dropdown —
  **Service account credentials** (JSON key) · **Application default credentials**. ADC is an
  **explicit** choice, never implicit "blank = ADC", and is **disabled on Camunda SaaS** (Self-Managed
  / Hybrid only).
- **Gemini Developer API** (`developer-api`): API key (server-side `generateContent`). Note Gemini is
  therefore *not* key-only overall — its Vertex connection is IAM/ADC.
- **Ambient-credential options are always explicit + SaaS-disabled.** Both the AWS *Default
  credentials chain* and the GCP *Application default credentials* options are labelled
  "(Hybrid/Self-Managed only)" and blocked on SaaS (the pilot already enforces this for AWS via
  `@AssertFalse isDefaultCredentialsChainUsedInSaaS`). Same rule, applied uniformly.
- **Compatible endpoints** (`compatible` on any family): endpoint URL + an auth-method dropdown
  (**API key / OAuth 2.0 / None**; OAuth = token URL + client id/secret + scopes) + **headers** +
  **query parameters** + **body (request) parameters** maps. This matches the pilot's
  `AnthropicCompatibleBackend` (headers/queryParameters/requestParameters) + `CompatibleAuthentication`.

## 5. Feature gating without the capability matrix

Two separable concerns:

- **Feature *visibility*** (should we show the Skills / server-tools toggle?) — driven **statically by
  the backend discriminator** via template `condition`s. Show Skills/server-tools only for
  full-feature backends (direct, claude-platform-aws, azure Hosted-on-Anthropic). Capped backends
  (Bedrock, Vertex, azure Hosted-on-Azure) hide them. This needs **no runtime capability matrix.**
- **Numeric capabilities** (context window, max output tokens, reasoning support) — the deferred
  runtime capability matrix. Can't be gated in the template anyway (model is free-text). Stays
  deferred; unaffected by this design.

### Per-API field differences (already proven in the pilot)

Conditions key on **either** the `api` enum **or** the backend discriminator — this is why both are kept
as separate gating axes. Fields that differ per API sit on one record as sibling nullable components,
each shown by a condition. The pilot's `OpenAiModelParameters` already does exactly this:

```java
@TemplateProperty(label = "Maximum completion tokens", …,
  condition = @PropertyCondition(property = "provider.openai.apiFamily", equals = "completions"))
@Nullable Integer maxCompletionTokens,
@TemplateProperty(label = "Maximum output tokens", …,
  condition = @PropertyCondition(property = "provider.openai.apiFamily", equals = "responses"))
@Nullable Integer maxOutputTokens,
```

and gates OpenAI's server tools (`enableWebSearch`, `enableCodeInterpreter`) to `apiFamily = responses`.
Combinators: `equals` / `equalsBoolean` (single value), `oneOf` (OR over values of one property, e.g.
Skills shown when backend ∈ {direct, claude-platform-aws, azure}), `allMatch` (AND across properties,
e.g. OpenAI web search = `apiFamily=responses` AND backend ∉ Bedrock). OR across *different* properties
is not a single primitive — duplicate the field or gate on a derived value (rarely needed).

## 6. Concrete template tree + labels

Two visible dropdowns: **Provider** (the preset `type`) and, within a preset, **Connection**
(hosting/backend). `api` is a third field shown only when multi-valued.

```
Provider (discriminator: type)
├─ anthropic            [Anthropic Messages mapper]
│    Connection: direct | bedrock | claude-platform-aws | azure | vertex | compatible
│    (Skills/server-tools shown only for direct, claude-platform-aws, azure-hosted-on-anthropic)
├─ openai               [OpenAI mapper]
│    api: responses | completions
│    Connection: direct | bedrock | azure | compatible
├─ gemini               [Gemini / Gen AI SDK mapper]
│    Connection: developer-api | vertex
├─ mistral              [→ OpenAI ChatCompletions mapper]  (branded preset; completions hardcoded)
│    Connection: direct (api.mistral.ai) | azure | compatible
├─ bedrock-converse     [Converse mapper]  (Nova, Llama, Gemma, gpt-oss; and older Claude via Converse)
│    Connection: bedrock (SigV4 | assumed role | default chain | bearer)
└─ custom               [user-supplied ChatModelApiFactory]  (#5547)

(The generic OpenAI-compatible long tail = `openai` + `compatible` connection; no separate top entry.)
```

Label conventions (brand/product names in the label, API detail in tooltips):

| id | Provider label | Notes / tooltip gist |
|---|---|---|
| `anthropic` | **Anthropic (Claude)** | — |
| `openai` | **OpenAI** | — |
| `gemini` | **Google (Gemini)** | — |
| `mistral` | **Mistral AI** | pre-filled `api.mistral.ai` |
| `bedrock-converse` | **Amazon Bedrock (other models)** | Nova, Llama, Gemma & others via Converse |
| `custom` | **Custom (self-managed)** | user-supplied factory (#5547) |

Anthropic Connection labels (the subtle AWS pair called out explicitly):

| backend id | Connection label | Tooltip gist |
|---|---|---|
| `direct` | **Anthropic API** | Anthropic-hosted; all features |
| `bedrock` | **Amazon Bedrock (AWS-managed)** | AWS-operated; server tools & Skills unavailable; AWS IAM/bearer |
| `claude-platform-aws` | **Claude Platform on AWS** | Anthropic-operated; full features; AWS Marketplace billing; SigV4 or AWS-console key + workspace-id |
| `azure` | **Microsoft Foundry (Azure)** | native Messages; API key or Entra ID |
| `vertex` | **Google Vertex AI** | native Messages; Google Cloud IAM/ADC |
| `compatible` | **Custom / compatible endpoint** | any Anthropic-Messages-compatible URL |

## 7. Decisions (ratified this session)

1. **Presentation-first, decoupled from mapper.** Dropdown entries are curated presets; the internal
   wire mapper is a code-level binding. Marketing can promote any maker to top-level without new wire
   code.
2. **Branded presets + generic OpenAI-compatible + Custom** together absorb the entire long tail with
   no per-maker mappers.
3. **Claude-via-Converse: kept** as a UX preset (older Claude on Bedrock) that maps internally to the
   Converse mapper. UX presence ≠ mapper identity.
4. **`api` mode = per-preset enum field**, hidden when single-valued, additive/BC to any preset later.
5. **Hosting/backend = discriminated sub-union** carrying the shared auth blocks.
6. **Feature visibility gated by template `condition` on the backend** (static). Numeric capability
   matrix stays deferred.
7. **v1 templates: frozen now.** Future intent (not this epic): internally rewrite/adapt v1 configs
   onto the new v2 code path with sane defaults, rather than keeping them on the L4J bridge forever.
   New hostings land only in v2.
8. **Labels** per the tables above; the AWS pair uses "Amazon Bedrock (AWS-managed)" vs "Claude
   Platform on AWS" (Anthropic's own product name).
9. **No "OpenAI-compatible" top-level preset.** The generic long tail = `openai` + `compatible`
   connection (and Anthropic-Messages-compatible = `anthropic` + `compatible`). A dedicated top entry
   is redundant.
10. **AWS backends offer bearer-token auth** (`AWS_BEARER_TOKEN_BEDROCK`) alongside SigV4 / assumed
    role / default chain — on Converse and Mantle alike.
11. **Compatible endpoints always carry** headers + query params + body (request) params + an
    auth-method dropdown (API key / OAuth 2.0 / None).
12. **Azure auth = API key / Entra ID client-credentials / Entra ID managed-identity.** Client
    credentials (Client ID + secret + Tenant ID) is confirmed the standard server-to-server flow;
    mirrors the existing v1 Azure OpenAI authentication-strategy dropdown.

## 8. Out of scope / deferred

- **Runtime capability matrix** for numeric caps — deferred (unchanged by this design).
- **Non-chat modalities** (image generation, rerank, embeddings — BFL FLUX, Cohere rerank,
  Mistral-OCR) — not part of the agentic chat path; handled separately if ever needed.
- **Gemini on Azure Foundry** — reported to exist; treat as secondary/unverified until confirmed
  against Microsoft's own docs.
- **v1 internal-rewrite adapter** — future, not this epic.
- **Which branded presets ship first** — a curation/phasing decision, not a structural one. Structure
  must accommodate all; implementation ships a subset per PR (native Anthropic PR first, then OpenAI,
  then the compatible/branded long-tail presets).

## 9. Relationship to the current pilot / PRs

The pilot's `V2ProviderConfiguration` already models `AnthropicChatModel` / `OpenAiChatModel` as sealed
members with a nested backend sub-union and (for OpenAI) an `api` family field — i.e. it is already the
preset+backend shape at layer 3, minus the explicit "preset decoupled from mapper" framing and minus
the extra hostings/branded presets. This design extends that shape; it does not replace it. The
custom-only PR2′ ships `custom` first; the native Anthropic and OpenAI PRs add those presets and their
backends; branded/compatible long-tail presets follow.

## 10. Forward-look: the capability matrix

The deferred runtime capability matrix (`model-capabilities.yaml` + `ModelCapabilitiesResolver`) is
still deferred — but the preset/api/backend/model split established here is exactly its selector
schema, so record how it lines up before the work resumes.

### 10.1 The matrix keys on the *resolved* technical triple, never the preset

The resolver input is **`(apiFamilyKey, backend, model)`** — the preset `type` never reaches the matrix.
The preset *resolves to* that triple. Two presets can resolve to the same triple: the `mistral` branded
preset and a generic `openai` + `compatible` connection both resolve to `apiFamilyKey = openai-completions`
and therefore read the *same* matrix entries — zero maker-specific matrix data. The bridge already
exists in code: the pilot's `OpenAiChatModel.apiFamilyKey()` returns `openai-completions` /
`openai-responses`; generalise `apiFamilyKey()` onto the `V2ProviderConfiguration` interface (Anthropic
→ `anthropic-messages`, Converse → `converse`), and the matrix input flows straight from the config.

### 10.2 Axis roles

| Axis | Role in the matrix | Owns |
|---|---|---|
| **api-family** (mapper + api) | **primary key** — selects the family block | feature *shape* / which fields exist |
| **model** | the row within the block | numerics, modalities, reasoning-*capability* |
| **backend** | orthogonal **overlay** — a model-agnostic baseline tier + a most-specific model+backend overlay | backend-varied numerics; availability → validation, not merge |

api-family is **categorically different** from backend. Backend and model are values on orthogonal axes
that merge. api-family changes *which fields exist and the whole feature shape* (`max-completion-tokens`
vs `max-output-tokens`; responses-only server tools + encrypted-reasoning round-trip). That is a
different table, not a different value — so it is the top-level key, resolved from `apiFamilyKey()`. You
read one family block and merge model/backend within it; you **never merge across api-families**.

### 10.3 Resolution cascade (deep-merge; most-specific wins; defaults never override a more specific entry)

Lowest → highest precedence:

1. conservative default (unknown api-family)
2. `<family>.defaults`
3. **`<family>.backends.<id>.defaults`** — model-agnostic baseline for that backend
4. `<family>.models.<m>` (backend-agnostic) — the model's intrinsic values
5. `<family>.models.<m>` + `backend: <id>` (most specific)
6. per-element `capabilityOverride` (per-call, already in the pilot)

The rule that keeps tier 3 honest: **adding the model axis outranks adding the backend axis; pinning
both outranks either.** So `backends.<id>.defaults` is a genuine baseline (fills fields a model does not
pin, e.g. "on Vertex, unpinned models default to text-only tool-results") and a model that states its
own value always wins. It never *revokes* anything.

**Feature availability is not in this cascade.** "Bedrock has no Skills" is a backend/operator trait,
not a per-model capability to override. It is enforced on its own axis by (a) **static template
gating** (hide the toggle when `backend ∈ {bedrock, vertex}`) and (b) **fail-fast validation** (a config
that requests Skills on an unsupporting backend errors out — the pilot already does matrix-driven
fail-fast validation). So there is never a state where a model says `skills: true` and the backend must
overrule it.

### 10.4 Example

Illustrative (numbers are placeholders; field names follow the real file):

```yaml
capabilities:

  # ── api-family = PRIMARY KEY (a whole surface: field set + feature shape) ──
  anthropic-messages:

    defaults:                      # tier 2 — family baseline (shape + surface)
      input-modalities:
        user-message: [text, image, document]
        tool-result:  [text, image, document]
      output-modalities:
        assistant-message: [text]
      context-window: 200000
      max-output-tokens: 32000

    backends:                      # tier 3 — model-AGNOSTIC baseline per backend
      bedrock:                     # only fills what a model doesn't pin; never overrides it
        defaults:
          context-window: 200000
      vertex:
        defaults:
          input-modalities:
            tool-result: [text]    # baseline: no image tool-results unless a model says so

    models:                        # tier 4 — the row: intrinsic numerics + reasoning capability
      claude-sonnet-5:
        patterns: "claude-sonnet-5*"
        capabilities:
          context-window: 200000
          max-output-tokens: 64000
        provider:                  # opaque per-family bag (reasoning etc.)
          reasoning:
            supported: true
            effort-values: [low, medium, high, xhigh, max]
            thinking-modes: [enabled, adaptive, disabled]

      claude-opus-4-6:
        patterns: "claude-opus-4-6*"
        capabilities:
          context-window: 200000
          max-output-tokens: 32000
        provider:
          reasoning: { supported: true, effort-values: [low, medium, high] }

      claude-opus-4-6-bedrock:     # tier 5 — model + backend, MOST specific (wins)
        id: claude-opus-4-6
        backend: bedrock
        capabilities:
          context-window: 180000   # this exact combo genuinely differs

  # ── SAME maker, DIFFERENT api-family = SEPARATE block (different surface) ──
  openai-responses:
    defaults:
      output-modalities: { assistant-message: [text] }
      context-window: 272000
      max-output-tokens: 128000    # note: "output tokens" field
    models:
      gpt-5.4:
        patterns: "gpt-5.4*"
        capabilities: { context-window: 272000, max-output-tokens: 128000 }
        provider:
          reasoning: { supported: true, encrypted-round-trip: true }   # responses-only
          server-tools: [web_search, code_interpreter]                 # responses-only

  openai-completions:
    defaults:
      output-modalities: { assistant-message: [text] }
      context-window: 272000
      max-completion-tokens: 128000  # DIFFERENT field name — different surface
    models:
      gpt-5.4:                       # numerics DUPLICATED (same model, other surface)
        patterns: "gpt-5.4*"
        capabilities: { context-window: 272000, max-completion-tokens: 128000 }
        provider:
          reasoning: { supported: true }   # effort only, no round-trip, no server tools
```

Worked resolutions:

- **`anthropic` + `bedrock` + `claude-opus-4-6`** → tier2 → tier3 (`bedrock` baseline) → tier4
  (`claude-opus-4-6` pins 200000/32000, **wins over the baseline**) → tier5 (`claude-opus-4-6-bedrock`,
  context → **180000**). Final: context 180000, max-output 32000.
- **`anthropic` + `vertex` + unlisted `claude-haiku-*`** → tier2 → tier3 (`vertex` baseline drops image
  tool-results) → no tier4/5 match → family defaults + vertex baseline. The restriction sticks *because
  nothing overrode it*.
- **`openai` + `responses` + `gpt-5.4`** reads only `openai-responses` (→ `max-output-tokens`,
  `encrypted-round-trip`, `server-tools`); the same config with `completions` reads `openai-completions`
  (→ `max-completion-tokens`, no server tools). Never merged across the two.

### 10.5 Known cost

api-family-as-partition **duplicates** a model's numerics across `openai-responses` and
`openai-completions` (same underlying model, two surfaces). Bounded for now; if it grows, factor the
shared numerics into a maker-level block both api-families read. Not worth restructuring today — and it
is the correct trade vs. pretending responses and completions are the same surface.

### 10.6 Shared vocabulary

Backend ids are defined once and reused by config `backend()`, the matrix `backends.<id>` / `backend:`
field, and template `condition`s: `direct · bedrock · claude-platform-aws · azure-foundry · vertex ·
compatible`. No translation layers. Template conditions are the coarse static affordance; the matrix is
the precise runtime source of truth; they agree because they share the ids.
