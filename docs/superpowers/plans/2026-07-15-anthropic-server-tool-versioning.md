# Anthropic Server-Tool Versioning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Anthropic `code_execution` server tool version-configurable (mirroring the existing `web_search`/`web_fetch` pattern) and bump the default server-tool versions to the latest revisions, so the native Anthropic path emits GA tools with no unnecessary beta headers and Skills + code execution + web tools coexist out of the box.

**Architecture:** The native Anthropic request converter (`AnthropicMessageRequestConverter`) currently hardcodes `code_execution` to `20250825` and always emits the `code-execution-2025-08-25` beta header. This chunk replaces that with an `addCodeExecutionTool(builder, version)` switch (version → typed SDK builder + optional beta header), threads a new `codeExecutionVersion` template field through the connection, and bumps the `web_search`/`web_fetch` blank-defaults from the basic/direct revisions to the latest dynamic-filtering revisions (`20260318`). Only the legacy `code_execution_20250522` revision emits a beta header; all GA revisions emit none.

**Tech Stack:** Java 21, anthropic-java 2.48.0 (beta messages client), Spring Boot, JUnit 5 + Mockito + AssertJ, Camunda element-template generator, WireMock-based e2e (Camunda Process Test).

## Global Constraints

- **BC on Camunda 8.9-persisted data is priority #1**, but the v2 native Anthropic path is **unreleased** — there is no released consumer of the `code_execution_20250825`/`code-execution-2025-08-25` wire shape, so changing those defaults and dropping that beta header carries **no** backward-compatibility obligation.
- **No template `version` bump.** The v2 templates are unreleased; this chunk adds a field and changes field defaults only. The generated JSON `version` attribute on each template must be unchanged in the diff.
- **Element templates are generated, never hand-edited.** Regenerate with `mvn clean process-classes -f connectors/agentic-ai/pom.xml` (the generator + the groovy subprocess-transform both bind to the `process-classes` phase; `mvn compile` alone does **not** regenerate — the AGENTS.md "Definition of Done" line saying `mvn clean compile` is wrong for this module).
- **Run all `mvn`/`git` commands with the sandbox disabled** (`dangerouslyDisableSandbox: true`) — the sandbox breaks Mockito's MockMaker and blocks the network.
- **The native path stays on the beta messages client** (`com.anthropic.models.beta.*`); this is required for Skills and is out of scope to change. effort/thinking/web/code_execution ride the beta types by consequence, not necessity.
- **Only `code_execution_20250522` (legacy, Python-only) requires a beta header** (`AnthropicBeta.CODE_EXECUTION_2025_05_22` = `"code-execution-2025-05-22"`). `20250825`/`20260120`/`20260521` are GA and emit **no** `anthropic-beta` header. Skills' own headers (`skills-2025-10-02`, `files-api-2025-04-14`) are unrelated and stay untouched.
- **Never push. Commit only.** The driver performs any amend/rebase; implementer subagents only `git add` + `git commit`, never checkout/reset/revert/stash/rebase/push.

---

## Background: the corrected combination story (drives the tooltips + class comment)

The current code comments claim the dynamic-filtering web revisions (`web_search_20260209`+) are "not yet compatible with Skills or code execution in the same request." **That is wrong.** Verified against Anthropic docs:

- From `20260209` onward the web tools default `allowed_callers` to `["code_execution_20260120"]` ("dynamic filtering": the tool runs *inside* code execution). The API auto-provisions its own `code_execution` tool for this.
- If you *also* provide your own `code_execution` tool alongside these web revisions, it **must be `20260120` or later** — the API rejects older code-execution versions in that combination. With our new default `code_execution_20260521` (≥ `20260120`), all three (Skills, code execution, dynamic web tools) coexist with **no** `allowed_callers` configuration needed.
- The `400 "Auto-injecting tools would conflict with existing tool names: ['code_execution']"` we previously hit was self-inflicted: we pinned `code_execution_20250825` (too old) alongside a dynamic web revision. Bumping code_execution to the latest GA fixes it.
- Basic revisions (`web_search_20250305` / `web_fetch_20250910`) call directly, are ZDR-eligible, and work on all models — kept as downgrade options via the version field for ZDR or non-programmatic/older models.
- `allowed_callers` / a "call directly" toggle is **explicitly deferred** (not in this chunk).

