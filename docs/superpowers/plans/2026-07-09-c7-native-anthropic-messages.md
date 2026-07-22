# C7 — Native Anthropic Messages (direct backend)

Implementation plan for chunk **C7** of the #7211 "own the LLM layer" vertical pilot.
Module: `connectors/agentic-ai/connector-agentic-ai`. Stacked on the C6 tip `e23dd703df`.

## Goal

A **native** `ChatModelApi` + `ChatModelApiFactory` for the **Anthropic Messages API, DIRECT
backend only**, over the official `com.anthropic:anthropic-java` SDK, running parallel to the
untouched LangChain4j (L4J) bridge and overriding it for the v2/wire-format-first config. This is
the first real implementation that fills the fail-loud registry gap C6 left: a direct-backend
Anthropic v2 config now resolves to this native path; bedrock still fails loud (deferred to C11).

## Architecture

- New package `io.camunda.connector.agenticai.aiagent.provider.anthropic`, importing only its own
  vendor SDK (`com.anthropic.*`) — obeying the invariant that native impls live under their own
  `framework/<provider>/**` package. The provider-neutral SPI (`framework/api/**`) and domain model
  (`model/**`) never import `com.anthropic.*`; translation is confined to this package.
- The native drives the vendor **streaming** endpoint (`messages().createStreaming(...)`) internally
  and assembles the full `Message` via the SDK's `MessageAccumulator`; the SPI stays the synchronous
  `ChatModelResult call(ChatModelRequest)` returning `Completed | Continuation` (decision D-stream).
- Client is built **per `call()`** in try-with-resources (see Decisions); capabilities are resolved
  once at `create()` and cached on the `ChatModelApi` instance.
- Proxy support is shared through `HttpTransportSupport` via a new **provider-neutral OkHttp proxy**
  method (decision D1), reusable by C8 (openai-java, also OkHttp).

## Tech Stack

- Java 21; JUnit 5, Mockito, AssertJ. `@NullMarked` per package.
- New dependency `com.anthropic:anthropic-java:2.48.0` (pinned; not in any BOM — declared explicitly
  in the module pom). Transitively pulls `anthropic-java-client-okhttp` → `com.squareup.okhttp3:okhttp:4.12.0`
  and `anthropic-java-core`. **OkHttp is available transitively; no separate okhttp declaration needed.**
- WireMock wire-format e2e in the separate `connectors-e2e-test/connectors-e2e-test-agentic-ai` module.

## Global Constraints (verbatim from the C7 planner context)

- **BC on Camunda 8.9-persisted data is the #1 priority.** C7 adds a new path; it must NOT change any
  persisted type, discriminator value, field name, or v1/v2 template JSON. Do NOT touch the L4J bridge's
  behavior. `AgentConfiguration` is transient (safe), but stored `AgentContext`/message/content JSON is not.
- **v1 must stay byte-identical** on every e2e-reachable bridge path. The native path is only reachable
  via the v2 config today.
- Java 21. Module `connectors/agentic-ai/connector-agentic-ai`. Build/tooling: `-pl connector-agentic-ai
  -f connectors/agentic-ai/pom.xml`; run Maven/tests OUTSIDE the sandbox (sandbox breaks Mockito MockMaker).
- The separate `connectors-e2e-test/connectors-e2e-test-agentic-ai` module must test-COMPILE and the
  affected e2e classes must PASS. Watch stale timestamped 8.10.0-SNAPSHOT jars in ~/.m2.
- New dependency `com.anthropic:anthropic-java` — pin a version (planner picks a current stable; note it
  is NOT in any BOM today, so declare the version explicitly in the module pom, consistent with repo style).
- License header on every new file (proprietary header, as in existing files).
- Commit messages describe the actual change (no "wip"/"task N"/"review round"). One commit per task,
  stacked on C6 tip `e23dd703df`.
- No pushing/PR (driver handles that on explicit user confirmation).

### C7 scope discipline

- **DIRECT backend only.** `supports()` matches Anthropic + direct backend; bedrock still fails loud
  (C11). No SPI change (sync `Completed | Continuation` contract stays).
- **D2 depth = Core + read reasoning.** IN: text, `tool_use`→`ToolCall`, multimodal user/tool-result
  content as native image/document blocks, `pause_turn`→`Continuation`, full token metrics incl.
  cache/reasoning, INBOUND Anthropic `thinking`→`ReasoningContent` (READ-ONLY).
- **DEFERRED (do NOT implement; list as follow-ups):** request-side extended-thinking config,
  `ReasoningContent.providerPayload` signature round-trip back to Anthropic, prompt `cache_control`
  WRITES, capability-resolution caching (C3f).

