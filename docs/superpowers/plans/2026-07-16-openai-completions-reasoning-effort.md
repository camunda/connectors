# OpenAI Completions Reasoning-Effort Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add controllable `reasoning_effort` support to the native OpenAI **Chat Completions** family, so reasoning models (gpt-5, o-series) can be driven at a chosen effort on Completions — mirroring the Responses family — and surface their reasoning-token metrics.

**Architecture:** Completions reasoning is **input-only**. The Chat Completions API accepts a `reasoning_effort` request param but returns **no reasoning content** (only a `reasoning_tokens` count in `completion_tokens_details`). Unlike Responses, there are no reasoning items and no encrypted reasoning payload — so the `ReasoningContent` / encrypted round-trip stays Responses-only and our content model, serialization, and turn-to-turn replay are untouched. The change is: declare reasoning in the matrix (drives the existing validator), map effort onto the request, and stop zeroing reasoning tokens in the response.

**Tech Stack:** Java 21, openai-java 4.43.0 (`com.openai.*`), JUnit 5 + Mockito + AssertJ, Spring Boot. Capability data in `model-capabilities.yaml`.

## Global Constraints

- Delegated implementation work runs on **Sonnet**; reviews on **Sonnet**. Opus only orchestrates.
- Build/test with `dangerouslyDisableSandbox` (sandbox breaks Mockito). Use `mvn test` / `test-compile`; `mvn install` to m2 is permitted when needed to refresh the connector jar for the e2e module.
- After model/serialization changes, also test-compile the separate `connectors-e2e-test-agentic-ai` module (`-am`) and watch for stale timestamped `8.10.0-SNAPSHOT` jars in `~/.m2`.
- **No real-API e2e runs without explicit permission** (cost). Compile / unit / skip runs are fine.
- Coherent commit messages describing the actual change; never "task"/"review round"/"crit".
- `mvn spotless:apply` + `mvn license:format` must run clean (pre-commit hooks enforce).
- Matrix rule: `reasoning` is declared **per-model/glob only**, never in a family `defaults` block (the deep-merge cascade cannot un-set it). Effort levels must be valid for **every** model a glob matches.

---

## File Structure

- `connector-agentic-ai/src/main/resources/capabilities/model-capabilities.yaml` — declare `provider.reasoning` for reasoning models under `openai-completions` (mirror the `openai-responses` per-model declarations).
- `.../framework/openai/family/completions/OpenAiCompletionsRequestConverter.java` — map the configured effort onto `ChatCompletionCreateParams.reasoningEffort(...)`.
- `.../framework/openai/family/completions/OpenAiCompletionsResponseConverter.java` — surface `reasoning_tokens` instead of hard-coding 0.
- Tests: `OpenAiCompletionsRequestConverterTest`, `OpenAiCompletionsResponseConverterTest`, `OpenAiCapabilityResolutionTest` / `BundledCapabilityMatrixTest` (flip the "no reasoning" expectation), `OpenAiRequestValidatorTest` (effort now accepted for gpt-5 completions).
- `connectors-e2e-test-agentic-ai/.../NativeProviderAcceptanceIT.java` — add a gpt-5 **completions** row with `REASONING`; keep the gpt-5 responses row and the gpt-4o baseline so the three can be compared.

---

### Task 1: Declare reasoning for reasoning models under `openai-completions`

**Files:**
- Modify: `connector-agentic-ai/src/main/resources/capabilities/model-capabilities.yaml` (the `openai-completions` family block; gpt-5 entry ~line 212, plus o1/o3/o4 entries)
- Test: `connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/framework/openai/OpenAiCapabilityResolutionTest.java` and `.../capabilities/BundledCapabilityMatrixTest.java`

**Interfaces:**
- Produces: gpt-5 (and o-series) resolved under `openai-completions` now carry a non-null `reasoning` with the same `effort-levels` the `openai-responses` family already declares for that model.

- [ ] **Step 1: Update the existing "no reasoning" tests to the new expectation (red)**

`OpenAiCapabilityResolutionTest.gpt5OnCompletionsHasNoReasoning` currently asserts gpt-5 on completions has no reasoning. Rename/flip it:

```java
@Test
void gpt5OnCompletionsHasReasoning() {
  final var caps = resolve("openai-completions", "gpt-5");
  assertThat(caps.reasoning()).isNotNull();
  assertThat(caps.reasoning().effortLevels())
      .containsExactlyInAnyOrder(
          OpenAiEffort.MINIMAL, OpenAiEffort.LOW, OpenAiEffort.MEDIUM, OpenAiEffort.HIGH);
}
```

Do the same for any `BundledCapabilityMatrixTest` assertion pinning gpt-5-completions to no-reasoning.

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -pl connectors/agentic-ai/connector-agentic-ai -Dtest='OpenAiCapabilityResolutionTest,BundledCapabilityMatrixTest'`
Expected: FAIL (reasoning still null).

- [ ] **Step 3: Add the reasoning declaration to the matrix**

Under `openai-completions`, add `provider.reasoning` to the gpt-5 entry mirroring the `openai-responses` gpt-5 declaration, and to each o-series entry mirroring its responses counterpart (o1/o3/o4). Example for gpt-5 (place under its `capabilities`/`provider`, exactly as the responses sibling does):

```yaml
                gpt-5:
                  pattern: gpt-5*
                  capabilities:
                    provider:
                      reasoning:
                        effort-levels: [minimal, low, medium, high]
```

For o-series, copy the effort-levels verbatim from the matching `openai-responses` entry (do not invent levels — `minimal` is gpt-5-only; o1 etc. must use exactly what the responses family already declares).

- [ ] **Step 4: Run to verify it passes**

Run: `mvn test -pl connectors/agentic-ai/connector-agentic-ai -Dtest='OpenAiCapabilityResolutionTest,BundledCapabilityMatrixTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add connectors/agentic-ai/connector-agentic-ai/src/main/resources/capabilities/model-capabilities.yaml \
        connectors/agentic-ai/connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/framework/openai/OpenAiCapabilityResolutionTest.java \
        connectors/agentic-ai/connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/framework/capabilities/BundledCapabilityMatrixTest.java
git commit -m "feat(agentic-ai): declare reasoning effort for gpt-5/o-series on openai-completions"
```

---

### Task 2: Map the configured effort onto the Completions request

**Files:**
- Modify: `.../framework/openai/family/completions/OpenAiCompletionsRequestConverter.java`
- Test: `.../framework/openai/family/completions/OpenAiCompletionsRequestConverterTest.java`

**Interfaces:**
- Consumes: `OpenAiModelParameters.effort()` (an `OpenAiEffort`, already parsed), `capabilities.reasoning()`; `OpenAiRequestValidator.validate(...)` already runs first and rejects effort on non-reasoning models — **no validator change needed**.
- Produces: `ChatCompletionCreateParams` with `reasoningEffort(...)` set when an effort is configured.

- [ ] **Step 1: Write the failing test**

```java
@Test
void mapsConfiguredEffortToReasoningEffort() {
  final var params =
      converter.toChatCompletionCreateParams(
          ctx(model(parameters(OpenAiEffort.HIGH)), null), snapshot(userMessage("hi")), caps());
  assertThat(params.reasoningEffort()).hasValue(ReasoningEffort.HIGH);
}

@Test
void omitsReasoningEffortWhenNoneConfigured() {
  final var params =
      converter.toChatCompletionCreateParams(
          ctx(model(parameters(null)), null), snapshot(userMessage("hi")), caps());
  assertThat(params.reasoningEffort()).isEmpty();
}
```

(Use the test's existing `model(...)`/`parameters(...)`/`caps()` helpers; add a `parameters(OpenAiEffort)` helper if absent. `caps()` must resolve to a reasoning-capable model so the validator permits the effort.)

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -pl connectors/agentic-ai/connector-agentic-ai -Dtest=OpenAiCompletionsRequestConverterTest`
Expected: FAIL (`reasoningEffort` empty / method not applying).

- [ ] **Step 3: Apply effort in the converter**