## File Structure

- `.../model/request/chatmodel/AnthropicChatModel.java` — add the `codeExecutionVersion` template field; bump `webSearchVersion`/`webFetchVersion` `defaultValue`s and rewrite their tooltips.
- `.../framework/anthropic/AnthropicMessageRequestConverter.java` — add `addCodeExecutionTool(...)` + version constants; drop `CODE_EXECUTION_BETA`; bump web blank-defaults; rewrite the class-level tool comment.
- `.../test/.../framework/anthropic/AnthropicMessageRequestConverterTest.java` — update the `model(...)` helper signature + call sites; update the skills/code-exec/web tests to the new defaults; add code_execution version tests.
- `connectors/agentic-ai/connector-agentic-ai/element-templates/*.v2.json` (+ `hybrid/*.v2-hybrid.json`) — regenerated JSON (4 files).
- `connectors-e2e-test/.../wiremock/anthropic/NativeAnthropicSkillsAndToolsWireFormatTest.java` — update wire assertions to the new defaults; add a combined all-on test.

---

### Task 1: Version-configurable `code_execution` + bumped web defaults (code + unit tests + templates)

**Files:**
- Modify: `connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/model/request/chatmodel/AnthropicChatModel.java:100-158`
- Modify: `connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/anthropic/AnthropicMessageRequestConverter.java` (imports ~11-31, class comment ~71-105, `applySkillsAndBuiltInTools` ~393-396, `addWebSearchTool`/`addWebFetchTool` ~412-442)
- Test: `connectors/agentic-ai/connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/framework/anthropic/AnthropicMessageRequestConverterTest.java`
- Regenerate: `connectors/agentic-ai/connector-agentic-ai/element-templates/agenticai-ai-agent-task.v2.json`, `agenticai-ai-agent-subprocess.v2.json`, `hybrid/agenticai-ai-agent-task.v2-hybrid.json`, `hybrid/agenticai-ai-agent-subprocess.v2-hybrid.json`

**Interfaces:**
- Consumes: `AnthropicChatModel.AnthropicConnection` (a `record` — positional; adding a component shifts every positional constructor call). `MessageCreateParams.Builder#addTool(...)`, `#addBeta(AnthropicBeta)`. SDK builders `BetaCodeExecutionTool20250522/20250825/20260120/20260521` (each `.builder().build()`, and `.type(JsonValue)` for the raw escape hatch). `AnthropicBeta.CODE_EXECUTION_2025_05_22`.
- Produces: `AnthropicConnection#codeExecutionVersion()` returning `@Nullable String`; converter constants `CODE_EXECUTION_DEFAULT_VERSION = "code_execution_20260521"`, `WEB_SEARCH_DEFAULT_VERSION = "web_search_20260318"`, `WEB_FETCH_DEFAULT_VERSION = "web_fetch_20260318"`; private method `addCodeExecutionTool(MessageCreateParams.Builder, @Nullable String)`.

- [ ] **Step 1: Add the `codeExecutionVersion` field to `AnthropicConnection`**

In `AnthropicChatModel.java`, insert a new component immediately after `enableCodeExecution` (currently line 100) so the record order becomes `..., enableCodeExecution, codeExecutionVersion, enableWebSearch, webSearchVersion, enableWebFetch, webFetchVersion`. Add the field with its `@TemplateProperty` annotation:

```java
          @Nullable Boolean enableCodeExecution,
      @TemplateProperty(
              group = "skills",
              label = "Code execution tool version",
              tooltip =
                  "Anthropic <code>code_execution</code> tool version string (wire <code>type</code>). "
                      + "Defaults to the latest GA revision <code>code_execution_20260521</code>, which "
                      + "needs no beta header and is required (version <code>20260120</code> or later) for "
                      + "the default dynamic-filtering web tools to run in the same request. This version "
                      + "also applies to the <code>code_execution</code> tool that Skills provision "
                      + "automatically. The legacy <code>code_execution_20250522</code> revision is "
                      + "Python-only and additionally sends the <code>code-execution-2025-05-22</code> "
                      + "beta header.",
              type = TemplateProperty.PropertyType.String,
              feel = FeelMode.optional,
              defaultValue = "code_execution_20260521",
              optional = true,
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "configuration.anthropic.enableCodeExecution",
                      equalsBoolean = TemplateProperty.EqualsBoolean.TRUE))
          @Nullable String codeExecutionVersion,
```