### License header (prepend to every new `.java` file)

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
```

## Decisions (resolved during planning)

1. **Client lifecycle — build per `call()`, try-with-resources.** `AnthropicClient` is `AutoCloseable`
   (`close()`) and wraps an OkHttp dispatcher + connection pool that must be released
   deterministically. The `ChatModelApi` SPI has **no** `close()` hook, and one `ChatModelApi` instance
   is reused across the handler's `do/while(continued)` continuation loop within a job. Building the
   client inside each `call()` in try-with-resources (mirroring the bridge's `try (chatModel = factory.create(...))`)
   guarantees release and avoids cross-thread sharing questions. The immutable, cheap
   `ModelCapabilities` IS cached on the `ChatModelApi` (resolved once at `create()`).
2. **`max_tokens` default source.** The Messages API requires `max_tokens`. Rule, highest priority first:
   configured `parameters.maxTokens()` → resolved `capabilities.maxOutputTokens()` (models.dev-backed) →
   fixed fallback `DEFAULT_MAX_TOKENS = 4096`. The fallback only fires for an unknown model whose family
   default also left it null (the `anthropic-messages` family default is 8192, so in practice the
   capability value always applies for known claude ids).
3. **Native factory Spring gating — always-on `@Bean`, dedicated `@Configuration`.** The native factory
   is NOT tied to the `camunda.connector.agenticai.framework=langchain4j` toggle (it coexists with the
   bridge and wins only via `getOrder()` + `supports()` for v2 Anthropic-direct configs). A dedicated
   `AgenticAiAnthropicFrameworkConfiguration` is added to the autoconfiguration's `@Import` list. An
   opt-out kill-switch `@ConditionalOnProperty("camunda.connector.agenticai.aiagent.framework.anthropic.native.enabled", matchIfMissing=true)`
   lets an operator fall back to the bridge. `getOrder()` = `100` (< bridge's 1000).
4. **Structured output → Anthropic wire.** When `ResponseConfiguration.format()` is a
   `JsonResponseFormatConfiguration` with a non-null `schema`, set
   `MessageCreateParams.outputConfig(OutputConfig.builder().format(JsonOutputFormat.builder().schema(
   JsonOutputFormat.Schema.builder().additionalProperties(<schema as JsonValue map>).build()).build()).build())`.
   This serialises to `output_config.format` (type `json_schema` + `schema`). The **schema name is NOT
   sent** (the Anthropic wire format carries only type+schema; the existing e2e adapter forces name null).
   A `TextResponseFormatConfiguration` (incl. `parseJson`) has **no** request-side effect (mirror the
   bridge: never force a TEXT format), so it is ignored by the request converter.
5. **apiFamily key = `anthropic-messages`.** The claude capability buckets already exist in
   `model-capabilities.yaml` under `anthropic-messages` (context window / max-output-tokens / reasoning
   flags sourced from models.dev per the file header). **No YAML edit is needed in C7.** The native
   path passes the literal `"anthropic-messages"` to `ModelCapabilitiesResolver.resolve(...)`. The family
   defaults declare `tool-result: [text, image, document]`, which the native honours natively (Anthropic
   `tool_result` blocks accept `ofText`/`ofImage`/`ofDocument` — verified against the SDK), so
   `toolResultModalities` is not over-promised.

## Context-file corrections (verified against real code / the SDK jar)

- **D1 / transport — the ratified "okhttp3.OkHttpClient.Builder … Anthropic client built from that via
  `.httpClient(...)`" is NOT how anthropic-java works.** `com.anthropic.client.okhttp.AnthropicOkHttpClient.Builder`
  exposes **no** `okhttp3` type and no `httpClient(...)` method — it builds OkHttp internally and accepts
  `.proxy(java.net.Proxy)` + `.proxyAuthenticator(com.anthropic.core.http.ProxyAuthenticator)` (+
  `.sslSocketFactory`, `.timeout(Timeout|Duration)`, `.baseUrl(String)`, `.apiKey(String)`,
  `.maxRetries(int)`, headers). The provider-neutral thing `HttpTransportSupport` can share with
  anthropic-java AND openai-java (both OkHttp/Stainless SDKs with the same `.proxy(java.net.Proxy)`
  surface) is a **`java.net.Proxy` + credentials**, not an okhttp client. The plan implements D1 as an
  `okHttpProxy(scheme)` method returning a neutral `OkHttpProxy(Proxy, user, password)` record.
- **`ChatModelResult` accessor names** are `assistantMessage()` / `metrics()` (records `Completed` /
  `Continuation`), matching the context. Confirmed.
- **`Usage` has no direct reasoning-token field**; reasoning tokens come from
  `usage.outputTokensDetails().map(OutputTokensDetails::thinkingTokens)` (a `long`). `cacheReadInputTokens()`
  / `cacheCreationInputTokens()` are `Optional<Long>`.
- **Anthropic `StopReason`** enum values are `END_TURN, MAX_TOKENS, STOP_SEQUENCE, TOOL_USE, PAUSE_TURN,
  REFUSAL` (not the domain names). `Message.stopReason()` is `Optional<StopReason>`.
- **Tool-call input** on inbound blocks is `ToolUseBlock._input()` returning `com.anthropic.core.JsonValue`
  (no typed `input()`); convert with `_input().convert(new TypeReference<Map<String,Object>>(){})`.
- **Streaming assembly**: `client.messages().createStreaming(params)` returns
  `StreamResponse<RawMessageStreamEvent>` (`AutoCloseable`, `.stream(): Stream<T>`, `.close()`);
  `com.anthropic.helpers.MessageAccumulator.create()` → `.accumulate(event)` per event → `.message(): Message`.
- **v2 element-template ids**: the provider discriminator property id is `configuration.type`
  (value `"anthropic"`), NOT a bare `provider`. The `direct.endpoint` property id is
  `configuration.anthropic.backend.direct.endpoint` (binding name `configuration.anthropic.backend.endpoint`);
  `.property(...)` in the e2e DSL keys off the **id**. Other ids match the context.

### confirm-at-impl notes (implementers have the jar)

- `Message.model()` → `com.anthropic.models.messages.Model`; use `.asString()` for the model id String
  (confirm the accessor name; `asString()` is the Stainless convention).
- `DocumentBlockParam` non-PDF handling: `.base64Source(String)` builds a `Base64PdfSource` (PDF only).
  For a `text/*` document use `.textSource(String)` (PlainTextSource) with the decoded string. Confirm
  which `DocumentBlockParam.Builder` overload accepts a media type for non-PDF binary documents; if none,
  such documents fall through to the `ObjectContent`/JSON-reference text path.
- `Base64ImageSource.MediaType.of(String)` accepts an arbitrary mime; prefer the constants
  (`IMAGE_JPEG/PNG/GIF/WEBP`) when they match.
- `JsonValue.from(Object)` builds a `JsonValue` from a plain Java value (used to feed the tool-input map
  and the JSON schema map into the SDK builders). Confirm it accepts nested `Map`/`List`.

## File Structure map

```
connectors/agentic-ai/connector-agentic-ai/
  pom.xml                                                              (MODIFY: anthropic-java dep)
  src/main/java/io/camunda/connector/agenticai/aiagent/framework/
    transport/HttpTransportSupport.java                               (MODIFY: okHttpProxy + OkHttpProxy record)
    anthropic/                                                         (NEW package)
      package-info.java                                               (NEW: @NullMarked)
      AnthropicMessageRequestConverter.java                           (NEW: snapshot+config -> MessageCreateParams)
      AnthropicContentConverter.java                                  (NEW: domain Content -> Anthropic blocks)
      AnthropicMessageResponseConverter.java                          (NEW: Message -> AssistantMessage + result)
      AnthropicClientFactory.java                                     (NEW: interface, AnthropicClient create())
      AnthropicOkHttpClientFactory.java                               (NEW: builds client from config + transport)
      AnthropicChatModelApi.java                                      (NEW: streaming assembly + capabilities)
      AnthropicChatModelApiFactory.java                               (NEW: supports/create/getOrder)
      configuration/AgenticAiAnthropicFrameworkConfiguration.java     (NEW: @Configuration + @Bean)
    autoconfigure/AgenticAiConnectorsAutoConfiguration.java           (MODIFY: add config to @Import)
  src/test/java/io/camunda/connector/agenticai/aiagent/framework/
    transport/HttpTransportSupportTest.java                          (MODIFY: okHttpProxy tests)
    anthropic/
      AnthropicMessageRequestConverterTest.java                      (NEW)
      AnthropicContentConverterTest.java                             (NEW)
      AnthropicMessageResponseConverterTest.java                     (NEW)
      AnthropicChatModelApiTest.java                                 (NEW)
      AnthropicChatModelApiFactoryTest.java                          (NEW)
    LlmProviderChatModelApiConfigurationRegistryTest.java            (MODIFY: direct->native, bedrock->fail-loud)
connectors-e2e-test/connectors-e2e-test-agentic-ai/
  src/test/java/io/camunda/connector/e2e/agenticai/aiagent/wiremock/
    ProviderWireFormatSmokeTests.java                                (MODIFY: register native fixture)
    anthropic/NativeAnthropicMessagesWireFormatFixture.java          (NEW: v2-template fixture)
```

---

## Task 1 — Dependency + provider-neutral OkHttp proxy transport (D1)

**Files**
- Modify: `connectors/agentic-ai/connector-agentic-ai/pom.xml`
- Modify: `.../aiagent/framework/transport/HttpTransportSupport.java`
- Test (modify): `.../aiagent/framework/transport/HttpTransportSupportTest.java`

**Interfaces**
- Consumes: `io.camunda.connector.http.client.proxy.ProxyConfiguration#getProxyDetails(String): Optional<ProxyDetails>`
  where `ProxyDetails` exposes `scheme()/host()/port()/hasCredentials()/user()/password()`.
- Produces: `HttpTransportSupport#okHttpProxy(String scheme): Optional<HttpTransportSupport.OkHttpProxy>`
  and nested `record OkHttpProxy(java.net.Proxy proxy, @Nullable String username, @Nullable String password) { boolean hasCredentials(); }`.

**Steps**

1. Write failing test in `HttpTransportSupportTest` (a new `@Nested class OkHttpProxyTests`):
   ```java
   @Nested
   class OkHttpProxyTests {
     @Test
     void returnsProxyWithCredentialsForConfiguredScheme() {
       final var details =
           new ProxyDetails(SCHEME_HTTP, PROXY_HOST, PROXY_PORT, PROXY_USER, PROXY_PASSWORD);
       when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.of(details));

       final var result = transport.okHttpProxy(SCHEME_HTTPS);

       assertThat(result).isPresent();
       final var okHttpProxy = result.get();
       assertThat(okHttpProxy.proxy().type()).isEqualTo(java.net.Proxy.Type.HTTP);
       assertThat(okHttpProxy.proxy().address())
           .isEqualTo(new java.net.InetSocketAddress(PROXY_HOST, PROXY_PORT));
       assertThat(okHttpProxy.hasCredentials()).isTrue();
       assertThat(okHttpProxy.username()).isEqualTo(PROXY_USER);
       assertThat(okHttpProxy.password()).isEqualTo(PROXY_PASSWORD);
     }

     @Test
     void returnsEmptyWhenNoProxyConfiguredForScheme() {
       when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.empty());
       assertThat(transport.okHttpProxy(SCHEME_HTTPS)).isEmpty();
     }
   }
   ```
   (Confirm the `ProxyDetails` constructor arity at impl; the existing test already builds `ProxyDetails`
   fixtures — reuse its construction style. If the ctor differs, mock the accessors instead.)
2. Run `mvn test -pl connector-agentic-ai -f connectors/agentic-ai/pom.xml -Dtest=HttpTransportSupportTest`
   (outside sandbox) → **fails to compile** (no `okHttpProxy`).
3. Add the pom dependency. In `pom.xml` `<properties>` add after `version.record-builder`:
   ```xml
   <version.anthropic-java>2.48.0</version.anthropic-java>
   ```
   and in `<dependencies>` (near the other model SDKs) add:
   ```xml
   <dependency>
     <groupId>com.anthropic</groupId>
     <artifactId>anthropic-java</artifactId>
     <version>${version.anthropic-java}</version>
   </dependency>
   ```
   (No explicit okhttp declaration — it arrives transitively via `anthropic-java-client-okhttp`.)
4. Implement `okHttpProxy` in `HttpTransportSupport` (add imports `java.net.InetSocketAddress`,
   `java.net.Proxy`):
   ```java
   /**
    * Provider-neutral proxy resolution for OkHttp-based vendor SDKs (anthropic-java, openai-java),
    * which accept a {@link Proxy} rather than a pre-built HTTP client. Returns the proxy configured
    * for the target scheme, if any, together with any credentials for the SDK's own proxy
    * authenticator. Shared design so C8's OpenAI native reuses it unchanged.
    */
   public Optional<OkHttpProxy> okHttpProxy(String scheme) {
     return proxyConfiguration
         .getProxyDetails(scheme)
         .map(
             proxyDetails -> {
               LOG.debug(
                   "Using proxy for target scheme [{}] => [{}:{}]",
                   scheme,
                   proxyDetails.host(),
                   proxyDetails.port());
               final var proxy =
                   new Proxy(
                       Proxy.Type.HTTP,
                       new InetSocketAddress(proxyDetails.host(), proxyDetails.port()));
               return proxyDetails.hasCredentials()
                   ? new OkHttpProxy(proxy, proxyDetails.user(), proxyDetails.password())
                   : new OkHttpProxy(proxy, null, null);
             });
   }

   /** Proxy plus optional credentials in a form neutral to any OkHttp-based SDK. */
   public record OkHttpProxy(
       Proxy proxy, @Nullable String username, @Nullable String password) {
     public boolean hasCredentials() {
       return username != null && !username.isBlank();
     }
   }
   ```
5. Run the test → **passes**. Run `mvn -pl connector-agentic-ai -f connectors/agentic-ai/pom.xml
   -DskipTests compile` to confirm the new dependency resolves on the classpath.
6. Commit: `Add anthropic-java dependency and provider-neutral OkHttp proxy resolution`.

---

## Task 2 — Anthropic content converter (domain Content → Anthropic content blocks)

**Files**
- Create: `.../framework/anthropic/package-info.java` (`@NullMarked` — mirror an existing package-info).
- Create: `.../framework/anthropic/AnthropicContentConverter.java`
- Test: `.../framework/anthropic/AnthropicContentConverterTest.java`

**Interfaces**
- Consumes: domain `Content` (`TextContent(text)`, `DocumentContent(Document document)` with
  `Document#asBase64()` + `Document#metadata().getContentType()`, `ObjectContent(Object content)`,
  `ReasoningContent`), `io.camunda.connector.agenticai.aiagent.provider.multimodal.DocumentModality#fromDocument(Document): Modality`,
  `com.fasterxml.jackson.databind.ObjectMapper`.