Add to `OpenAiCompletionsRequestConverter`, mirroring the Responses sibling's `mapEffort`. Note: **no** `store(false)`, **no** encrypted-content include, **no** reasoning-item replay — Completions has none of that.

```java
private void applyReasoning(
    ChatCompletionCreateParams.Builder builder, @Nullable OpenAiModelParameters params) {
  final OpenAiEffort effort = params == null ? null : params.effort();
  if (effort == null) {
    return;
  }
  builder.reasoningEffort(ReasoningEffort.of(effort.name().toLowerCase(Locale.ROOT)));
}
```

Call `applyReasoning(builder, params);` from `toChatCompletionCreateParams(...)` right after `applyModelParameters(...)`. Add imports for `com.openai.models.ReasoningEffort` and `java.util.Locale`.

- [ ] **Step 4: Run to verify it passes**

Run: `mvn test -pl connectors/agentic-ai/connector-agentic-ai -Dtest=OpenAiCompletionsRequestConverterTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/openai/family/completions/OpenAiCompletionsRequestConverter.java \
        connectors/agentic-ai/connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/framework/openai/family/completions/OpenAiCompletionsRequestConverterTest.java
git commit -m "feat(agentic-ai): send reasoning_effort on native OpenAI Chat Completions requests"
```

---

### Task 3: Surface reasoning tokens in the Completions response metrics

**Files:**
- Modify: `.../framework/openai/family/completions/OpenAiCompletionsResponseConverter.java`
- Test: `.../framework/openai/family/completions/OpenAiCompletionsResponseConverterTest.java`

**Interfaces:**
- Consumes: `CompletionUsage.completionTokensDetails().reasoningTokens()` (`Optional<Long>`).
- Produces: `AgentMetrics.TokenUsage.reasoningTokenCount` populated from the response instead of hard-coded 0.

- [ ] **Step 1: Write the failing test**

```java
@Test
void surfacesReasoningTokensFromUsage() {
  final var completion = completionWithUsage(/* prompt */ 100, /* completion */ 200, /* reasoning */ 128);
  final var result = converter.toResult(completion, Duration.ZERO);
  assertThat(result.metrics().tokenUsage().reasoningTokenCount()).isEqualTo(128);
}
```

(Build `completionWithUsage` with `CompletionUsage.CompletionTokensDetails.builder().reasoningTokens(128).build()`.)

- [ ] **Step 2: Run to verify it fails**

Run: `mvn test -pl connectors/agentic-ai/connector-agentic-ai -Dtest=OpenAiCompletionsResponseConverterTest`
Expected: FAIL (reasoningTokenCount == 0).

- [ ] **Step 3: Map reasoning tokens**

Replace the deliberate `reasoningTokenCount(0)` and its comment in `toTokenUsage(CompletionUsage usage)`:

```java
final long reasoningTokens =
    usage
        .completionTokensDetails()
        .flatMap(CompletionUsage.CompletionTokensDetails::reasoningTokens)
        .orElse(0L);
// ...
    .reasoningTokenCount((int) reasoningTokens)
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn test -pl connectors/agentic-ai/connector-agentic-ai -Dtest=OpenAiCompletionsResponseConverterTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/openai/family/completions/OpenAiCompletionsResponseConverter.java \
        connectors/agentic-ai/connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/framework/openai/family/completions/OpenAiCompletionsResponseConverterTest.java
git commit -m "feat(agentic-ai): surface reasoning tokens from native OpenAI Chat Completions usage"
```

---

### Task 4: Update Completions-family javadoc + validator test

**Files:**
- Modify: `OpenAiCompletionsRequestConverter.java` and `OpenAiCompletionsResponseConverter.java` class javadocs (they currently state "no reasoning (deferred)").
- Test: `.../framework/openai/OpenAiRequestValidatorTest.java`

- [ ] **Step 1: Add validator test asserting effort is now accepted for a reasoning-capable completions model and still rejected for gpt-4o**