- [ ] **Step 2: Bump the web-version defaults and rewrite their tooltips**

In `AnthropicChatModel.java`, for `webSearchVersion` (currently line 111-129) change `defaultValue = "web_search_20250305"` → `defaultValue = "web_search_20260318"` and replace the tooltip with:

```java
              tooltip =
                  "Anthropic <code>web_search</code> tool version string (wire <code>type</code>). "
                      + "The default <code>web_search_20260318</code> uses dynamic filtering (the tool "
                      + "runs inside code execution) and works alongside Skills and code execution when "
                      + "the code execution tool is version <code>20260120</code> or later (the default). "
                      + "It requires a programmatic-tool-calling model and is not ZDR-eligible. Downgrade "
                      + "to a basic/direct revision such as <code>web_search_20250305</code> for ZDR or "
                      + "older/non-programmatic models.",
```

For `webFetchVersion` (currently line 140-158) change `defaultValue = "web_fetch_20250910"` → `defaultValue = "web_fetch_20260318"` and replace the tooltip with:

```java
              tooltip =
                  "Anthropic <code>web_fetch</code> tool version string (wire <code>type</code>). "
                      + "The default <code>web_fetch_20260318</code> uses dynamic filtering (the tool "
                      + "runs inside code execution) and works alongside Skills and code execution when "
                      + "the code execution tool is version <code>20260120</code> or later (the default). "
                      + "It requires a programmatic-tool-calling model and is not ZDR-eligible. Downgrade "
                      + "to a basic/direct revision such as <code>web_fetch_20250910</code> for ZDR or "
                      + "older/non-programmatic models.",
```

- [ ] **Step 3: Update converter imports and version constants**

In `AnthropicMessageRequestConverter.java`, add these imports alongside the existing `BetaCodeExecutionTool20250825` import (keep it):

```java
import com.anthropic.models.beta.messages.BetaCodeExecutionTool20250522;
import com.anthropic.models.beta.messages.BetaCodeExecutionTool20260120;
import com.anthropic.models.beta.messages.BetaCodeExecutionTool20260521;
```

Replace the class-level comment block + constants currently at lines 71-105 (the `CODE_EXECUTION_BETA` constant, the long web-tool paragraph, and `WEB_SEARCH_BASIC_VERSION`/`WEB_FETCH_BASIC_VERSION`) with:

```java
  // Built-in server tools (code_execution / web_search / web_fetch) are user-version-configurable
  // because the revision determines calling behavior and header requirements. Defaults track the
  // latest GA/dynamic revisions; the version fields let users downgrade for ZDR or older models.
  //
  // code_execution: the GA revisions (20250825/20260120/20260521) need NO anthropic-beta header;
  // only the legacy Python-only 20250522 requires code-execution-2025-05-22. The default 20260521
  // (>= 20260120) is also what lets the default dynamic-filtering web tools run in the same request
  // (from 20260209 the web tools default allowedCallers to ["code_execution_20260120"], sharing the
  // container; providing an OLDER code_execution alongside them is rejected by the API). The same
  // resolved version applies whether code execution is enabled explicitly or implicitly via Skills.
  //
  // web_search/web_fetch are General Availability (no beta header at any revision). The default
  // dynamic-filtering revisions (20260318) run inside code execution; the basic/direct revisions
  // (20250305/20250910) call directly, are ZDR-eligible, and work on all models (downgrade path).
  //
  // Unrecognized versions fall back to the raw-type escape hatch: the latest typed builder with
  // `.type(JsonValue.from(raw))` overridden. Verified via a serialization round-trip (see the
  // converter test) that the generated build() does not validate and `name` defaults independently
  // of `type`, so this produces `{"type":"<raw>","name":"<tool>"}` without any other field changing.
  static final String CODE_EXECUTION_DEFAULT_VERSION = "code_execution_20260521";

  static final String WEB_SEARCH_BASIC_VERSION = "web_search_20250305";

  static final String WEB_SEARCH_DEFAULT_VERSION = "web_search_20260318";

  static final String WEB_FETCH_BASIC_VERSION = "web_fetch_20250910";

  static final String WEB_FETCH_DEFAULT_VERSION = "web_fetch_20260318";
```