- Produces:
  - `List<ContentBlockParam> toContentBlockParams(List<Content> content)` (for user/assistant message bodies)
  - `List<ToolResultBlockParam.Content.Block> toToolResultBlocks(List<Content> content)` (for tool_result bodies)

**Steps**

1. Write failing test `AnthropicContentConverterTest` covering: (a) `TextContent` → `ContentBlockParam.isText()`
   with the text; (b) image `DocumentContent` (contentType `image/png`) → `isImage()` with base64 data +
   `IMAGE_PNG`; (c) PDF `DocumentContent` (`application/pdf`) → `isDocument()`; (d) `ObjectContent` →
   text block containing the JSON serialisation; (e) tool-result variants via `toToolResultBlocks`
   returning `ofText`/`ofImage` blocks. Use `mock(Document.class)` with `asBase64()`/`metadata()` stubbed.
   Example assertion:
   ```java
   @Test
   void mapsImageDocumentToBase64ImageBlock() {
     final var doc = mock(Document.class);
     final var metadata = mock(DocumentMetadata.class);
     when(doc.metadata()).thenReturn(metadata);
     when(metadata.getContentType()).thenReturn("image/png");
     when(doc.asBase64()).thenReturn("QUJD");

     final var blocks = converter.toContentBlockParams(List.of(new DocumentContent(doc, null)));

     assertThat(blocks).hasSize(1);
     final var image = blocks.get(0).image().orElseThrow();
     assertThat(image.source().base64().orElseThrow().data()).isEqualTo("QUJD");
   }
   ```
   (Confirm `ImageBlockParam.source()` accessor path at impl; if awkward, assert via the serialised
   JSON with the SDK `JsonMapper` instead.)
