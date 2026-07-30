# ADR-0006: `createDocument` FEEL Function for Result-Expression Document Extraction

## Status
Proposed

## Context

[#4715](https://github.com/camunda/connectors/issues/4715) asks for a way to store only *part* of an HTTP response as a document, instead of the REST connector's current all-or-nothing `storeResponse` toggle (`connectors/http/http-base/.../HttpCommonRequest.java`, `HttpService.java`), which can only convert the entire response body. The motivating example is a response whose JSON body embeds several base64-encoded files (`{"file": [{"filename": ..., "document": {"data": "..."}, "outputFormat": ...}]}`) — today a user must store the whole payload or nothing.

Camunda's architecture session on the issue proposed a new FEEL function, callable from a connector's result expression, that turns an arbitrary sub-value into a Document reference:

```feel
{ mydocument: createDocument(response.body.file[1]) }
```

with a parallel ask for the Webhook connector: `createDocuments(request.files)` / `createDocument(request.files[1])`.

Investigation into the current runtime surfaced three load-bearing facts that shape this decision:

1. **The FEEL function SPI is a static singleton.** `LocalFeelExpressionEvaluator` (`connector-runtime/connector-feel/`) loads `org.camunda.feel.context.CustomFunctionProvider` implementations once via `SpiServiceLoader`. The existing connector functions (`bpmnError`, `jobError`, `ignoreError`, `backoff` in `connector-runtime/connector-feel/.../function/`) are all stateless: they build a sentinel `ValContext` map tagged with a discriminator (e.g. `errorType: "jobError"`), which is later parsed into a typed `ConnectorError` by `ConnectorResultHandler`. None of them touch external state at evaluation time — there is no per-job hook for injecting a `DocumentFactory` into the statically-loaded FEEL engine without a ThreadLocal.
2. **`ConnectorResultHandler` is the single shared choke point for result-expression evaluation**, used by both the outbound path (`SpringConnectorJobHandler.java:140`, after `DocumentReturnProcessor` has already materialized any whole-response document, so a `DocumentFactory` is in scope at the call site) and the inbound path (`InboundCorrelationHandler.java:70`, which builds its own `ConnectorResultHandler` and currently does not receive a `DocumentFactory`, though one already exists as a Spring bean in the same application context and reaches `InboundConnectorContextImpl` through a separate constructor argument).
3. **The Webhook connector already eagerly converts every multipart upload into a `Document`** before FEEL ever runs (`InboundWebhookRestController.java:243-244`, via `createDocuments(...)` → `context.create(DocumentCreationRequest.from(part.inputStream())...)`), exposed as a `documents` list sibling to `request` in `WebhookTriggerResultContext`. The architecture session's `request.files` premise does not match current behavior: files are not raw bytes at FEEL-evaluation time, they're already `Document` references. Webhook's only remaining gap is the same as REST's — pulling a base64 field out of a JSON request body — since multipart files are already handled.
4. **A lazy, deserialization-time resolution mechanism already exists** for the opposite direction: `IntrinsicFunctionModel` / `IntrinsicFunctionRegistry` (`connector-runtime/connector-runtime-core/.../intrinsic/`) resolve functions like `getText`, `getJson`, `base64`, `createLink` when a JSON payload carrying `"camunda.function.type"` is deserialized as connector *input*. `ConnectorResultHandler.FORBIDDEN_LITERALS` explicitly forbids that discriminator from appearing in evaluated *output*, precisely to keep this input-side mechanism from firing unexpectedly on process variables. Reusing it for `createDocument` would mean the base64 payload stays embedded in the process variable until some future, unguaranteed deserialization step reads it as a `Document` — which defeats the purpose of the feature (replacing a large inline blob with a small reference).

## Decision

Add `createDocument(value)` as a new, stateless FEEL function registered through the existing `FeelConnectorFunctionProvider` SPI, resolved **eagerly** inside `ConnectorResultHandler` right after result/error-expression evaluation — not via the lazy intrinsic-function mechanism, and not by threading a `DocumentFactory` into the FEEL engine itself.

1. **Function shape.** `createDocument(value)`:
   - If `value` is a context/map with a recognized key for content (`content` or `data`, a base64 string), it also reads optional `name`/`fileName` and `contentType` keys.
   - If `value` is a bare string, it's treated as the base64 content with no metadata.
   - This covers both forms from the architecture session — a bare string, e.g. `createDocument(response.body.file[1].document.data)`, and an object with recognized keys, e.g. `createDocument(response.body.file[1].document)` (whose shape, per the motivating example above, is already `{"data": "..."}` — a top-level recognized key) — without requiring the caller to reshape data into a fixed envelope. Note the architecture session's own illustrative `createDocument(response.body.file[1])` does *not* work as written against that motivating example's actual shape (`{filename, document: {data}, outputFormat}` has no top-level `content`/`data` key); combining the nested `data` with the sibling `filename` needs an explicit object, e.g. `createDocument({data: response.body.file[1].document.data, name: response.body.file[1].filename})`.
   - When `name`/`fileName` is omitted, a random UUID is used as the filename; when `contentType` is omitted, it's resolved via the existing `MimeTypeResolver.resolveContentType(contentType, fileName)` (`connector-runtime/connector-runtime-core/.../document/MimeTypeResolver.java`) — extension-based inference from `fileName`, falling back to `application/octet-stream` when no extension is available (e.g. an auto-generated UUID name). This reuses the exact utility and defaulting behavior already established for inline documents in ADR-0005 Step 1, rather than introducing a new byte-sniffing mechanism.
   - The function returns a sentinel `ValContext` tagged with a **new** discriminator (distinct from `IntrinsicFunctionModel.DISCRIMINATOR_KEY`, which remains reserved for input-side functions and stays forbidden in output), e.g. `connectorResultFunction: "createDocument"`. The discriminator *value* itself is nonce-suffixed at class-load time (`"createDocument:" + UUID.randomUUID()`) so it can never be forged by data arriving in a connector's response/request payload — the sentinel only ever exists transiently within a single JVM's FEEL evaluation, never serialized across a process boundary.

2. **Resolution point.** `ConnectorResultHandler` gains a `DocumentFactory` constructor dependency. After `evaluateToJson` produces the result JSON, a new step walks the parsed tree (objects and arrays, at any nesting depth) looking for the `createDocument` sentinel; each match is replaced with a real `Document` built via `documentFactory.create(DocumentCreationRequest.from(...).contentType(...).fileName(...).build())`. This runs in **both** `createOutputVariables` (success path) and `examineErrorExpression` (error path), so a marker used inside an error expression's `variables` cannot leak unresolved into job/incident variables either.

3. **Wiring.**
   - Outbound: `SpringConnectorJobHandler` already holds `this.documentFactory` in scope where it constructs `ConnectorResultHandler` — pass it through.
   - Inbound: thread a `DocumentFactory` through `InboundCorrelationConfiguration` → `MeteredInboundCorrelationHandler` → `InboundCorrelationHandler` → its internal `ConnectorResultHandler`. The bean already exists in the same Spring context (used by `InboundConnectorRuntimeConfiguration`/`DefaultInboundConnectorContextFactory`); this is additive plumbing, not new infrastructure.

4. **Webhook scope: no dedicated code.** Because Webhook's result expression runs through the same `ConnectorResultHandler.createOutputVariables`, it inherits `createDocument()` automatically. The existing eager multipart-to-`Document` conversion (`documents[]`) is untouched — it solves a different, already-working case. No `createDocuments` (plural) function is introduced for Webhook or REST: FEEL's native iteration (`for f in response.body.file return createDocument(f)`) covers the multi-value case without adding a second function surface.

5. **Existing `storeResponse` toggle is untouched.** `createDocument()` is purely additive in the result expression; the whole-response toggle keeps working exactly as today, per ADR-0005's backward-compatibility constraint (no existing template, process, or FEEL expression breaks).

## Consequences

### Positive

- **Solves the issue's actual use case**: partial extraction of documents from a structured response/request body, without an all-or-nothing toggle.
- **One implementation serves REST, Webhook, and any future connector** that routes through `ConnectorResultHandler` — no per-connector code.
- **Consistent with ADR-0005's principle that the runtime owns document-conversion mechanics**, not individual connectors.
- **No breaking changes.** Additive function, existing toggles and existing `documents[]` webhook behavior unchanged.
- **Avoids a static-singleton/ThreadLocal hack** in the FEEL engine by keeping the function itself stateless and doing the actual `DocumentFactory` work at the walker step, which already has natural per-job scope.

### Negative

- **Sentinel walking adds a tree-traversal step to result/error-expression evaluation** when a `createDocument` call is present. Guarded by a cheap `String.contains` check on the discriminator property beforehand, so evaluations that never call `createDocument` skip the parse/walk/reserialize entirely and see zero added cost.
- **Two sentinel-style discriminators now coexist** (`IntrinsicFunctionModel.DISCRIMINATOR_KEY` for input, the new one for output) — a future maintainer must understand why they're deliberately different and non-interchangeable.
- **`InboundCorrelationHandler`'s constructor signature changes**, along with its Spring wiring chain (`InboundCorrelationConfiguration`, `MeteredInboundCorrelationHandler`) — every call site and test double for these classes needs updating.
- **No plural `createDocuments`.** Users must know to use FEEL's `for` iteration for multi-file extraction rather than a single function call; this is a discoverability cost against the issue's originally-discussed API surface.