(`WEB_SEARCH_BASIC_VERSION`/`WEB_FETCH_BASIC_VERSION` stay: they remain the basic-revision `case` labels and the escape-hatch base builders. Only the blank-default fallback moves to the new `*_DEFAULT_VERSION` constants.)

- [ ] **Step 4: Route code_execution through the new switch**

In `applySkillsAndBuiltInTools` (currently lines 393-396) replace:

```java
    if (hasSkills || Boolean.TRUE.equals(connection.enableCodeExecution())) {
      builder.addTool(BetaCodeExecutionTool20250825.builder().build());
      builder.addBeta(CODE_EXECUTION_BETA);
    }
```

with:

```java
    if (hasSkills || Boolean.TRUE.equals(connection.enableCodeExecution())) {
      addCodeExecutionTool(builder, connection.codeExecutionVersion());
    }
```

Update the surrounding javadoc on `applySkillsAndBuiltInTools` (lines ~358-360) that says "its beta header emitted AT MOST ONCE" — the GA default emits no beta header; reword to: "code_execution is added AT MOST ONCE (skills auto-require it; the explicit toggle may independently request it; both share this single addition), at the configured version, so enabling both never duplicates the tool."

- [ ] **Step 5: Add the `addCodeExecutionTool` method**

Add next to `addWebSearchTool`/`addWebFetchTool` (after line 442):

```java
  /**
   * Adds the {@code code_execution} server tool for the given (optional) version string, defaulting
   * to the latest GA revision (see the class-level comment). Only the legacy {@code 20250522}
   * revision requires a beta header; the GA revisions emit none. Unknown versions use the raw-type
   * escape hatch on the latest revision's builder.
   */
  private void addCodeExecutionTool(MessageCreateParams.Builder builder, @Nullable String version) {
    final String resolved =
        (version == null || version.isBlank()) ? CODE_EXECUTION_DEFAULT_VERSION : version;
    switch (resolved) {
      case "code_execution_20250522" -> {
        builder.addTool(BetaCodeExecutionTool20250522.builder().build());
        builder.addBeta(AnthropicBeta.CODE_EXECUTION_2025_05_22);
      }
      case "code_execution_20250825" ->
          builder.addTool(BetaCodeExecutionTool20250825.builder().build());
      case "code_execution_20260120" ->
          builder.addTool(BetaCodeExecutionTool20260120.builder().build());
      case CODE_EXECUTION_DEFAULT_VERSION ->
          builder.addTool(BetaCodeExecutionTool20260521.builder().build());
      default ->
          builder.addTool(
              BetaCodeExecutionTool20260521.builder().type(JsonValue.from(resolved)).build());
    }
  }
```

- [ ] **Step 6: Bump the web blank-default fallbacks**

In `addWebSearchTool` (line 413-414) change the fallback from `WEB_SEARCH_BASIC_VERSION` to `WEB_SEARCH_DEFAULT_VERSION`:

```java
    final String resolved =
        (version == null || version.isBlank()) ? WEB_SEARCH_DEFAULT_VERSION : version;
```

In `addWebFetchTool` (line 431-432) change the fallback from `WEB_FETCH_BASIC_VERSION` to `WEB_FETCH_DEFAULT_VERSION`:

```java
    final String resolved =
        (version == null || version.isBlank()) ? WEB_FETCH_DEFAULT_VERSION : version;
```

Leave both `switch` bodies unchanged — they already have `case`s for the basic revision, `20260209`, and `20260318` (fetch also `20260309`), and the default (blank) now resolves to `20260318`, which has a `case`. Update each method's javadoc first sentence: replace "defaulting to the combination-safe basic/direct-calling revision" with "defaulting to the latest dynamic-filtering revision (see the class-level comment above)".

- [ ] **Step 7: Update the converter unit test helper + existing call sites**

In `AnthropicMessageRequestConverterTest.java`, update the `model(...)` helper (lines 86-102) to add a `codeExecutionVersion` parameter positioned to match the new record order. Current inner builder call passes `(parameters, skills, enableCodeExecution, enableWebSearch, webSearchVersion, enableWebFetch, webFetchVersion)`; it must become `(parameters, skills, enableCodeExecution, codeExecutionVersion, enableWebSearch, webSearchVersion, enableWebFetch, webFetchVersion)`. Add `@Nullable String codeExecutionVersion` to the full `model(...)` signature and thread it through. For the 5-arg convenience overload (lines 75-81) that omits versions, pass `null` for `codeExecutionVersion` in its delegation. Fix every call site that used positional web-version args so the new parameter lands in the right slot (the compiler will flag them).