2. Run `-Dtest=AnthropicContentConverterTest` → **fails to compile**.
3. Implement `AnthropicContentConverter`:
   ```java
   public class AnthropicContentConverter {
     private final ObjectMapper objectMapper;

     public AnthropicContentConverter(ObjectMapper objectMapper) {
       this.objectMapper = objectMapper;
     }

     public List<ContentBlockParam> toContentBlockParams(List<Content> content) {
       final List<ContentBlockParam> blocks = new ArrayList<>();
       for (final Content c : content) {
         switch (c) {
           case TextContent text ->
               blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(text.text()).build()));
           case DocumentContent doc -> blocks.add(documentBlock(doc));
           case ObjectContent obj ->
               blocks.add(
                   ContentBlockParam.ofText(
                       TextBlockParam.builder().text(writeAsJson(obj.content())).build()));
           // Reasoning content is NOT re-emitted on the request side in C7 (signature
           // round-trip is deferred); skip it so history replay stays valid.
           case ReasoningContent ignored -> {}
           default ->
               blocks.add(
                   ContentBlockParam.ofText(
                       TextBlockParam.builder().text(writeAsJson(c)).build()));
         }
       }
       return blocks;
     }

     public List<ToolResultBlockParam.Content.Block> toToolResultBlocks(List<Content> content) {
       final List<ToolResultBlockParam.Content.Block> blocks = new ArrayList<>();
       for (final Content c : content) {
         switch (c) {
           case TextContent text ->
               blocks.add(
                   ToolResultBlockParam.Content.Block.ofText(
                       TextBlockParam.builder().text(text.text()).build()));
           case DocumentContent doc -> {
             final ContentBlockParam block = documentBlock(doc);
             block.image().ifPresent(i -> blocks.add(ToolResultBlockParam.Content.Block.ofImage(i)));
             block.document().ifPresent(d -> blocks.add(ToolResultBlockParam.Content.Block.ofDocument(d)));
             block.text().ifPresent(t -> blocks.add(ToolResultBlockParam.Content.Block.ofText(t)));
           }
           case ObjectContent obj ->
               blocks.add(
                   ToolResultBlockParam.Content.Block.ofText(
                       TextBlockParam.builder().text(writeAsJson(obj.content())).build()));
           default ->
               blocks.add(
                   ToolResultBlockParam.Content.Block.ofText(
                       TextBlockParam.builder().text(writeAsJson(c)).build()));
         }
       }
       return blocks;
     }

     private ContentBlockParam documentBlock(DocumentContent doc) {
       final var modality = DocumentModality.fromDocument(doc.document());
       final var contentType = contentType(doc.document());
       return switch (modality) {
         case IMAGE ->
             ContentBlockParam.ofImage(
                 ImageBlockParam.builder()
                     .source(
                         Base64ImageSource.builder()
                             .data(doc.document().asBase64())
                             .mediaType(Base64ImageSource.MediaType.of(contentType))
                             .build())
                     .build());
         case DOCUMENT ->
             ContentBlockParam.ofDocument(
                 DocumentBlockParam.builder().base64Source(doc.document().asBase64()).build());
         // TEXT-family documents inline as plain text; AUDIO/VIDEO have no direct
         // Anthropic block yet, so fall back to a JSON reference like the bridge.
         case TEXT ->
             ContentBlockParam.ofDocument(
                 DocumentBlockParam.builder().textSource(decodeUtf8(doc.document())).build());
         default ->
             ContentBlockParam.ofText(TextBlockParam.builder().text(writeAsJson(doc)).build());
       };
     }

     private static String contentType(Document document) {
       final var metadata = document.metadata();
       final var type = metadata != null ? metadata.getContentType() : null;
       return type != null ? type : "application/octet-stream";
     }

     private static String decodeUtf8(Document document) {
       return new String(document.asByteArray(), java.nio.charset.StandardCharsets.UTF_8);
     }

     private String writeAsJson(Object value) {
       try {
         return objectMapper.writeValueAsString(value);
       } catch (JsonProcessingException e) {
         throw new IllegalStateException("Failed to serialize content to JSON", e);
       }
     }
   }
   ```
   confirm-at-impl: `ContentBlockParam#image()/document()/text()` accessor names, `DocumentBlockParam.Builder#textSource`,
   `Base64ImageSource.MediaType.of`. If `documentBlock` producing a `ContentBlockParam` is awkward to
   destructure for the tool-result path, add a private enum-returning helper instead and build both
   `ContentBlockParam` and `ToolResultBlockParam.Content.Block` from the classified modality directly.
4. Run the test → **passes**.
5. Commit: `Add Anthropic content converter for native multimodal request blocks`.

---

## Task 3 — Anthropic request converter (snapshot + config → MessageCreateParams)

**Files**
- Create: `.../framework/anthropic/AnthropicMessageRequestConverter.java`
- Test: `.../framework/anthropic/AnthropicMessageRequestConverterTest.java`

**Interfaces**
- Consumes: `ConversationSnapshot#messages(): List<Message>` / `#toolDefinitions(): List<ToolDefinition>`;
  `AgentExecutionContext#configuration(): AgentConfiguration`; the C6 accessor chain for model id/params
  (`AnthropicChatModel` → `AnthropicConnection` → `AnthropicModel#model()/parameters()`);
  `ResponseConfiguration#format()`; `ModelCapabilities#maxOutputTokens()`; `AnthropicContentConverter`.
  Domain `Message` subtypes: `SystemMessage`, `UserMessage`, `AssistantMessage`, `ToolCallResultMessage`.
- Produces: `MessageCreateParams toMessageCreateParams(AgentExecutionContext ctx, ConversationSnapshot snapshot, ModelCapabilities capabilities)`.

**Steps**

1. Write failing test `AnthropicMessageRequestConverterTest`:
   - `mapsSystemPromptToTopLevelSystemAndRemainingToMessages`: snapshot `[SystemMessage("sys"), UserMessage("hi")]`
     → `params.system()` present == "sys", `params.messages()` size 1 role USER.
   - `mapsToolDefinitionsToTools`: `ToolDefinition("SuperfluxProduct","desc", schemaMap)` → `params.tools()`
     size 1 with name/description/input_schema.
   - `mapsAssistantToolCallsAndToolResults`: assistant with `ToolCall("id","name",{"a":5})` then a
     `ToolCallResultMessage` → assistant message carries a `tool_use` block, following user message carries
     a `tool_result` block with `toolUseId=="id"`.
   - `defaultsMaxTokensFromCapabilitiesWhenConfigNull`: params null → `maxTokens == capabilities.maxOutputTokens()`.
   - `usesConfiguredMaxTokensAndModelParams`: params set → maxTokens/temperature/topP/topK propagate.
   - `configuresStructuredOutputFromJsonSchema`: response format json+schema → `params.outputConfig()` present.
   - `omitsMaxTokensFallbackConstantWhenBothNull`: capabilities.maxOutputTokens null + config null →
     `maxTokens == 4096`.
   Assert via the SDK `JsonMapper`-serialised body where accessors are awkward (mirror the e2e recorded
   parser: read `system`, `messages`, `tools[].input_schema`, `output_config.format`).