```java
@Test
void acceptsEffortForReasoningCapableCompletionsModel() {
  assertThatCode(() -> OpenAiRequestValidator.validate(connection(OpenAiEffort.HIGH), gpt5Reasoning(), true, "gpt-5"))
      .doesNotThrowAnyException();
}

@Test
void rejectsEffortForNonReasoningCompletionsModel() {
  assertThatThrownBy(() -> OpenAiRequestValidator.validate(connection(OpenAiEffort.HIGH), null, true, "gpt-4o"))
      .isInstanceOf(ConnectorException.class)
      .hasMessageContaining("does not support reasoning effort");
}
```

- [ ] **Step 2: Run**

Run: `mvn test -pl connectors/agentic-ai/connector-agentic-ai -Dtest=OpenAiRequestValidatorTest`
Expected: PASS (no production change needed — the validator already behaves this way; this locks it in).

- [ ] **Step 3: Update javadocs**

Remove the "no reasoning / deferred" wording from both Completions converter class javadocs; state that reasoning is input-only (effort forwarded as `reasoning_effort`, reasoning-token count surfaced, no reasoning content in the response).

- [ ] **Step 4: Commit**

```bash
git add connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/openai/family/completions/OpenAiCompletionsRequestConverter.java \
        connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/openai/family/completions/OpenAiCompletionsResponseConverter.java \
        connectors/agentic-ai/connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/framework/openai/OpenAiRequestValidatorTest.java
git commit -m "docs(agentic-ai): document input-only reasoning on the OpenAI Completions family"
```

---

### Task 5: Add gpt-5 Completions row to the acceptance IT (compare the 3)

**Files:**
- Modify: `connectors-e2e-test/connectors-e2e-test-agentic-ai/.../NativeProviderAcceptanceIT.java`

**Interfaces:**
- Consumes: the `NativeProvider` / `openaiDirect(...)` factory and the `REASONING` capability already used by the gpt-5 responses row.

- [ ] **Step 1: Add the row**

Add alongside the existing gpt-4o completions row, keeping the gpt-5 responses row unchanged (so the suite exercises gpt-5 on **both** APIs plus the gpt-4o baseline — "the 3"):

```java
// gpt-5 on the Completions API family: reasoning via reasoning_effort (input-only; no server
// tools, no reasoning content in the response). Effort "high" forces reasoning tokens.
openaiDirect(
    "completions",
    "gpt-5",
    Map.of(
        Capability.STRUCTURED_OUTPUT, Map.of(),
        Capability.MULTIMODAL_USER_MESSAGE, Map.of(),
        Capability.MULTIMODAL_TOOL_RESULT, Map.of(),
        Capability.PROMPT_CACHING, Map.of(),
        Capability.REASONING,
            Map.of("configuration.openai.model.parameters.effort", "high")),
    true),
```

- [ ] **Step 2: Compile the e2e module**

Run: `mvn test-compile -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Real-API verification — ASK FIRST**

Do **not** run real-API e2e without explicit permission. When granted, verify the reasoning + structured-output scenarios pass for gpt-5-completions and that `reasoningTokenCount > 0` is reported (proves Task 3 wiring end-to-end). Mechanics: `RUN_NATIVE_LLM_E2E=true` with the OpenAI key injected via the secrets mechanism; run only the relevant methods; read metrics from the surefire `*-output.txt`.

- [ ] **Step 4: Commit**

```bash
git add connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/e2e/NativeProviderAcceptanceIT.java
git commit -m "test(agentic-ai): exercise gpt-5 reasoning on both OpenAI API families in acceptance IT"
```

---

## Notes / findings feeding this plan

- **Performance is not the motivation.** Latency probing (2026-07-16) showed our pipeline overhead is ~50–130ms/turn; wall-clock is dominated by the OpenAI API, and gpt-5's effort knob does **not** reliably change latency (run-to-run reasoning-token variance dominates — a "minimal" run spent 1088 reasoning tokens / 17s on a turn a "high" run did in 4.6s). This feature is about **capability parity** (letting users pick effort on Completions and observe reasoning tokens), not speed.
- **Scope guard:** reasoning stays input-only on Completions. No `ReasoningContent`, no encrypted round-trip, no content-model/serialization changes — that is Responses-only and must not be touched here.