- [ ] **Step 8: Run the test to verify it fails to compile / fails**

Run (sandbox disabled):
```
mvn -q -o test-compile -f connectors/agentic-ai/pom.xml
```
Expected: compile error until the helper + call sites are consistent, then the next steps' new tests fail. (If offline `-o` fails to resolve, drop `-o`.)

- [ ] **Step 9: Update the existing skills/code-exec/web default assertions**

In `AnthropicMessageRequestConverterTest.java`:

- `emitsContainerSkillsCodeExecutionToolAndBetaHeadersWhenSkillsConfigured` (~562): change the expected code_execution tool type from `code_execution_20250825` to `code_execution_20260521`; change the `betas()` assertion to expect **only** `skills-2025-10-02` and `files-api-2025-04-14` (no `code-execution-*`). Rename to `emitsContainerSkillsCodeExecutionToolAndSkillBetaHeadersWhenSkillsConfigured`.
- `enableCodeExecutionAddsCodeExecutionToolAndBetaHeaderWithoutSkills` (~623): expect tool type `code_execution_20260521` and `assertThat(params.betas()).isEmpty()`. Rename to `enableCodeExecutionAddsLatestCodeExecutionToolWithoutBetaHeader`.
- `skillsPlusEnabledCodeExecutionToggleYieldsExactlyOneCodeExecutionToolAndNoDuplicateBeta` (~770): expect the single tool type `code_execution_20260521` and `betas()` containing only the two skill betas.
- `nullWebSearchVersionDefaultsToBasicDirectVersion` (~669): expect `web_search_20260318`. Rename to `nullWebSearchVersionDefaultsToLatestDynamicVersion`.
- Any web-fetch analogue asserting the blank default: expect `web_fetch_20260318`.

- [ ] **Step 10: Add code_execution version unit tests**