2. Run `-Dtest=AnthropicMessageRequestConverterTest` → **fails to compile**.
3. Implement:
   ```java
   public class AnthropicMessageRequestConverter {
     static final long DEFAULT_MAX_TOKENS = 4096L;

     private final AnthropicContentConverter contentConverter;
     private final ObjectMapper objectMapper;

     public AnthropicMessageRequestConverter(
         AnthropicContentConverter contentConverter, ObjectMapper objectMapper) {
       this.contentConverter = contentConverter;
       this.objectMapper = objectMapper;
     }

     public MessageCreateParams toMessageCreateParams(
         AgentExecutionContext ctx, ConversationSnapshot snapshot, ModelCapabilities capabilities) {
       final var cfg =
           (LlmProviderChatModelApiConfiguration) ctx.configuration().chatModelApiConfiguration();
       final var model = (AnthropicChatModel) cfg.configuration();
       final var params = model.anthropic().model().parameters();

       final var builder =
           MessageCreateParams.builder()
               .model(model.anthropic().model().model())
               .maxTokens(resolveMaxTokens(params, capabilities));

       applyModelParameters(builder, params);
       applySystemPrompt(builder, snapshot.messages());
       applyMessages(builder, snapshot.messages());
       applyTools(builder, snapshot.toolDefinitions());
       applyResponseFormat(builder, ctx.configuration().response());

       return builder.build();
     }

     private long resolveMaxTokens(
         @Nullable AnthropicModelParameters params, ModelCapabilities capabilities) {
       if (params != null && params.maxTokens() != null) {
         return params.maxTokens().longValue();
       }
       if (capabilities.maxOutputTokens() != null) {
         return capabilities.maxOutputTokens().longValue();
       }
       return DEFAULT_MAX_TOKENS;
     }

     private void applyModelParameters(
         MessageCreateParams.Builder builder, @Nullable AnthropicModelParameters params) {
       if (params == null) {
         return;
       }
       if (params.temperature() != null) builder.temperature(params.temperature());
       if (params.topP() != null) builder.topP(params.topP());
       if (params.topK() != null) builder.topK(params.topK().longValue());
     }

     private void applySystemPrompt(MessageCreateParams.Builder builder, List<Message> messages) {
       final String system =
           messages.stream()
               .filter(SystemMessage.class::isInstance)
               .map(SystemMessage.class::cast)
               .flatMap(m -> m.content().stream())
               .filter(TextContent.class::isInstance)
               .map(c -> ((TextContent) c).text())
               .collect(Collectors.joining("\n"));
       if (!system.isBlank()) {
         builder.system(system);
       }
     }

     private void applyMessages(MessageCreateParams.Builder builder, List<Message> messages) {
       for (final Message message : messages) {
         switch (message) {
           case SystemMessage ignored -> {} // hoisted to top-level system
           case UserMessage user ->
               builder.addMessage(
                   MessageParam.builder()
                       .role(MessageParam.Role.USER)
                       .contentOfBlockParams(contentConverter.toContentBlockParams(user.content()))
                       .build());
           case AssistantMessage assistant -> builder.addMessage(assistantParam(assistant));
           case ToolCallResultMessage toolResults ->
               builder.addMessage(toolResultParam(toolResults));
           default -> throw new IllegalArgumentException(
               "Unsupported message type: " + message.getClass().getSimpleName());
         }
       }
     }

     private MessageParam assistantParam(AssistantMessage assistant) {
       final List<ContentBlockParam> blocks =
           new ArrayList<>(contentConverter.toContentBlockParams(assistant.content()));
       for (final ToolCall toolCall : assistant.toolCalls()) {
         blocks.add(
             ContentBlockParam.ofToolUse(
                 ToolUseBlockParam.builder()
                     .id(toolCall.id())
                     .name(toolCall.name())
                     .input(toInput(toolCall.arguments()))
                     .build()));
       }
       return MessageParam.builder()
           .role(MessageParam.Role.ASSISTANT)
           .contentOfBlockParams(blocks)
           .build();
     }

     private MessageParam toolResultParam(ToolCallResultMessage message) {
       final List<ContentBlockParam> blocks = new ArrayList<>();
       for (final ToolCallResultContent result : message.results()) {
         blocks.add(
             ContentBlockParam.ofToolResult(
                 ToolResultBlockParam.builder()
                     .toolUseId(result.id())
                     .contentOfBlocks(contentConverter.toToolResultBlocks(result.content()))
                     .build()));
       }
       return MessageParam.builder()
           .role(MessageParam.Role.USER)
           .contentOfBlockParams(blocks)
           .build();
     }

     private void applyTools(MessageCreateParams.Builder builder, List<ToolDefinition> toolDefinitions) {
       for (final ToolDefinition definition : toolDefinitions) {
         final var toolBuilder =
             Tool.builder().name(definition.name()).inputSchema(toInputSchema(definition.inputSchema()));
         if (definition.description() != null) {
           toolBuilder.description(definition.description());
         }
         builder.addTool(toolBuilder.build());
       }
     }

     private Tool.InputSchema toInputSchema(Map<String, Object> schema) {
       // input_schema is a JSON-schema object; feed properties/required through additionalProperties
       // so the whole schema serialises verbatim (the SDK enforces type=object).
       final var inputSchemaBuilder = Tool.InputSchema.builder();
       final Map<String, JsonValue> additional = new LinkedHashMap<>();
       schema.forEach((k, v) -> {
         if (!"type".equals(k)) {
           additional.put(k, JsonValue.from(v));
         }
       });
       return inputSchemaBuilder.additionalProperties(additional).build();
     }

     private void applyResponseFormat(
         MessageCreateParams.Builder builder, @Nullable ResponseConfiguration response) {
       if (response == null || !(response.format() instanceof JsonResponseFormatConfiguration json)
           || json.schema() == null) {
         return; // TEXT / parseJson has no request-side effect (mirror the bridge)
       }
       final Map<String, JsonValue> schema = new LinkedHashMap<>();
       json.schema().forEach((k, v) -> schema.put(k, JsonValue.from(v)));
       builder.outputConfig(
           OutputConfig.builder()
               .format(
                   JsonOutputFormat.builder()
                       .schema(JsonOutputFormat.Schema.builder().additionalProperties(schema).build())
                       .build())
               .build());
     }

     private ToolUseBlockParam.Input toInput(Map<String, Object> arguments) {
       final Map<String, JsonValue> converted = new LinkedHashMap<>();
       arguments.forEach((k, v) -> converted.put(k, JsonValue.from(v)));
       return ToolUseBlockParam.Input.builder().putAllAdditionalProperties(converted).build();
     }
   }
   ```
   confirm-at-impl:
   `MessageParam.Role.USER/ASSISTANT` names; `Tool.InputSchema.Builder#properties/required` vs
   `additionalProperties` (prefer feeding the whole schema through `additionalProperties`, minus `type`,
   so a schema with `$defs`/`additionalProperties` round-trips); `JsonValue.from` accepting nested maps.
4. Run the test → **passes**.
5. Commit: `Map conversation snapshot and model config to Anthropic MessageCreateParams`.

---

## Task 4 — Anthropic response converter (accumulated Message → AssistantMessage + result)

**Files**
- Create: `.../framework/anthropic/AnthropicMessageResponseConverter.java`
- Test: `.../framework/anthropic/AnthropicMessageResponseConverterTest.java`

**Interfaces**
- Consumes: `com.anthropic.models.messages.Message` (`content()/id()/model()/stopReason()/usage()`),
  `ContentBlock` (`isText()/text()`, `isToolUse()/toolUse()`, `isThinking()/thinking()`,
  `isRedactedThinking()/redactedThinking()`), `Usage`, `com.fasterxml.jackson.databind.ObjectMapper`.
- Produces: `ChatModelResult toResult(Message message, Duration executionTime)`; internal
  `AssistantMessage toAssistantMessage(Message)` and `AgentMetrics toMetrics(Message, int toolCalls, Duration)`.

**Steps**

1. Write failing test `AnthropicMessageResponseConverterTest`:
   - `mapsTextAndToolUseAndStopReason`: build a `Message` (via SDK builders) with a text block + a
     `tool_use` block + `stopReason=TOOL_USE`, usage input=10/output=20 → `Completed`, assistantMessage has
     `TextContent` + one `ToolCall(id,name,args)`, `stopReason()==TOOL_USE`, metrics
     `modelCalls==1, toolCalls==1, tokenUsage.inputTokenCount==10/outputTokenCount==20`.
   - `mapsPauseTurnToContinuation`: `stopReason=PAUSE_TURN` → `Continuation`.
   - `mapsThinkingToReadOnlyReasoningContent`: a `thinking` block → assistantMessage content contains a
     `ReasoningContent` with the thinking text (and signature stored in `providerPayload`).
   - `populatesCacheAndReasoningTokenSubsets`: usage with cacheRead=3/cacheCreation=4 and
     outputTokensDetails.thinkingTokens=5 → metrics `cacheReadTokenCount==3/cacheCreationTokenCount==4/reasoningTokenCount==5`.
   - `mapsEndTurnToStopAndMaxTokensToLength`.
