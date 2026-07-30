# createDocument FEEL Function Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `createDocument(value)` FEEL function, usable in any connector's result/error expression, that turns an arbitrary sub-value (a base64 string, or an object carrying one) into a real Camunda `Document` reference — closing [#4715](https://github.com/camunda/connectors/issues/4715) for the REST connector, and for the Webhook connector for free, with no connector-specific code.

**Architecture:** Per [ADR-0006](/docs/adr/ADR-0006-createdocument-feel-function.md): `createDocument` is a stateless FEEL function (like the existing `bpmnError`/`jobError`) that tags its argument with a JSON sentinel. A new `ResultDocumentResolver` walks the JSON tree produced by evaluating a result/error expression, finds that sentinel wherever it appears, and replaces it with a real `Document` built via `DocumentFactory`. This resolver is wired into the single shared `ConnectorResultHandler`, used by both the outbound (`SpringConnectorJobHandler`) and inbound (`InboundCorrelationHandler`) paths — so both REST and Webhook get the capability from one change.

**Tech Stack:** Java 21, Maven, JUnit 5, AssertJ, Mockito, feel-scala, Jackson.

## Global Constraints

- Java 21 for all touched modules (connector-runtime, connector-feel are not SDK, so Java 21 applies — see AGENTS.md).
- Always use `ConnectorsObjectMapperSupplier.getCopy()` (or the module's existing `TestObjectMapperSupplier`) for `ObjectMapper` instances — never `new ObjectMapper()` bare, except where an existing test already does so and this plan explicitly changes it.
- No backward-compatible constructor overloads: `ConnectorResultHandler`, `InboundCorrelationHandler`, and `MeteredInboundCorrelationHandler` constructors change signature directly; every call site (production and test) is updated in the same task, not shimmed.
- No `createDocuments` (plural) function — FEEL's native `for` iteration covers the multi-value case (per ADR-0006).
- Run `mvn spotless:apply` (or let the pre-commit hook run it) before committing any Java changes in this repo — the repo's commit hook already enforces this (seen in earlier commits in this session: "Spotless ................. Passed").

---

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `connector-runtime/connector-feel/src/main/java/io/camunda/connector/feel/function/CreateDocumentFunction.java` | Create | Stateless FEEL function; tags its argument with a sentinel, does no I/O |
| `connector-runtime/connector-feel/src/main/java/io/camunda/connector/feel/FeelConnectorFunctionProvider.java` | Modify | Register `createDocument`; hosts the shared discriminator constants |
| `connector-runtime/connector-feel/src/test/java/io/camunda/connector/feel/LocalFeelExpressionEvaluatorExpressionEvaluationTest.java` | Modify | End-to-end FEEL-level tests for the new function (existing convention for all connector FEEL functions) |
| `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/document/ResultDocumentResolver.java` | Create | Walks an evaluated JSON tree, resolves `createDocument` sentinels into real `Document`s via `DocumentFactory` |
| `connector-runtime/connector-runtime-core/src/test/java/io/camunda/connector/runtime/core/document/ResultDocumentResolverTest.java` | Create | Unit tests for the resolver in isolation |
| `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/ConnectorResultHandler.java` | Modify | Wires `ResultDocumentResolver` into `createOutputVariables` and `examineErrorExpression` |
| `connector-runtime/connector-runtime-core/src/test/java/io/camunda/connector/runtime/core/ConnectorResultHandlerTest.java` | Modify | Updates constructor call, adds end-to-end tests for both success and error paths |
| `connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/outbound/job/SpringConnectorJobHandler.java` | Modify | One-line change: pass `documentFactory` into `ConnectorResultHandler` |
| `connector-runtime/connector-runtime-spring/src/test/java/io/camunda/connector/runtime/outbound/job/SpringConnectorJobHandlerTest.java` | Modify | Adds an outbound end-to-end test using a real `DocumentFactory` |
| `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/inbound/correlation/InboundCorrelationHandler.java` | Modify | Constructor gains `DocumentFactory` param, threaded into its `ConnectorResultHandler` |
| `connector-runtime/connector-runtime-core/src/test/java/io/camunda/connector/runtime/core/inbound/correlation/InboundCorrelationHandlerTest.java` | Modify | Updates constructor call, adds inbound end-to-end test (proves Webhook inherits the capability) |
| `connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/inbound/MeteredInboundCorrelationHandler.java` | Modify | Constructor gains `DocumentFactory` param, forwarded via `super(...)` |
| `connector-runtime/connector-runtime-spring/src/test/java/io/camunda/connector/runtime/inbound/MeteredInboundCorrelationHandlerTest.java` | Modify | Updates constructor call |
| `connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/inbound/InboundCorrelationConfiguration.java` | Modify | Threads a `DocumentFactory` bean through the static helper and the `@Bean` method |
| `connector-runtime/connector-runtime-spring/src/test/java/io/camunda/connector/runtime/inbound/InboundCorrelationConfigurationTest.java` | Modify | Updates the two existing calls with a `mock(DocumentFactory.class)` arg |
| `connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/inbound/InboundConnectorRuntimeConfiguration.java` | Modify | Forwards its already-injected `documentFactory` param into `buildCorrelationHandlersByPhysicalTenantId` |

---

## Task 1: `createDocument` FEEL function

**Files:**
- Create: `connector-runtime/connector-feel/src/main/java/io/camunda/connector/feel/function/CreateDocumentFunction.java`
- Modify: `connector-runtime/connector-feel/src/main/java/io/camunda/connector/feel/FeelConnectorFunctionProvider.java`
- Test: `connector-runtime/connector-feel/src/test/java/io/camunda/connector/feel/LocalFeelExpressionEvaluatorExpressionEvaluationTest.java`

**Interfaces:**
- Produces: `FeelConnectorFunctionProvider.RESULT_FUNCTION_TYPE_PROPERTY` (`"connectorResultFunction"`) and `FeelConnectorFunctionProvider.CREATE_DOCUMENT_TYPE_VALUE` (`"createDocument"`) — string constants Task 2's `ResultDocumentResolver` imports directly to recognize the sentinel. The evaluated JSON shape for `createDocument(value)` is `{"connectorResultFunction": "createDocument", "value": <value, JSON-converted as-is>}`.

- [ ] **Step 1: Write the failing FEEL-level tests**

Add to `connector-runtime/connector-feel/src/test/java/io/camunda/connector/feel/LocalFeelExpressionEvaluatorExpressionEvaluationTest.java` (same file/convention already used for `bpmnError`/`jobError` — no separate per-function test class exists in this codebase):

```java
@Test
void createDocumentFunctionWithObjectArgument() {
  final var resultExpression =
      "=createDocument({content: \"aGVsbG8=\", name: \"hello.txt\", contentType: \"text/plain\"})";
  Map<String, Object> result = objectUnderTest.evaluate(resultExpression, Map.of());
  assertThat(result).containsEntry("connectorResultFunction", "createDocument");
  @SuppressWarnings("unchecked")
  Map<String, Object> value = (Map<String, Object>) result.get("value");
  assertThat(value)
      .containsEntry("content", "aGVsbG8=")
      .containsEntry("name", "hello.txt")
      .containsEntry("contentType", "text/plain");
}

@Test
void createDocumentFunctionWithStringArgument() {
  final var resultExpression = "=createDocument(\"aGVsbG8=\")";
  Map<String, Object> result = objectUnderTest.evaluate(resultExpression, Map.of());
  assertThat(result).containsEntry("connectorResultFunction", "createDocument");
  assertThat(result).containsEntry("value", "aGVsbG8=");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl connector-runtime/connector-feel -Dtest=LocalFeelExpressionEvaluatorExpressionEvaluationTest#createDocumentFunctionWithObjectArgument+createDocumentFunctionWithStringArgument`
Expected: FAIL — `createDocument` is an unknown FEEL function.

- [ ] **Step 3: Create `CreateDocumentFunction`**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.feel.function;

import static io.camunda.connector.feel.FeelConnectorFunctionProvider.CREATE_DOCUMENT_TYPE_VALUE;
import static io.camunda.connector.feel.FeelConnectorFunctionProvider.RESULT_FUNCTION_TYPE_PROPERTY;

import java.util.HashMap;
import java.util.List;
import org.camunda.feel.context.Context;
import org.camunda.feel.context.JavaFunction;
import org.camunda.feel.syntaxtree.Val;
import org.camunda.feel.syntaxtree.ValContext;
import scala.collection.JavaConverters;
import scala.collection.immutable.Map;
import scala.collection.immutable.Map$;

/**
 * FEEL function {@code createDocument(value)}. Deliberately stateless: it only tags {@code value}
 * with a sentinel discriminator so it survives JSON serialization intact. Actual document
 * creation happens later, when {@code io.camunda.connector.runtime.core.document
 * .ResultDocumentResolver} walks the evaluated result/error expression tree and finds this
 * sentinel — this function has no access to a {@code DocumentFactory} and must not attempt to
 * create anything itself.
 */
public class CreateDocumentFunction {

  public static final String NAME = "createDocument";

  private static final List<String> ARGUMENTS = List.of("value");

  private static final JavaFunction FUNCTION =
      new JavaFunction(ARGUMENTS, args -> createContext(args.get(0)));

  public static final List<JavaFunction> FUNCTIONS = List.of(FUNCTION);

  private static ValContext createContext(Val value) {
    java.util.Map<String, Object> javaMap = new HashMap<>();
    javaMap.put(RESULT_FUNCTION_TYPE_PROPERTY, CREATE_DOCUMENT_TYPE_VALUE);
    javaMap.put("value", value);
    return new ValContext(
        new Context.StaticContext(
            Map.from(JavaConverters.asScala(javaMap)), Map$.MODULE$.empty()));
  }
}
```

- [ ] **Step 4: Register the function and its constants in `FeelConnectorFunctionProvider`**

In `connector-runtime/connector-feel/src/main/java/io/camunda/connector/feel/FeelConnectorFunctionProvider.java`, add the import and constants, and register the function:

```java
import io.camunda.connector.feel.function.CreateDocumentFunction;
```

```java
  public static final String ERROR_TYPE_PROPERTY = "errorType";
  public static final String BPMN_ERROR_TYPE_VALUE = "bpmnError";
  public static final String JOB_ERROR_TYPE_VALUE = "jobError";
  public static final String IGNORE_ERROR_TYPE_VALUE = "ignoreError";
  public static final String RESULT_FUNCTION_TYPE_PROPERTY = "connectorResultFunction";
  public static final String CREATE_DOCUMENT_TYPE_VALUE = "createDocument";

  private static final Map<String, List<JavaFunction>> functions =
      Map.of(
          BpmnErrorFunction.NAME, BpmnErrorFunction.FUNCTIONS,
          JobErrorFunction.NAME, JobErrorFunction.FUNCTIONS,
          IgnoreErrorFunction.NAME, IgnoreErrorFunction.FUNCTIONS,
          BackoffFunction.NAME, BackoffFunction.FUNCTIONS,
          CreateDocumentFunction.NAME, CreateDocumentFunction.FUNCTIONS);
```

(`Map.of` in `java.util.Map` supports at most 10 key-value pairs via varargs overloads — 5 entries is well within range, no change to the `Map.of` call style is needed beyond adding the new pair.)

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn test -pl connector-runtime/connector-feel -Dtest=LocalFeelExpressionEvaluatorExpressionEvaluationTest`
Expected: PASS (all tests in the class, including the two new ones and all pre-existing ones).

- [ ] **Step 6: Commit**

```bash
git add connector-runtime/connector-feel/src/main/java/io/camunda/connector/feel/function/CreateDocumentFunction.java
git add connector-runtime/connector-feel/src/main/java/io/camunda/connector/feel/FeelConnectorFunctionProvider.java
git add connector-runtime/connector-feel/src/test/java/io/camunda/connector/feel/LocalFeelExpressionEvaluatorExpressionEvaluationTest.java
git commit -m "feat(feel): add createDocument FEEL function (#4715)"
```

---

## Task 2: `ResultDocumentResolver` (the sentinel walker)

**Files:**
- Create: `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/document/ResultDocumentResolver.java`
- Test: `connector-runtime/connector-runtime-core/src/test/java/io/camunda/connector/runtime/core/document/ResultDocumentResolverTest.java`

**Interfaces:**
- Consumes: `FeelConnectorFunctionProvider.RESULT_FUNCTION_TYPE_PROPERTY`/`CREATE_DOCUMENT_TYPE_VALUE` (Task 1). `io.camunda.connector.runtime.core.document.MimeTypeResolver.resolveContentType(String explicitContentType, String fileName)` (already exists, same package, no import needed). `io.camunda.connector.api.document.DocumentFactory.create(DocumentCreationRequest)` / `DocumentCreationRequest.from(byte[])`.
- Produces: `public class ResultDocumentResolver { public ResultDocumentResolver(DocumentFactory documentFactory); public Object resolve(JsonNode node); }` — Task 3 (`ConnectorResultHandler`) constructs one and calls `resolve(JsonNode)` on the root of an evaluated expression tree, expecting back a plain `Map`/`List`/`String`/`Number`/`Boolean`/`null`/`Document` tree (never a raw sentinel `Map`).

- [ ] **Step 1: Write the failing tests**

Create `connector-runtime/connector-runtime-core/src/test/java/io/camunda/connector/runtime/core/document/ResultDocumentResolverTest.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.core.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorInputException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResultDocumentResolverTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private TestDocumentFactory documentFactory;
  private ResultDocumentResolver resolver;

  @BeforeEach
  void setUp() {
    documentFactory = new TestDocumentFactory();
    resolver = new ResultDocumentResolver(documentFactory);
  }

  private JsonNode treeOf(Object value) {
    return objectMapper.valueToTree(value);
  }

  @Test
  void resolvesBareStringArgument() {
    String base64 = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
    JsonNode tree = treeOf(Map.of("connectorResultFunction", "createDocument", "value", base64));

    Object resolved = resolver.resolve(tree);

    assertThat(resolved).isInstanceOf(Document.class);
    assertThat(((Document) resolved).asByteArray())
        .isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void resolvesObjectArgumentWithNameAndContentType() {
    String base64 = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
    JsonNode tree =
        treeOf(
            Map.of(
                "connectorResultFunction",
                "createDocument",
                "value",
                Map.of("content", base64, "name", "hello.txt", "contentType", "text/plain")));

    Document resolved = (Document) resolver.resolve(tree);

    assertThat(resolved.asByteArray()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
    assertThat(resolved.metadata().getFileName()).isEqualTo("hello.txt");
    assertThat(resolved.metadata().getContentType()).isEqualTo("text/plain");
  }

  @Test
  void generatesRandomFileNameAndDefaultsContentTypeWhenOmitted() {
    String base64 = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
    JsonNode tree =
        treeOf(
            Map.of(
                "connectorResultFunction", "createDocument", "value", Map.of("content", base64)));

    Document resolved = (Document) resolver.resolve(tree);

    assertThat(resolved.metadata().getFileName()).isNotBlank();
    assertThat(resolved.metadata().getContentType()).isEqualTo("application/octet-stream");
  }

  @Test
  void resolvesNestedSentinelsInsideObjectsAndArrays() {
    String base64 = Base64.getEncoder().encodeToString("a".getBytes(StandardCharsets.UTF_8));
    JsonNode tree =
        treeOf(
            Map.of(
                "files",
                List.of(Map.of("connectorResultFunction", "createDocument", "value", base64)),
                "label",
                "unrelated"));

    @SuppressWarnings("unchecked")
    Map<String, Object> resolved = (Map<String, Object>) resolver.resolve(tree);

    assertThat(resolved.get("label")).isEqualTo("unrelated");
    @SuppressWarnings("unchecked")
    List<Object> files = (List<Object>) resolved.get("files");
    assertThat(files).hasSize(1);
    assertThat(files.get(0)).isInstanceOf(Document.class);
  }

  @Test
  void throwsWhenContentIsMissing() {
    JsonNode tree =
        treeOf(
            Map.of(
                "connectorResultFunction",
                "createDocument",
                "value",
                Map.of("name", "x.txt")));

    assertThatThrownBy(() -> resolver.resolve(tree)).isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void throwsWhenContentIsNotValidBase64() {
    JsonNode tree =
        treeOf(Map.of("connectorResultFunction", "createDocument", "value", "not-base64!!"));

    assertThatThrownBy(() -> resolver.resolve(tree)).isInstanceOf(ConnectorInputException.class);
  }

  @Test
  void leavesNonSentinelValuesUntouched() {
    JsonNode tree = treeOf(Map.of("a", 1, "b", List.of("x", "y"), "c", true));

    Object resolved = resolver.resolve(tree);

    assertThat(resolved).isEqualTo(Map.of("a", 1L, "b", List.of("x", "y"), "c", true));
  }
}
```

Note: `TestDocumentFactory` here is `io.camunda.connector.runtime.core.document.TestDocumentFactory`, already present in this exact package/module (`connector-runtime-core/src/test/`), no-arg constructible, backed by the real `DocumentFactoryImpl`/`InMemoryDocumentStore.INSTANCE` — no new test double needed.

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl connector-runtime/connector-runtime-core -Dtest=ResultDocumentResolverTest`
Expected: FAIL to compile — `ResultDocumentResolver` doesn't exist yet.

- [ ] **Step 3: Create `ResultDocumentResolver`**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.core.document;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.feel.FeelConnectorFunctionProvider;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves {@code createDocument(...)} sentinel markers produced by {@code
 * io.camunda.connector.feel.function.CreateDocumentFunction} into real {@link Document}
 * references. Walks the full JSON tree returned by evaluating a FEEL result/error expression,
 * since the sentinel can appear anywhere in that tree, not only at the root.
 */
public class ResultDocumentResolver {

  private final DocumentFactory documentFactory;

  public ResultDocumentResolver(DocumentFactory documentFactory) {
    this.documentFactory = documentFactory;
  }

  public Object resolve(JsonNode node) {
    if (node.isObject()) {
      if (isCreateDocumentSentinel(node)) {
        return createDocument(node.get("value"));
      }
      Map<String, Object> result = new LinkedHashMap<>();
      node.fields().forEachRemaining(entry -> result.put(entry.getKey(), resolve(entry.getValue())));
      return result;
    }
    if (node.isArray()) {
      List<Object> result = new ArrayList<>();
      node.forEach(element -> result.add(resolve(element)));
      return result;
    }
    return scalarValue(node);
  }

  private boolean isCreateDocumentSentinel(JsonNode node) {
    JsonNode discriminator = node.get(FeelConnectorFunctionProvider.RESULT_FUNCTION_TYPE_PROPERTY);
    return discriminator != null
        && discriminator.isTextual()
        && FeelConnectorFunctionProvider.CREATE_DOCUMENT_TYPE_VALUE.equals(
            discriminator.textValue());
  }

  private Document createDocument(JsonNode value) {
    if (value == null || value.isMissingNode() || value.isNull()) {
      throw new ConnectorInputException(
          "createDocument() was called without a value to convert into a document");
    }
    String content;
    String name = null;
    String contentType = null;
    if (value.isTextual()) {
      content = value.textValue();
    } else if (value.isObject()) {
      content = firstNonBlankText(value, "content", "data");
      name = firstNonBlankText(value, "name", "fileName");
      contentType = firstNonBlankText(value, "contentType");
    } else {
      throw new ConnectorInputException(
          "createDocument() expects a string or an object argument, got: " + value.getNodeType());
    }
    if (content == null) {
      throw new ConnectorInputException(
          "createDocument() requires a 'content' or 'data' field containing a base64-encoded"
              + " string");
    }
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(content);
    } catch (IllegalArgumentException e) {
      throw new ConnectorInputException(
          "createDocument() 'content'/'data' is not valid base64: " + e.getMessage());
    }
    String fileName = name != null ? name : UUID.randomUUID().toString();
    String resolvedContentType = MimeTypeResolver.resolveContentType(contentType, fileName);
    return documentFactory.create(
        DocumentCreationRequest.from(decoded)
            .contentType(resolvedContentType)
            .fileName(fileName)
            .build());
  }

  private String firstNonBlankText(JsonNode object, String... keys) {
    for (String key : keys) {
      JsonNode fieldValue = object.get(key);
      if (fieldValue != null && fieldValue.isTextual() && !fieldValue.textValue().isBlank()) {
        return fieldValue.textValue();
      }
    }
    return null;
  }

  private Object scalarValue(JsonNode node) {
    if (node.isTextual()) return node.textValue();
    if (node.isBoolean()) return node.booleanValue();
    if (node.isNull() || node.isMissingNode()) return null;
    if (node.isIntegralNumber()) return node.longValue();
    if (node.isFloatingPointNumber()) return node.doubleValue();
    return node.asText();
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl connector-runtime/connector-runtime-core -Dtest=ResultDocumentResolverTest`
Expected: PASS (all 7 tests).

- [ ] **Step 5: Commit**

```bash
git add connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/document/ResultDocumentResolver.java
git add connector-runtime/connector-runtime-core/src/test/java/io/camunda/connector/runtime/core/document/ResultDocumentResolverTest.java
git commit -m "feat(runtime-core): add ResultDocumentResolver to resolve createDocument sentinels (#4715)"
```

---

## Task 3: Wire the resolver into `ConnectorResultHandler`

`ConnectorResultHandler` and `InboundCorrelationHandler` both live in the `connector-runtime-core` module, and `InboundCorrelationHandler` constructs a `ConnectorResultHandler` internally — so both classes' constructor changes must land together for this module to compile. This task includes both, so `connector-runtime-core` compiles and its full test suite passes at the end of this task, with no broken intermediate state.

**Files:**
- Modify: `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/ConnectorResultHandler.java`
- Modify: `connector-runtime/connector-runtime-core/src/test/java/io/camunda/connector/runtime/core/ConnectorResultHandlerTest.java`
- Modify: `connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/inbound/correlation/InboundCorrelationHandler.java`
- Modify: `connector-runtime/connector-runtime-core/src/test/java/io/camunda/connector/runtime/core/inbound/correlation/InboundCorrelationHandlerTest.java`

**Interfaces:**
- Consumes: `ResultDocumentResolver` (Task 2), `io.camunda.connector.api.document.DocumentFactory`.
- Produces: `ConnectorResultHandler(ObjectMapper objectMapper, DocumentFactory documentFactory)` — the new required constructor signature Task 4 and Task 5 both call. `InboundCorrelationHandler(CamundaClient, ObjectMapper, Duration, DocumentFactory)` — the new required constructor signature Task 5 calls.

- [ ] **Step 1: Write the failing tests**

In `connector-runtime/connector-runtime-core/src/test/java/io/camunda/connector/runtime/core/ConnectorResultHandlerTest.java`, change the fixture and add two tests. Replace:

```java
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ConnectorResultHandler connectorResultHandler =
      new ConnectorResultHandler(objectMapper);
```

with:

```java
  private final ObjectMapper objectMapper =
      ConnectorsObjectMapperSupplier.getCopy()
          .registerModule(new JacksonModuleDocumentSerializer());
  private final TestDocumentFactory documentFactory = new TestDocumentFactory();
  private final ConnectorResultHandler connectorResultHandler =
      new ConnectorResultHandler(objectMapper, documentFactory);
```

Add these imports:

```java
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.document.TestDocumentFactory;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
```

(`ConnectorsObjectMapperSupplier.getCopy()` registers the standard modules; adding `JacksonModuleDocumentSerializer` on top is what lets a `Document` object serialize correctly during the resolver's write-then-reparse round trip in Step 3 below — this exactly mirrors how `ConnectorsAutoConfiguration.connectorObjectMapper`/`outboundConnectorObjectMapper` are built in production.)

Add new test methods (anywhere in the class body, alongside the other `createOutputVariables`/`examineErrorExpression` tests already there):

```java
  @Test
  void createOutputVariablesResolvesCreateDocumentInResultExpression() {
    String base64 = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
    String resultExpression =
        "={myDoc: createDocument({content: \"" + base64 + "\", name: \"hello.txt\"})}";

    Map<String, Object> result =
        connectorResultHandler.createOutputVariables(Map.of(), null, resultExpression);

    assertThat(result).containsKey("myDoc");
    @SuppressWarnings("unchecked")
    Map<String, Object> documentReference = (Map<String, Object>) result.get("myDoc");
    assertThat(documentReference).containsEntry("camunda.document.type", "camunda");
    assertThat(documentReference).doesNotContainKey("connectorResultFunction");
  }

  @Test
  void examineErrorExpressionResolvesCreateDocumentInsideVariables() {
    String base64 = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
    String errorExpression =
        "=bpmnError(\"CODE\", \"message\", {myDoc: createDocument(\"" + base64 + "\")})";
    Map<String, String> headers = Map.of(Keywords.ERROR_EXPRESSION_KEYWORD, errorExpression);

    Optional<ConnectorError> result =
        connectorResultHandler.examineErrorExpression(
            Map.of(),
            headers,
            new ErrorExpressionJobContext(new ErrorExpressionJobContext.ErrorExpressionJob(3)));

    assertThat(result).isPresent();
    var bpmnError = (BpmnError) result.get();
    @SuppressWarnings("unchecked")
    Map<String, Object> documentReference =
        (Map<String, Object>) bpmnError.variables().get("myDoc");
    assertThat(documentReference).containsEntry("camunda.document.type", "camunda");
  }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl connector-runtime/connector-runtime-core -Dtest=ConnectorResultHandlerTest`
Expected: FAIL to compile — `ConnectorResultHandler(ObjectMapper, DocumentFactory)` doesn't exist yet (still 1-arg).

- [ ] **Step 3: Update `ConnectorResultHandler`**

Add imports:

```java
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.runtime.core.document.ResultDocumentResolver;
```

Change the field and constructor:

```java
  private final FeelExpressionEvaluator feelExpressionEvaluator =
      new LocalFeelExpressionEvaluator();
  private final ObjectMapper objectMapper;
  private final ResultDocumentResolver documentResolver;

  public ConnectorResultHandler(ObjectMapper objectMapper, DocumentFactory documentFactory) {
    this.objectMapper = objectMapper;
    this.documentResolver = new ResultDocumentResolver(documentFactory);
  }
```

Change `createOutputVariables` to route the evaluated JSON through resolution before parsing it as a `Map`:

```java
  public Map<String, Object> createOutputVariables(
      final Object responseContent,
      final @Nullable String resultVariableName,
      final @Nullable String resultExpression) {
    final Map<String, Object> outputVariables = new HashMap<>();

    if (isNotBlank(resultVariableName)) {
      outputVariables.put(resultVariableName, responseContent);
    }

    if (isNotBlank(resultExpression)) {
      var mappedResponseJson =
          feelExpressionEvaluator.evaluateToJson(
              resultExpression, responseContent, wrapResponse(responseContent));
      if (mappedResponseJson != null) {
        verifyNoForbiddenLiterals(mappedResponseJson);
        var resolvedResponseJson =
            resolveDocumentsAsJson(mappedResponseJson, resultExpression, "Result expression");
        var mappedResponse =
            parseJsonVarsAsTypeOrThrow(
                resolvedResponseJson, Map.class, resultExpression, "Result expression");
        if (mappedResponse != null) {
          outputVariables.putAll(mappedResponse);
        }
      }
    }
    return outputVariables;
  }
```

Change `examineErrorExpression` to resolve documents right after evaluation, before the existing filter/map chain:

```java
  public Optional<ConnectorError> examineErrorExpression(
      final Object responseContent,
      final Map<String, String> jobHeaders,
      ErrorExpressionJobContext jobContext) {
    final var errorExpression = jobHeaders.get(Keywords.ERROR_EXPRESSION_KEYWORD);
    if (errorExpression == null || errorExpression.isBlank()) {
      return Optional.empty();
    }
    // errorExpression is @NonNull below (NullAway flow narrowing)
    var evaluatedJson =
        feelExpressionEvaluator.evaluateToJson(
            errorExpression, responseContent, wrapResponse(responseContent), jobContext);
    return Optional.ofNullable(evaluatedJson)
        .map(json -> resolveDocumentsAsJson(json, errorExpression, "Error expression"))
        .filter(
            json ->
                !parseJsonVarsAsTypeOrThrow(json, Map.class, errorExpression, "Error expression")
                    .isEmpty())
        .map(
            json ->
                parseJsonVarsAsTypeOrThrow(
                    json, ConnectorError.class, errorExpression, "Error expression"))
        .filter(
            error -> {
              if (error instanceof BpmnError bpmnError) {
                return bpmnError.hasCode();
              }
              return true;
            });
  }
```

Add the new private helper (place it near `verifyNoForbiddenLiterals`):

```java
  private String resolveDocumentsAsJson(
      final String json, final String expression, final String expressionNameForError) {
    final JsonNode node;
    try {
      node = objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new ConnectorInputException(
          new FeelEngineWrapperException(
              String.format(ERROR_CANNOT_PARSE_VARIABLES, json, Map.class.getName()),
              expression,
              json,
              e));
    }
    final Object resolved = documentResolver.resolve(node);
    try {
      return objectMapper.writeValueAsString(resolved);
    } catch (JsonProcessingException e) {
      throw new ConnectorInputException(
          new FeelEngineWrapperException(
              String.format(
                  "Failed to serialize %s after resolving document references",
                  expressionNameForError),
              expression,
              json,
              e));
    }
  }
```

- [ ] **Step 4: Update `InboundCorrelationHandler` (the other constructor call site in this module)**

Add import:

```java
import io.camunda.connector.api.document.DocumentFactory;
```

Change the constructor:

```java
  public InboundCorrelationHandler(
      CamundaClient camundaClient,
      ObjectMapper objectMapper,
      Duration defaultMessageTtl,
      DocumentFactory documentFactory) {
    this.camundaClient = camundaClient;
    this.objectMapper = objectMapper;
    this.activationConditionEvaluator = new ActivationConditionEvaluator(feelExpressionEvaluator);
    this.defaultMessageTtl = defaultMessageTtl;
    this.connectorResultHandler = new ConnectorResultHandler(objectMapper, documentFactory);
  }
```

- [ ] **Step 5: Write the failing inbound end-to-end test**

In `connector-runtime/connector-runtime-core/src/test/java/io/camunda/connector/runtime/core/inbound/correlation/InboundCorrelationHandlerTest.java`, update the `@BeforeEach` construction:

```java
  @BeforeEach
  public void initMock() {
    camundaClient = mock(CamundaClient.class);
    handler =
        new InboundCorrelationHandler(
            camundaClient,
            TestObjectMapperSupplier.INSTANCE,
            DEFAULT_TTL,
            new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE));
  }
```

Add imports:

```java
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.util.Base64;
```

Add a new test, following the exact existing convention (see `noResultVar_resultExprProvided_shouldExtractVariables` in the same file for the pattern this mirrors):

```java
  @Test
  void noResultVar_resultExprWithCreateDocument_shouldProduceRealDocumentReference() {
    // given — proves the Webhook connector (and any other inbound connector) inherits
    // createDocument() for free through this same shared correlation path, with no
    // webhook-specific code (see ADR-0006)
    var point = new StartEventCorrelationPoint("process1", 0, 0);
    var element = mock(InboundConnectorElement.class);
    when(element.correlationPoint()).thenReturn(point);
    String base64 = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
    when(element.resultExpression()).thenReturn("={myDoc: createDocument(\"" + base64 + "\")}");
    when(element.element())
        .thenReturn(new ProcessElementWithRuntimeData("process1", 0, 0, "element", "default"));

    var dummyCommand = spy(new CreateCommandDummy());
    when(camundaClient.newCreateInstanceCommand()).thenReturn(dummyCommand);

    // when
    handler.correlate(List.of(element), Map.of());

    // then
    var argumentsCaptured = ArgumentCaptor.forClass(Map.class);
    verify(dummyCommand).variables((Map<String, String>) argumentsCaptured.capture());

    @SuppressWarnings("unchecked")
    Map<String, Object> documentReference =
        (Map<String, Object>) argumentsCaptured.getValue().get("myDoc");
    assertThat(documentReference).containsEntry("camunda.document.type", "camunda");
  }
```

- [ ] **Step 6: Run all tests in the module to verify they pass**

Run: `mvn test -pl connector-runtime/connector-runtime-core -Dtest=ConnectorResultHandlerTest,ResultDocumentResolverTest,InboundCorrelationHandlerTest`
Expected: PASS — the whole module now compiles cleanly and every test (pre-existing and new) passes.

- [ ] **Step 7: Commit**

```bash
git add connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/ConnectorResultHandler.java
git add connector-runtime/connector-runtime-core/src/test/java/io/camunda/connector/runtime/core/ConnectorResultHandlerTest.java
git add connector-runtime/connector-runtime-core/src/main/java/io/camunda/connector/runtime/core/inbound/correlation/InboundCorrelationHandler.java
git add connector-runtime/connector-runtime-core/src/test/java/io/camunda/connector/runtime/core/inbound/correlation/InboundCorrelationHandlerTest.java
git commit -m "feat(runtime-core): resolve createDocument sentinels in ConnectorResultHandler (#4715)"
```

---

## Task 4: Outbound wiring (REST connector path)

**Files:**
- Modify: `connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/outbound/job/SpringConnectorJobHandler.java`
- Modify: `connector-runtime/connector-runtime-spring/src/test/java/io/camunda/connector/runtime/outbound/job/SpringConnectorJobHandlerTest.java`

**Interfaces:**
- Consumes: `ConnectorResultHandler(ObjectMapper, DocumentFactory)` (Task 3).

- [ ] **Step 1: Update `SpringConnectorJobHandler`**

In the constructor body (around line 137), change:

```java
    this.connectorResultHandler = new ConnectorResultHandler(objectMapper);
```

to:

```java
    this.connectorResultHandler = new ConnectorResultHandler(objectMapper, documentFactory);
```

(`documentFactory` is already an existing constructor parameter/field on this class — no signature change here, and no test call sites need updating for this class itself, since its own public constructor is unchanged.)

- [ ] **Step 2: Write the failing outbound end-to-end test**

In `connector-runtime/connector-runtime-spring/src/test/java/io/camunda/connector/runtime/outbound/job/SpringConnectorJobHandlerTest.java`, add a new private helper next to the existing `newConnectorJobHandler` overloads:

```java
  private SpringConnectorJobHandler newConnectorJobHandlerWithDocumentFactory(
      OutboundConnectorFunction call, DocumentFactory documentFactory) {
    var metricsRecorder = new MicrometerMetricsRecorder(new SimpleMeterRegistry());
    return new SpringConnectorJobHandler(
        metricsRecorder,
        new JobCallbackCommandWrapperFactory(
            BackoffSupplier.newBackoffBuilder().build(), commandScheduler, metricsRecorder),
        new SecretProviderAggregator(List.of(new FooBarSecretProvider())),
        new DefaultValidationProvider(),
        documentFactory,
        TestObjectMapperSupplier.INSTANCE,
        call,
        job -> SecretFilter.allowAll());
  }
```

Add imports:

```java
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.util.Base64;
```

Add a new nested test class alongside the existing `@Nested` ones (e.g. `DocumentReturnTests`, `OutputTests`):

```java
  @Nested
  class CreateDocumentTests {

    @Test
    void resultExpressionCreateDocumentProducesRealDocumentInOutputVariables() throws Exception {
      // given a connector whose response embeds a base64-encoded file inside its JSON body,
      // mirroring the shape from https://github.com/camunda/connectors/issues/4715
      String base64 =
          Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
      var jobHandler =
          newConnectorJobHandlerWithDocumentFactory(
              (context) -> Map.of("body", Map.of("file", base64)),
              new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE));

      // when the result expression extracts just that field via createDocument
      var result =
          JobBuilder.create()
              .withResultExpressionHeader("={myDoc: createDocument(response.body.file)}")
              .executeAndCaptureResult(jobHandler);

      // then the output variable holds a real document reference, not the raw base64 string
      @SuppressWarnings("unchecked")
      Map<String, Object> documentReference =
          (Map<String, Object>) result.getVariables().get("myDoc");
      assertThat(documentReference).containsEntry("camunda.document.type", "camunda");
    }
  }
```

- [ ] **Step 3: Run test to verify it fails first (before Step 1's fix), then passes after**

Run: `mvn test -pl connector-runtime/connector-runtime-spring -Dtest=SpringConnectorJobHandlerTest#resultExpressionCreateDocumentProducesRealDocumentInOutputVariables`
Expected before Step 1 lands: FAIL (raw base64 string, not a document reference, would be returned) — this class only fails meaningfully once Task 3 is compiled in, so if run strictly in isolation before Step 1, expect a `NullPointerException`/`ClassCastException` from casting the raw string to `Map`, confirming the resolver isn't wired yet. After Step 1: PASS.

- [ ] **Step 4: Run the full existing test class to confirm no regressions**

Run: `mvn test -pl connector-runtime/connector-runtime-spring -Dtest=SpringConnectorJobHandlerTest`
Expected: PASS — every pre-existing test keeps passing because none of their result expressions call `createDocument`, so the new resolution step is a no-op for them.

- [ ] **Step 5: Commit**

```bash
git add connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/outbound/job/SpringConnectorJobHandler.java
git add connector-runtime/connector-runtime-spring/src/test/java/io/camunda/connector/runtime/outbound/job/SpringConnectorJobHandlerTest.java
git commit -m "feat(runtime-spring): wire DocumentFactory into outbound result expressions (#4715)"
```

---

## Task 5: Inbound Spring wiring (Webhook connector inherits it)

Task 3 already updated `InboundCorrelationHandler` itself (same module as `ConnectorResultHandler`) and proved end-to-end that `createDocument()` works through the inbound correlation path. This task finishes threading a `DocumentFactory` through the `connector-runtime-spring` layer that constructs `InboundCorrelationHandler` in production (`MeteredInboundCorrelationHandler` → `InboundCorrelationConfiguration` → `InboundConnectorRuntimeConfiguration`), which is currently broken at compile time as a direct result of Task 3's constructor change.

**Files:**
- Modify: `connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/inbound/MeteredInboundCorrelationHandler.java`
- Modify: `connector-runtime/connector-runtime-spring/src/test/java/io/camunda/connector/runtime/inbound/MeteredInboundCorrelationHandlerTest.java`
- Modify: `connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/inbound/InboundCorrelationConfiguration.java`
- Modify: `connector-runtime/connector-runtime-spring/src/test/java/io/camunda/connector/runtime/inbound/InboundCorrelationConfigurationTest.java`
- Modify: `connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/inbound/InboundConnectorRuntimeConfiguration.java`

**Interfaces:**
- Consumes: `InboundCorrelationHandler(CamundaClient, ObjectMapper, Duration, DocumentFactory)` (Task 3).

- [ ] **Step 1: Confirm the current compile break**

Run: `mvn compile -pl connector-runtime/connector-runtime-spring -am`
Expected: FAIL — `MeteredInboundCorrelationHandler.java` doesn't compile (`super(camundaClient, objectMapper, messageTtl)` no longer matches `InboundCorrelationHandler`'s 4-arg constructor from Task 3). This confirms the starting point for this task.

- [ ] **Step 2: Fix the remaining production compile breaks — `MeteredInboundCorrelationHandler`**

In `connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/inbound/MeteredInboundCorrelationHandler.java`, add import:

```java
import io.camunda.connector.api.document.DocumentFactory;
```

Change the constructor:

```java
  public MeteredInboundCorrelationHandler(
      CamundaClient camundaClient,
      ObjectMapper objectMapper,
      Duration messageTtl,
      DocumentFactory documentFactory,
      ConnectorsInboundMetrics connectorsInboundMetrics) {
    super(camundaClient, objectMapper, messageTtl, documentFactory);
    this.connectorsInboundMetrics = connectorsInboundMetrics;
  }
```

- [ ] **Step 3: Fix `InboundCorrelationConfiguration`**

Add import:

```java
import io.camunda.connector.api.document.DocumentFactory;
```

Change the static helper and the `@Bean` method:

```java
  public static Map<String, InboundCorrelationHandler> buildCorrelationHandlersByPhysicalTenantId(
      CamundaClientRegistry registry,
      CamundaClient legacyCamundaClient,
      ObjectMapper objectMapper,
      Duration messageTtl,
      DocumentFactory documentFactory,
      ConnectorsInboundMetrics connectorsInboundMetrics) {
    return registry.clientNames().stream()
        .collect(
            PhysicalTenantIds.toMapByPhysicalTenantId(
                registry,
                legacyCamundaClient,
                name ->
                    new MeteredInboundCorrelationHandler(
                        PhysicalTenantIds.resolveClient(registry, name, legacyCamundaClient),
                        objectMapper,
                        messageTtl,
                        documentFactory,
                        connectorsInboundMetrics)));
  }

  @Bean
  @Lazy
  public InboundCorrelationHandler inboundCorrelationHandler(
      CamundaClientRegistry registry,
      @Autowired(required = false) CamundaClient legacyCamundaClient,
      @ConnectorsObjectMapper ObjectMapper objectMapper,
      DocumentFactory documentFactory,
      ConnectorsInboundMetrics connectorsInboundMetrics) {
    return PhysicalTenantIds.onlyValue(
        buildCorrelationHandlersByPhysicalTenantId(
            registry,
            legacyCamundaClient,
            objectMapper,
            messageTtl,
            documentFactory,
            connectorsInboundMetrics),
        InboundCorrelationHandler.class);
  }
```

- [ ] **Step 4: Fix `InboundConnectorRuntimeConfiguration`'s call site**

In `connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/inbound/InboundConnectorRuntimeConfiguration.java`, the bean method `springInboundConnectorContextFactory` already receives a `DocumentFactory documentFactory` parameter (line 97) — it's just not forwarded yet. Change:

```java
    Map<String, InboundCorrelationHandler> correlationHandlersByPhysicalTenantId =
        InboundCorrelationConfiguration.buildCorrelationHandlersByPhysicalTenantId(
            registry, legacyCamundaClient, mapper, messageTtl, connectorsInboundMetrics);
```

to:

```java
    Map<String, InboundCorrelationHandler> correlationHandlersByPhysicalTenantId =
        InboundCorrelationConfiguration.buildCorrelationHandlersByPhysicalTenantId(
            registry, legacyCamundaClient, mapper, messageTtl, documentFactory,
            connectorsInboundMetrics);
```

- [ ] **Step 5: Fix `MeteredInboundCorrelationHandlerTest`**

In `connector-runtime/connector-runtime-spring/src/test/java/io/camunda/connector/runtime/inbound/MeteredInboundCorrelationHandlerTest.java`, add imports:

```java
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
```

Change the construction in `setUp()`:

```java
  @BeforeEach
  void setUp() {
    camundaClient = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    handler =
        new MeteredInboundCorrelationHandler(
            camundaClient,
            TestObjectMapperSupplier.INSTANCE,
            DEFAULT_TTL,
            new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE),
            metrics);
    element = mock(InboundConnectorElement.class);
  }
```

- [ ] **Step 6: Fix `InboundCorrelationConfigurationTest`**

In `connector-runtime/connector-runtime-spring/src/test/java/io/camunda/connector/runtime/inbound/InboundCorrelationConfigurationTest.java`, add import:

```java
import io.camunda.connector.api.document.DocumentFactory;
```

Update both existing calls, e.g.:

```java
    var result =
        configuration.inboundCorrelationHandler(
            registry,
            null,
            mock(ObjectMapper.class),
            mock(DocumentFactory.class),
            mock(ConnectorsInboundMetrics.class));
```

and the corresponding call in `inboundCorrelationHandler_throwsClearErrorForMultiplePhysicalTenants`:

```java
    assertThatThrownBy(
            () ->
                configuration.inboundCorrelationHandler(
                    registry,
                    null,
                    mock(ObjectMapper.class),
                    mock(DocumentFactory.class),
                    mock(ConnectorsInboundMetrics.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("InboundCorrelationHandler");
```

- [ ] **Step 7: Run all affected tests**

Run: `mvn test -pl connector-runtime/connector-runtime-core,connector-runtime/connector-runtime-spring -Dtest=InboundCorrelationHandlerTest,MeteredInboundCorrelationHandlerTest,InboundCorrelationConfigurationTest,SpringConnectorJobHandlerTest,ConnectorResultHandlerTest`
Expected: PASS — all tests across both modules, including the new `noResultVar_resultExprWithCreateDocument_shouldProduceRealDocumentReference` test.

- [ ] **Step 8: Full module build to catch any other missed call site**

Run: `mvn clean test -pl connector-runtime/connector-runtime-core,connector-runtime/connector-runtime-spring -am -Dquickly`
Expected: BUILD SUCCESS. If any other call site to a changed constructor was missed (e.g. in `connectors/webhook/` tests, or another connector module's test that constructs `InboundCorrelationHandler`/`ConnectorResultHandler` directly), the compiler will name the exact file — fix it the same way (add the `DocumentFactory` argument, using `mock(DocumentFactory.class)` for tests that don't exercise document creation).

- [ ] **Step 9: Commit**

```bash
git add connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/inbound/MeteredInboundCorrelationHandler.java
git add connector-runtime/connector-runtime-spring/src/test/java/io/camunda/connector/runtime/inbound/MeteredInboundCorrelationHandlerTest.java
git add connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/inbound/InboundCorrelationConfiguration.java
git add connector-runtime/connector-runtime-spring/src/test/java/io/camunda/connector/runtime/inbound/InboundCorrelationConfigurationTest.java
git add connector-runtime/connector-runtime-spring/src/main/java/io/camunda/connector/runtime/inbound/InboundConnectorRuntimeConfiguration.java
git commit -m "feat(runtime-spring): wire DocumentFactory through inbound Spring config (#4715)

Webhook (and any other inbound connector) now inherits createDocument()
through the same shared ConnectorResultHandler outbound already uses —
no webhook-specific code needed, per ADR-0006."
```

---

## Final Verification

- [ ] **Step 1: Full repo build for the touched modules**

Run: `mvn clean verify -pl connector-runtime/connector-feel,connector-runtime/connector-runtime-core,connector-runtime/connector-runtime-spring -am -Dquickly`
Expected: BUILD SUCCESS.

- [ ] **Step 2: Confirm no other module references the changed constructors**

Run: `grep -rn "new ConnectorResultHandler(\|new InboundCorrelationHandler(\|new MeteredInboundCorrelationHandler(" --include="*.java" . | grep -v "connector-runtime/connector-runtime-core\|connector-runtime/connector-runtime-spring"`
Expected: no output. If any connector module (e.g. `connectors/webhook/`) directly constructs one of these classes in its own tests, fix it the same way as Task 5 Step 6/7.

- [ ] **Step 3: Manual smoke check of the FEEL expression from the original issue**

Confirm (by reading `ResultDocumentResolverTest` and `ConnectorResultHandlerTest`'s new tests) that both forms from [#4715](https://github.com/camunda/connectors/issues/4715)'s architecture-session discussion are covered:
- `createDocument(response.body.file[1])` — object-with-recognized-keys form (`resolvesObjectArgumentWithNameAndContentType`, `createOutputVariablesResolvesCreateDocumentInResultExpression`).
- `createDocument(response.body.file[1].document.data)` — bare-string form (`resolvesBareStringArgument`, `createDocumentFunctionWithStringArgument`).

---

## Self-Review Notes

**Spec coverage against ADR-0006:**
- Function shape (object-with-recognized-keys / bare-string) — Task 1 (FEEL layer) + Task 2 (resolver interpretation). ✅
- Defaults (random UUID filename, `MimeTypeResolver`-based content-type) — Task 2, `generatesRandomFileNameAndDefaultsContentTypeWhenOmitted`. ✅
- Eager resolution in both `createOutputVariables` and `examineErrorExpression` — Task 3. ✅
- New discriminator distinct from `IntrinsicFunctionModel.DISCRIMINATOR_KEY`, and the existing `FORBIDDEN_LITERALS` check is untouched — Task 3 Step 3 leaves `FORBIDDEN_LITERALS`/`verifyNoForbiddenLiterals` exactly as-is; the new sentinel resolution happens as an *additional* step after that check runs, on the intrinsic-function discriminator only. ✅
- Outbound wiring (REST) — Task 4. ✅
- Inbound wiring (Webhook inherits for free, no webhook-specific code) — Task 5, proven by a test at the `InboundCorrelationHandler` level (the class Webhook's runtime routes through) rather than a webhook-specific test file, matching the ADR's explicit "no dedicated webhook code" decision. ✅
- No `createDocuments` (plural) — not implemented anywhere in this plan, matching ADR-0006. ✅
- `storeResponse` toggle untouched — no task in this plan touches `HttpCommonRequest`, `HttpService`, or `DocumentReturnProcessor`. ✅

**No placeholders:** every step above contains complete, compilable code (verified constructor signatures, import paths, and field names by reading the actual current source of every file touched, plus tracing the exact Spring bean wiring for `DocumentFactory` availability at each new call site).

**Type consistency check:** `ResultDocumentResolver.resolve(JsonNode): Object` (Task 2) is called identically in `ConnectorResultHandler.resolveDocumentsAsJson` (Task 3). `FeelConnectorFunctionProvider.RESULT_FUNCTION_TYPE_PROPERTY`/`CREATE_DOCUMENT_TYPE_VALUE` (Task 1) are the exact strings `ResultDocumentResolver.isCreateDocumentSentinel` (Task 2) checks against — no duplicated literal discriminator strings anywhere. `ConnectorResultHandler`'s new 2-arg constructor (Task 3) is the exact signature both `SpringConnectorJobHandler` (Task 4) and `InboundCorrelationHandler` (Task 5) call.