Append these tests (mirror the existing web-version tests' structure — `ctx(model(...), null)`, `convert(...)`, then assert `params.tools()` types and `params.betas()`):

```java
  @Test
  void defaultCodeExecutionVersionIsLatestGaWithoutBetaHeader() {
    final var params =
        converter.convert(
            ctx(model(null, null, true, null, null, null, null, null), null),
            List.of(),
            ConversationSnapshot.empty());

    assertThat(toolTypes(params)).contains("code_execution_20260521");
    assertThat(params.betas()).isEmpty();
  }

  @Test
  void legacyCodeExecutionVersionAddsBetaHeader() {
    final var params =
        converter.convert(
            ctx(model(null, null, true, "code_execution_20250522", null, null, null, null), null),
            List.of(),
            ConversationSnapshot.empty());

    assertThat(toolTypes(params)).contains("code_execution_20250522");
    assertThat(params.betas().orElseThrow()).containsExactly("code-execution-2025-05-22");
  }

  @Test
  void gaCodeExecutionVersionOverridesSelectRequestedTypedToolWithoutBetaHeader() {
    for (final String version :
        List.of("code_execution_20250825", "code_execution_20260120", "code_execution_20260521")) {
      final var params =
          converter.convert(
              ctx(model(null, null, true, version, null, null, null, null), null),
              List.of(),
              ConversationSnapshot.empty());

      assertThat(toolTypes(params)).as(version).contains(version);
      assertThat(params.betas()).as(version).isEmpty();
    }
  }

  @Test
  void unknownCodeExecutionVersionFallsBackToRawTypeOnLatestTool() {
    final var params =
        converter.convert(
            ctx(model(null, null, true, "code_execution_29991231", null, null, null, null), null),
            List.of(),
            ConversationSnapshot.empty());

    assertThat(toolTypes(params)).contains("code_execution_29991231");
    assertThat(params.betas()).isEmpty();
  }

  @Test
  void skillsUseConfiguredCodeExecutionVersion() {
    final var params =
        converter.convert(
            ctx(
                model(null, List.of("pptx"), null, "code_execution_20260120", null, null, null, null),
                null),
            List.of(),
            ConversationSnapshot.empty());

    assertThat(toolTypes(params)).contains("code_execution_20260120");
    assertThat(params.betas().orElseThrow())
        .contains("skills-2025-10-02", "files-api-2025-04-14")
        .doesNotContain("code-execution-2025-05-22");
  }
```

If a `toolTypes(params)` helper does not already exist in the test, add one that maps `params.tools()` to each tool's wire `type` string (reuse whatever extraction the existing `code_execution_20250825`/`web_search_20250305` assertions used — see `emitsContainerSkills...` and `enableWebSearchAndWebFetchTogetherAddsBothTools`). Adjust the exact `model(...)` argument order to whatever Step 7 established; the calls above assume `(parameters, skills, enableCodeExecution, codeExecutionVersion, enableWebSearch, webSearchVersion, enableWebFetch, webFetchVersion)`.

- [ ] **Step 11: Add a code_execution escape-hatch serialization round-trip test**

Mirror `defaultWebSearchToolSerializesToBasicDirectVersionWireShape` (~739): build the default code_execution tool and assert its serialized wire shape is `{"type":"code_execution_20260521","name":"code_execution"}` (only `type` differs for the raw escape hatch). Name it `defaultCodeExecutionToolSerializesToLatestWireShape`.

- [ ] **Step 12: Run the converter unit tests to green**

Run (sandbox disabled):
```
mvn -q test -f connectors/agentic-ai/pom.xml -pl connector-agentic-ai -Dtest=AnthropicMessageRequestConverterTest
```
Expected: BUILD SUCCESS, all `AnthropicMessageRequestConverterTest` tests pass. Read `connectors/agentic-ai/connector-agentic-ai/target/surefire-reports/*AnthropicMessageRequestConverterTest.txt` to confirm 0 failures/errors.

- [ ] **Step 13: Regenerate element templates and verify the diff**

Run (sandbox disabled):
```
mvn -q clean process-classes -f connectors/agentic-ai/pom.xml
git --no-pager diff --stat connectors/agentic-ai/connector-agentic-ai/element-templates/
```
Expected: the 4 v2 JSON files change. Inspect the full diff (`git --no-pager diff connectors/agentic-ai/connector-agentic-ai/element-templates/`) and confirm **only**:
- a new `codeExecutionVersion` property (id `configuration.anthropic.codeExecutionVersion` or similar), conditioned on `enableCodeExecution`, default `code_execution_20260521`, with the new tooltip;
- `webSearchVersion` default `web_search_20250305` → `web_search_20260318` + new tooltip;
- `webFetchVersion` default `web_fetch_20250910` → `web_fetch_20260318` + new tooltip.

Confirm each template's top-level `version` attribute is **unchanged**. If anything else changed, stop and report.

- [ ] **Step 14: Build the module to confirm green (spotless/license included)**

Run (sandbox disabled):
```
mvn -q clean install -DskipTests -f connectors/agentic-ai/pom.xml
```
Expected: BUILD SUCCESS (this runs spotless + license formatting as part of the build; if it reformats, re-add the changed files).

- [ ] **Step 15: Commit**

```bash
git add connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/model/request/chatmodel/AnthropicChatModel.java \
        connectors/agentic-ai/connector-agentic-ai/src/main/java/io/camunda/connector/agenticai/aiagent/framework/anthropic/AnthropicMessageRequestConverter.java \
        connectors/agentic-ai/connector-agentic-ai/src/test/java/io/camunda/connector/agenticai/aiagent/framework/anthropic/AnthropicMessageRequestConverterTest.java \
        connectors/agentic-ai/connector-agentic-ai/element-templates/
git commit -m "Make Anthropic code execution version configurable and default server tools to latest revisions"
```

---

### Task 2: Native e2e wire-format coverage for the new server-tool defaults

**Files:**
- Modify: `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/wiremock/anthropic/NativeAnthropicSkillsAndToolsWireFormatTest.java`

**Interfaces:**
- Consumes: the Task 1 defaults on the wire — code_execution `code_execution_20260521` (no beta header), web `web_search_20260318`/`web_fetch_20260318`. The test asserts against the recorded WireMock request body/headers, not a live API.

- [ ] **Step 1: Update the tool-type assertions to the new defaults**

In `NativeAnthropicSkillsAndToolsWireFormatTest.java`, the assertion (~lines 173-178) currently expects:
```java
        .filteredOn("code_execution_20250825"::equals)
        ...
    assertThat(toolTypes).as("tools[].type").contains("web_search_20250305", "web_fetch_20250910");
```
Change the code_execution filter to `"code_execution_20260521"::equals` and the web contains-assertion to `contains("web_search_20260318", "web_fetch_20260318")`. Update the surrounding javadoc (~163-165) accordingly.

- [ ] **Step 2: Update the `anthropic-beta` header assertion — code_execution header is gone**

The assertion (~lines 189-196) currently expects the header to contain `code-execution-2025-08-25`, `skills-2025-10-02`, `files-api-2025-04-14`. Change it to assert the header contains **only** the two skill betas and does **not** contain any `code-execution-*` value:
```java
    final var betaValues = loggedRequest.header("anthropic-beta").values();
    assertThat(betaValues)
        .as("anthropic-beta header values")
        .contains("skills-2025-10-02", "files-api-2025-04-14")
        .noneMatch(v -> v.startsWith("code-execution-"));
```
Update the method's javadoc (~182-184) to state the GA code_execution default emits no beta header.

- [ ] **Step 3: Add a combined all-on coexistence test**

Add a test that configures Skills **and** the code execution toggle **and** web search **and** web fetch on the same request (all defaults), then asserts the recorded request carries the default dynamic web tools (`web_search_20260318`, `web_fetch_20260318`), exactly one `code_execution_20260521` tool, and an `anthropic-beta` header with the two skill betas and no `code-execution-*`. Follow the existing test's request-recording and JSON-extraction helpers (`loggedRequests`, `toolTypes`, `loggedRequest.header(...)`). Name it `skillsCodeExecutionAndDynamicWebToolsCoexistOnDefaults`.

- [ ] **Step 4: Run the e2e test to green**

Run (sandbox disabled; requires `element-templates-cli` on PATH):
```
mvn -q test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -Dtest=NativeAnthropicSkillsAndToolsWireFormatTest
```
Expected: BUILD SUCCESS. Read `connectors-e2e-test/connectors-e2e-test-agentic-ai/target/surefire-reports/*NativeAnthropicSkillsAndToolsWireFormatTest.txt` to confirm 0 failures/errors. Also confirm `NativeAnthropicCodeExecutionServerToolE2eTest` still passes if it asserts any code_execution version/header (run it the same way; update its expectations to the new default if it hardcoded `20250825`/`code-execution-2025-08-25`).

- [ ] **Step 5: Commit**

```bash
git add connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/wiremock/anthropic/
git commit -m "Cover Anthropic server-tool version defaults in native wire-format e2e"
```

---

## Self-Review

**Spec coverage:**
- code_execution version-configurable → Task 1 Steps 1, 3–5, 10–11. ✓
- Default `code_execution_20260521`, GA (no beta header), legacy `20250522` header only → Steps 3, 5, 9, 10. ✓
- web defaults → `20260318` → Steps 2, 6, 9. ✓
- Same code_execution version for explicit toggle AND Skills path → Step 4 (shared addition) + Step 10 `skillsUseConfiguredCodeExecutionVersion`. ✓
- Corrected combination story in tooltips + class comment → Steps 1, 2, 3, 6. ✓
- Templates regenerated, no version bump → Step 13. ✓
- e2e wire coverage incl. all-on coexistence → Task 2. ✓
- `allowed_callers` / "call directly" toggle deferred → not implemented (per Global Constraints). ✓

**Placeholder scan:** no TBD/TODO/"handle edge cases"; every code step has complete code. The only "follow the existing pattern" references (Step 10 `toolTypes` helper, Step 11 round-trip, Task 2 Step 3 helpers) point at named existing methods in the same test file, with the exact assertion values given. ✓

**Type consistency:** `codeExecutionVersion` (String, nullable) consistent across model field, `model(...)` helper arg order, and converter method. Constants `CODE_EXECUTION_DEFAULT_VERSION`/`WEB_SEARCH_DEFAULT_VERSION`/`WEB_FETCH_DEFAULT_VERSION` named identically everywhere. `AnthropicBeta.CODE_EXECUTION_2025_05_22` verified to exist in anthropic-java 2.48.0. ✓