2. Run `-Dtest=AnthropicMessageResponseConverterTest` → **fails to compile**.
3. Implement:
   ```java
   public class AnthropicMessageResponseConverter {
     private final ObjectMapper objectMapper;

     public AnthropicMessageResponseConverter(ObjectMapper objectMapper) {
       this.objectMapper = objectMapper;
     }

     public ChatModelResult toResult(Message message, Duration executionTime) {
       final AssistantMessage assistantMessage = toAssistantMessage(message);
       final AgentMetrics metrics =
           toMetrics(message, assistantMessage.toolCalls().size(), executionTime);
       final boolean paused =
           message.stopReason().map(sr -> sr.equals(StopReason.PAUSE_TURN)).orElse(false);
       return paused
           ? new ChatModelResult.Continuation(assistantMessage, metrics)
           : new ChatModelResult.Completed(assistantMessage, metrics);
     }

     AssistantMessage toAssistantMessage(Message message) {
       final List<Content> content = new ArrayList<>();
       final List<ToolCall> toolCalls = new ArrayList<>();
       for (final ContentBlock block : message.content()) {
         if (block.isText()) {
           content.add(TextContent.textContent(block.text().orElseThrow().text()));
         } else if (block.isToolUse()) {
           final var toolUse = block.toolUse().orElseThrow();
           toolCalls.add(
               new ToolCall(
                   toolUse.id(),
                   toolUse.name(),
                   toolUse._input().convert(new TypeReference<Map<String, Object>>() {})));
         } else if (block.isThinking()) {
           final var thinking = block.thinking().orElseThrow();
           content.add(new ReasoningContent(thinking.thinking(), thinking.signature(), null));
         } else if (block.isRedactedThinking()) {
           content.add(
               new ReasoningContent(null, block.redactedThinking().orElseThrow().data(), null));
         }
       }

       final var builder =
           AssistantMessage.builder()
               .content(content)
               .toolCalls(toolCalls)
               .messageId(message.id())
               .modelId(message.model().asString())
               .stopReason(mapStopReason(message.stopReason().orElse(null)));
       message
           .stopReason()
           .ifPresent(sr -> builder.metadata(Map.of("stopReason", sr.asString())));
       return builder.build();
     }

     private AgentMetrics toMetrics(Message message, int toolCalls, Duration executionTime) {
       final Usage usage = message.usage();
       final var tokenUsage =
           AgentMetrics.TokenUsage.builder()
               .inputTokenCount((int) usage.inputTokens())
               .outputTokenCount((int) usage.outputTokens())
               .cacheReadTokenCount(usage.cacheReadInputTokens().map(Long::intValue).orElse(0))
               .cacheCreationTokenCount(usage.cacheCreationInputTokens().map(Long::intValue).orElse(0))
               .reasoningTokenCount(
                   usage.outputTokensDetails().map(d -> (int) d.thinkingTokens()).orElse(0))
               .build();
       return AgentMetrics.builder()
           .modelCalls(1)
           .toolCalls(toolCalls)
           .tokenUsage(tokenUsage)
           .executionTime(executionTime)
           .build();
     }

     private @Nullable io.camunda.connector.agenticai.aiagent.model.message.StopReason mapStopReason(
         @Nullable StopReason stopReason) {
       if (stopReason == null) {
         return null;
       }
       final var known = stopReason.value();
       return switch (stopReason.known()) {
         case END_TURN, STOP_SEQUENCE -> DomainStopReason.STOP;
         case MAX_TOKENS -> DomainStopReason.LENGTH;
         case TOOL_USE -> DomainStopReason.TOOL_USE;
         case REFUSAL -> DomainStopReason.CONTENT_FILTERED;
         case PAUSE_TURN -> null; // surfaced as Continuation, not a stop reason
         default -> DomainStopReason.UNKNOWN;
       };
     }
   }
   ```
   confirm-at-impl: `StopReason#known()` enum switch (guard the `_UNKNOWN`/unrecognised case with the
   `default` arm — Stainless enums expose a `known()` that throws only on genuinely unknown; if it throws
   instead of returning a sentinel, wrap in try/catch → `UNKNOWN`); `RedactedThinkingBlock#data()` name;
   `Model#asString()`; `AgentMetrics.TokenUsage.Builder` setter names (match the bridge's `.inputTokenCount`
   / `.outputTokenCount`). Alias the domain `StopReason` as `DomainStopReason` to avoid clashing with the
   SDK `StopReason` import.
4. Run the test → **passes**.
5. Commit: `Map accumulated Anthropic Message to AssistantMessage, metrics and result`.

---

## Task 5 — AnthropicClientFactory + AnthropicChatModelApi (streaming assembly, lifecycle, capabilities)

**Files**
- Create: `.../framework/anthropic/AnthropicClientFactory.java` (interface)
- Create: `.../framework/anthropic/AnthropicOkHttpClientFactory.java`
- Create: `.../framework/anthropic/AnthropicChatModelApi.java`
- Test: `.../framework/anthropic/AnthropicChatModelApiTest.java`

**Interfaces**
- Consumes: `AnthropicClientFactory#create(): AnthropicClient`;
  `AnthropicClient#messages().createStreaming(MessageCreateParams): StreamResponse<RawMessageStreamEvent>`;
  `MessageAccumulator.create()/.accumulate(event)/.message()`; the two converters; `ModelCapabilities`;
  `AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL`.
- Produces: `ChatModelApi` (`ChatModelResult call(ChatModelRequest)`, `ModelCapabilities capabilities()`).

**Steps — 5a AnthropicClientFactory + AnthropicOkHttpClientFactory**

1. Write failing test in `AnthropicChatModelApiTest` for the factory wiring is deferred to Task 6; here
   define `AnthropicClientFactory`:
   ```java
   public interface AnthropicClientFactory {
     /** Builds a fresh, closeable {@link AnthropicClient}; the caller owns its lifecycle. */
     AnthropicClient create();
   }
   ```
2. Implement `AnthropicOkHttpClientFactory` (built from the direct backend + timeout + transport proxy):
   ```java
   public class AnthropicOkHttpClientFactory implements AnthropicClientFactory {
     private final String apiKey;
     private final @Nullable String baseUrl;
     private final @Nullable Duration timeout;
     private final Optional<HttpTransportSupport.OkHttpProxy> proxy;

     public AnthropicOkHttpClientFactory(
         AnthropicDirectBackend backend,
         @Nullable Duration timeout,
         HttpTransportSupport transport) {
       this.apiKey = backend.apiKey();
       this.baseUrl = backend.endpoint();
       this.timeout = timeout;
       final String scheme =
           baseUrl != null ? URI.create(baseUrl).getScheme() : ProxyConfiguration.SCHEME_HTTPS;
       this.proxy = transport.okHttpProxy(scheme != null ? scheme : ProxyConfiguration.SCHEME_HTTPS);
     }

     @Override
     public AnthropicClient create() {
       final var builder = AnthropicOkHttpClient.builder().apiKey(apiKey);
       if (baseUrl != null && !baseUrl.isBlank()) {
         builder.baseUrl(baseUrl);
       }
       if (timeout != null) {
         builder.timeout(timeout);
       }
       proxy.ifPresent(
           p -> {
             builder.proxy(p.proxy());
             if (p.hasCredentials()) {
               builder.proxyAuthenticator(ProxyAuthenticator.basic(p.username(), p.password()));
             }
           });
       return builder.build();
     }
   }
   ```
   confirm-at-impl: `AnthropicOkHttpClient.builder()` returns the builder; `.build()` returns
   `AnthropicClient`; `ProxyAuthenticator.basic(user, pw)` (verified present).

**Steps — 5b AnthropicChatModelApi**

3. Write failing test `AnthropicChatModelApiTest`:
   ```java
   @ExtendWith(MockitoExtension.class)
   class AnthropicChatModelApiTest {
     @Mock AnthropicClientFactory clientFactory;
     @Mock AnthropicClient client;
     @Mock MessageService messageService;
     @Mock StreamResponse<RawMessageStreamEvent> streamResponse;
     @Mock AnthropicMessageRequestConverter requestConverter;
     @Mock AnthropicMessageResponseConverter responseConverter;

     @Test
     void drivesStreamingAccumulatesAndDelegatesToConverters() {
       final var capabilities = ModelCapabilities.builder()... .build();
       final var api = new AnthropicChatModelApi(clientFactory, requestConverter, responseConverter, capabilities);

       final var params = mock(MessageCreateParams.class);
       when(requestConverter.toMessageCreateParams(any(), any(), eq(capabilities))).thenReturn(params);
       when(clientFactory.create()).thenReturn(client);
       when(client.messages()).thenReturn(messageService);
       when(messageService.createStreaming(params)).thenReturn(streamResponse);
       when(streamResponse.stream()).thenReturn(Stream.empty()); // accumulator seeded via events at impl
       final var expected = new ChatModelResult.Completed(assistantMessage, metrics);
       when(responseConverter.toResult(any(), any())).thenReturn(expected);

       final var result = api.call(new ChatModelRequest(executionContext, snapshot));

       assertThat(result).isSameAs(expected);
       verify(client).close();
       verify(streamResponse).close();
     }

     @Test
     void wrapsSdkFailureAsConnectorException() {
       ... when(clientFactory.create()).thenThrow(new RuntimeException("boom"));
       assertThatThrownBy(() -> api.call(request))
           .isInstanceOf(ConnectorException.class)
           .extracting(e -> ((ConnectorException) e).getErrorCode())
           .isEqualTo(AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL);
     }

     @Test
     void capabilitiesReturnsInjectedValue() { assertThat(api.capabilities()).isSameAs(capabilities); }
   }
   ```
   (For the happy-path accumulation, either stub `streamResponse.stream()` with a real
   `RawMessageStreamEvent` sequence OR verify the accumulator is driven; simplest is asserting the
   converter is called with the accumulated `Message` — since `MessageAccumulator` is a concrete SDK
   helper, a minimal event stream that yields a valid `Message` is acceptable, otherwise spy the
   accumulation into a seam. Keep it behavior-focused: streaming is driven, resources closed,
   converters wired, errors wrapped.)
4. Run `-Dtest=AnthropicChatModelApiTest` → **fails to compile**.
5. Implement:
   ```java
   public class AnthropicChatModelApi implements ChatModelApi {
     private final AnthropicClientFactory clientFactory;
     private final AnthropicMessageRequestConverter requestConverter;
     private final AnthropicMessageResponseConverter responseConverter;
     private final ModelCapabilities capabilities;

     public AnthropicChatModelApi(
         AnthropicClientFactory clientFactory,
         AnthropicMessageRequestConverter requestConverter,
         AnthropicMessageResponseConverter responseConverter,
         ModelCapabilities capabilities) {
       this.clientFactory = clientFactory;
       this.requestConverter = requestConverter;
       this.responseConverter = responseConverter;
       this.capabilities = capabilities;
     }

     @Override
     public ChatModelResult call(ChatModelRequest request) {
       final long startNanos = System.nanoTime();
       final MessageCreateParams params =
           requestConverter.toMessageCreateParams(
               request.executionContext(), request.snapshot(), capabilities);
       try (final AnthropicClient client = clientFactory.create();
           final StreamResponse<RawMessageStreamEvent> stream =
               client.messages().createStreaming(params)) {
         final MessageAccumulator accumulator = MessageAccumulator.create();
         stream.stream().forEach(accumulator::accumulate);
         final Message message = accumulator.message();
         final Duration executionTime = Duration.ofNanos(System.nanoTime() - startNanos);
         return responseConverter.toResult(message, executionTime);
       } catch (Exception e) {
         final String detail =
             Optional.ofNullable(e.getMessage())
                 .filter(m -> !m.isBlank())
                 .orElseGet(() -> e.getClass().getSimpleName());
         throw new ConnectorException(
             AgentErrorCodes.ERROR_CODE_FAILED_MODEL_CALL,
             "Model call failed: %s".formatted(detail),
             e);
       }
     }

     @Override
     public ModelCapabilities capabilities() {
       return capabilities;
     }
   }
   ```
6. Run the test → **passes**.
7. Commit: `Add native Anthropic ChatModelApi driving the streaming Messages endpoint`.

---

## Task 6 — Factory + Spring wiring + fail-loud registry test update

**Files**
- Create: `.../framework/anthropic/AnthropicChatModelApiFactory.java`
- Create: `.../framework/anthropic/configuration/AgenticAiAnthropicFrameworkConfiguration.java`
- Modify: `.../autoconfigure/AgenticAiConnectorsAutoConfiguration.java` (add to `@Import`)
- Test: `.../framework/anthropic/AnthropicChatModelApiFactoryTest.java`
- Test (modify): `.../framework/LlmProviderChatModelApiConfigurationRegistryTest.java`

**Interfaces**
- Consumes: `HttpTransportSupport`, `ModelCapabilitiesResolver`, `@ConnectorsObjectMapper ObjectMapper`.
- Produces: `ChatModelApiFactory` (`supports`/`create`/`getOrder`).

**Steps**

1. Write failing tests:
   - `AnthropicChatModelApiFactoryTest`:
     - `supportsAnthropicDirectV2Config`: `LlmProviderChatModelApiConfiguration(AnthropicChatModel(direct))` → true.
     - `doesNotSupportBedrockBackend`: same but bedrock backend → false.
     - `doesNotSupportBridgeConfig`: a `ProviderChatModelApiConfiguration` → false.
     - `createResolvesCapabilitiesWithAnthropicMessagesFamily`: `create(...)` → non-null `ChatModelApi`
       whose `capabilities()` equals what the (mocked) resolver returns for
       `resolve("anthropic-messages", "claude-sonnet-4-6", "direct", override)`; verify the resolver was
       called with exactly `"anthropic-messages"` and the model id + backend `"direct"`.
     - `getOrderIsBelowBridge`: `getOrder() == 100` and `< 1000`.
   - Update `LlmProviderChatModelApiConfigurationRegistryTest`:
     - Rename/replace `registryFailsLoudWhenNoFactorySupportsLlmProviderConfiguration` semantics: register
       BOTH the bridge factory and the native `AnthropicChatModelApiFactory`; assert a **direct** Anthropic
       v2 config resolves to `AnthropicChatModelApi` (`assertThat(registry.resolve(directConfig)).isInstanceOf(AnthropicChatModelApi.class)`).
     - Add `bedrockAnthropicV2ConfigStillFailsLoud`: a bedrock `AnthropicChatModel` config → still throws
       `ConnectorException` with `ERROR_CODE_FAILED_MODEL_CALL` / "No chat model registered" (neither the
       native factory nor the bridge supports it).
2. Run the two test classes → **fail to compile**.
3. Implement `AnthropicChatModelApiFactory`:
   ```java
   public class AnthropicChatModelApiFactory implements ChatModelApiFactory {
     static final int ORDER = 100;
     static final String API_FAMILY = "anthropic-messages";

     private final HttpTransportSupport transport;
     private final ModelCapabilitiesResolver capabilitiesResolver;
     private final ObjectMapper objectMapper;

     public AnthropicChatModelApiFactory(
         HttpTransportSupport transport,
         ModelCapabilitiesResolver capabilitiesResolver,
         ObjectMapper objectMapper) {
       this.transport = transport;
       this.capabilitiesResolver = capabilitiesResolver;
       this.objectMapper = objectMapper;
     }

     @Override
     public boolean supports(ChatModelApiConfiguration configuration) {
       return configuration instanceof LlmProviderChatModelApiConfiguration llm
           && llm.configuration() instanceof AnthropicChatModel anthropic
           && anthropic.anthropic().backend() instanceof AnthropicDirectBackend;
     }

     @Override
     public ChatModelApi create(ChatModelApiConfiguration configuration) {
       final var llm = (LlmProviderChatModelApiConfiguration) configuration;
       final var model = (AnthropicChatModel) llm.configuration();
       final var connection = model.anthropic();
       final var direct = (AnthropicDirectBackend) connection.backend();
       final var timeout = connection.timeouts() != null ? connection.timeouts().timeout() : null;

       final ModelCapabilities capabilities =
           capabilitiesResolver.resolve(
               API_FAMILY,
               connection.model().model(),
               direct.type(),
               Optional.ofNullable(model.capabilityOverride()));

       final var clientFactory = new AnthropicOkHttpClientFactory(direct, timeout, transport);
       final var contentConverter = new AnthropicContentConverter(objectMapper);
       final var requestConverter = new AnthropicMessageRequestConverter(contentConverter);
       final var responseConverter = new AnthropicMessageResponseConverter(objectMapper);
       return new AnthropicChatModelApi(
           clientFactory, requestConverter, responseConverter, capabilities);
     }

     @Override
     public int getOrder() {
       return ORDER;
     }
   }
   ```
   Note: `direct.type()` returns `"direct"`, matching the resolver's `backend` dimension (currently no
   backend-specific `anthropic-messages` entry, so it layers to the backend-agnostic buckets — correct).
4. Implement `AgenticAiAnthropicFrameworkConfiguration`:
   ```java
   @Configuration
   @ConditionalOnProperty(
       value = "camunda.connector.agenticai.aiagent.framework.anthropic.native.enabled",
       havingValue = "true",
       matchIfMissing = true)
   public class AgenticAiAnthropicFrameworkConfiguration {

     @Bean
     @ConditionalOnMissingBean
     public AnthropicChatModelApiFactory anthropicChatModelApiFactory(
         HttpTransportSupport httpTransportSupport,
         ModelCapabilitiesResolver modelCapabilitiesResolver,
         @ConnectorsObjectMapper ObjectMapper objectMapper) {
       return new AnthropicChatModelApiFactory(
           httpTransportSupport, modelCapabilitiesResolver, objectMapper);
     }
   }
   ```
5. In `AgenticAiConnectorsAutoConfiguration`, add `AgenticAiAnthropicFrameworkConfiguration.class` to the
   `@Import({ ... })` list (after `AgenticAiLangchain4JFrameworkConfiguration.class`) and its import
   statement. This registers the native factory as an additional `ChatModelApiFactory` bean that
   `chatModelApiRegistry(List<ChatModelApiFactory>)` picks up automatically.
6. Run the two test classes → **pass**. Then run the full framework test package to confirm no regression:
   `mvn test -pl connector-agentic-ai -f connectors/agentic-ai/pom.xml -Dtest='io.camunda.connector.agenticai.aiagent.provider.*'`.
7. Commit: `Register native Anthropic factory and resolve direct-backend v2 configs to it`.

---

## Task 7 — WireMock wire-format e2e smoke fixture on the v2 template

**Files**
- Create: `connectors-e2e-test/connectors-e2e-test-agentic-ai/src/test/java/io/camunda/connector/e2e/agenticai/aiagent/wiremock/anthropic/NativeAnthropicMessagesWireFormatFixture.java`
- Modify: `.../wiremock/ProviderWireFormatSmokeTests.java` (register in `fixtures()`)

**Interfaces**
- Implements `io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ProviderWireFormatFixture`
  (`apiName()`, `configureProvider(WireMockRuntimeInfo)`, `stubConversation(TurnStub...)`,
  `recordedRequests()`, and the `assertResponseFormatConfigured(...)` default which asserts
  type `json_schema`).
- Reuses the existing Anthropic stub/parse helpers (`AnthropicMessagesChatModelStubs`,
  `AnthropicMessagesRecordedConversation`, `AnthropicMessagesRecordedChatRequestAdapter`) — the wire
  protocol is identical; only the element-template config differs (v2 ids).

**Steps**

1. Write the new fixture, mirroring `AnthropicMessagesWireFormatFixture` but targeting the **v2**
   property ids (confirmed against `agenticai-ai-agent-task.v2.json`):
   ```java
   public final class NativeAnthropicMessagesWireFormatFixture implements ProviderWireFormatFixture {
     @Override
     public String apiName() {
       return "NativeAnthropicMessages";
     }

     @Override
     public String toString() {
       return apiName();
     }

     @Override
     public Function<ElementTemplate, ElementTemplate> configureProvider(WireMockRuntimeInfo wireMock) {
       return template ->
           template
               .property("configuration.type", "anthropic")
               .property("configuration.anthropic.backend.type", "direct")
               .property(
                   "configuration.anthropic.backend.direct.endpoint",
                   wireMock.getHttpBaseUrl() + "/v1/")
               .property("configuration.anthropic.backend.apiKey", "dummy")
               .property("configuration.anthropic.model.model", "test-model");
     }

     @Override
     public void stubConversation(TurnStub... turns) {
       AnthropicMessagesChatModelStubs.stubConversation(
           Arrays.stream(turns)
               .map(NativeAnthropicMessagesWireFormatFixture::toStubTurn)
               .toArray(AnthropicMessagesChatModelStubs.Turn[]::new));
     }

     @Override
     public List<RecordedChatRequest> recordedRequests() {
       return AnthropicMessagesRecordedConversation.recorded().requests().stream()
           .<RecordedChatRequest>map(AnthropicMessagesRecordedChatRequestAdapter::new)
           .toList();
     }

     private static AnthropicMessagesChatModelStubs.Turn toStubTurn(TurnStub turn) {
       return switch (turn) {
         case TurnStub.Text t ->
             AnthropicMessagesChatModelStubs.Turn.text(t.text(), t.inputTokens(), t.outputTokens());
         case TurnStub.ToolCalls tc ->
             AnthropicMessagesChatModelStubs.Turn.toolCalls(
                 tc.text(),
                 tc.inputTokens(),
                 tc.outputTokens(),
                 tc.toolCalls().stream()
                     .map(c -> AnthropicMessagesChatModelStubs.ToolCall.of(c.id(), c.name(), c.argumentsJson()))
                     .toArray(AnthropicMessagesChatModelStubs.ToolCall[]::new));
       };
     }
   }
   ```
   (Copy `toStubTurn` verbatim from the existing fixture — the two are identical apart from
   `apiName()` and `configureProvider`.) The template class the base test applies must be the **v2**
   template; if `BaseAiAgentJobWorkerTest` hardcodes the v1 template path, override the template source
   in this fixture's scenario setup or add a v2-targeting hook — confirm the base test's template
   resolution at impl and point it at `agenticai-ai-agent-task.v2.json` for this fixture.
2. Register in `ProviderWireFormatSmokeTests.fixtures()`:
   ```java
   return Stream.of(
       new OpenAiCompletionsWireFormatFixture(),
       new AnthropicMessagesWireFormatFixture(),
       new NativeAnthropicMessagesWireFormatFixture(),
       new BedrockConverseWireFormatFixture(),
       new AzureOpenAiCompletionsWireFormatFixture());
   ```
3. Test-compile the e2e module and run the 4 scenarios for the new parameter (outside sandbox;
   requires `element-templates-cli` on PATH):
   ```
   mvn -q -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -am -DskipTests test-compile -f pom.xml
   mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai \
       -Dtest=ProviderWireFormatSmokeTests -f pom.xml
   ```
   All four scenarios (`singleTurnTextResponse`, `toolCallingTurn`, `documentInUserPrompt`,
   `jsonResponseSchemaStructuredOutput`) must pass for `NativeAnthropicMessages`. The structured-output
   scenario exercises decision #4 (the native emits `output_config.format`, which the existing recorded
   adapter reads as type `json_schema`, schema name null).
4. Commit: `Add v2 native-Anthropic WireMock wire-format e2e smoke fixture`.

---

## Deferred follow-ups (explicitly OUT of C7)

- Request-side extended-thinking enablement (`MessageCreateParams.thinking(...)` / `enabledThinking(long)`)
  gated on `capabilities.supportsReasoning()`.
- `ReasoningContent.providerPayload` (thinking `signature`) round-trip BACK to Anthropic on replay
  (re-emitting `thinking`/`redacted_thinking` blocks in assistant history).
- Prompt `cache_control` breakpoint WRITES (`CacheControlEphemeral` on system/tool/content blocks) gated
  on `capabilities.supportsPromptCaching()`.
- Capability-resolution caching / memoization (C3f).
- Anthropic-on-Bedrock backend (C11) — bedrock v2 configs intentionally still fail loud after C7.

## Verification checklist (end of chunk)

- `mvn clean install -f connectors/agentic-ai/pom.xml` green (unit tests + spotless + license + javadoc).
- New files carry the proprietary license header; `@NullMarked` package-info present in the new package.
- `connectors-e2e-test/connectors-e2e-test-agentic-ai` test-compiles and `ProviderWireFormatSmokeTests`
  passes for all fixtures including `NativeAnthropicMessages`.
- No change to any v1/v2 template JSON, persisted type, discriminator value, or the L4J bridge.
- `git log --oneline e23dd703df..HEAD` shows one focused commit per task (7 commits).
