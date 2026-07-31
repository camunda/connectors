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
package io.camunda.connector.runtime.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.document.jackson.IntrinsicFunctionExecutor;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.feel.FeelConnectorFunctionProvider;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.document.TestDocumentFactory;
import io.camunda.connector.runtime.core.error.BpmnError;
import io.camunda.connector.runtime.core.error.ConnectorError;
import io.camunda.connector.runtime.core.outbound.ErrorExpressionJobContext;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ConnectorResultHandlerTest {

  private final ObjectMapper objectMapper =
      ConnectorsObjectMapperSupplier.getCopy()
          .registerModule(new JacksonModuleDocumentSerializer());
  private final TestDocumentFactory documentFactory = new TestDocumentFactory();
  private final ConnectorResultHandler connectorResultHandler =
      new ConnectorResultHandler(objectMapper, documentFactory);

  @Test
  void feelEngineWrapperTest() {
    final var jsonDeserialized2 =
        Map.of(
            "data",
            List.of(
                Map.of("date", LocalDate.of(2024, 1, 1), "attr", "value1"),
                Map.of("date", LocalDate.of(2024, 2, 1), "attr", "value2")));

    final var actual =
        connectorResultHandler.createOutputVariables(
            jsonDeserialized2,
            null,
            """
                ={
                	res1: data[item.attr = "value1"][1].date,
                	res2: "hallo" + res1,
                	res3: 1 + 2,
                	res4: data[item.date = "2024-02-01"][1].attr,
                	res5: data[date(item.date) = date("2024-02-01")][1].attr,
                	res6: today()
                }
                """,
            null);

    assertThat(actual)
        .contains(
            Map.entry("res1", "2024-01-01"),
            Map.entry("res2", "hallo2024-01-01"),
            Map.entry("res3", 3),
            Map.entry("res4", "value2"),
            Map.entry("res5", "value2"),
            Map.entry("res6", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)));
  }

  @Test
  void ensureCanNotProduceIntrinsicFunction() {
    final String resultExpression =
        """
        {
          "camunda.function.type": myfun,
          "params": ["test"]
        }
        """;
    final Map<String, String> context = Map.of("myfun", "test");
    final var exception =
        assertThrows(
            ConnectorInputException.class,
            () ->
                connectorResultHandler.createOutputVariables(
                    context, null, resultExpression, null));

    assertThat(exception)
        .hasMessageContaining(
            "The connector result contains a forbidden literal 'camunda.function.type'");
  }

  @Test
  void shouldHandleEmptyResponseBody() {
    // given - simulates HTTP response with empty/null body
    final String resultExpression = "={\"status\": response.status}";
    final Object responseContent = null;

    // when - should not throw exception even though responseContent is null
    final var actual =
        connectorResultHandler.createOutputVariables(responseContent, null, resultExpression, null);

    // then - should evaluate successfully with null values
    assertThat(actual).containsEntry("status", null);
  }

  @Test
  void shouldProvideGoodErrorMessage_WhenResultExpressionReturnsArray() {
    // given - result expression that produces an array
    final String resultExpression = "= [1, 2, 3]";
    final Object responseContent = Map.of();

    // when - should throw exception with clear message
    final var exception =
        assertThrows(
            ConnectorInputException.class,
            () ->
                connectorResultHandler.createOutputVariables(
                    responseContent, null, resultExpression, null));

    // then - should indicate that an array was returned and JSON object is expected
    assertThat(exception.getMessage())
        .contains("Result expression must return a JSON object")
        .contains("array")
        .contains("[1,2,3]");
  }

  @Test
  void shouldProvideGoodErrorMessage_WhenResultExpressionReturnsString() {
    // given - result expression that produces a string
    final String resultExpression = "= \"hello\"";
    final Object responseContent = Map.of();

    // when - should throw exception with clear message
    final var exception =
        assertThrows(
            ConnectorInputException.class,
            () ->
                connectorResultHandler.createOutputVariables(
                    responseContent, null, resultExpression, null));

    // then - should indicate that a string was returned and JSON object is expected
    assertThat(exception.getMessage())
        .contains("Result expression must return a JSON object")
        .contains("string")
        .contains("\"hello\"");
  }

  @Test
  void shouldProvideGoodErrorMessage_WhenResultExpressionReturnsNumber() {
    // given - result expression that produces a number
    final String resultExpression = "= 42";
    final Object responseContent = Map.of();

    // when - should throw exception with clear message
    final var exception =
        assertThrows(
            ConnectorInputException.class,
            () ->
                connectorResultHandler.createOutputVariables(
                    responseContent, null, resultExpression, null));

    // then - should indicate that a number was returned and JSON object is expected
    assertThat(exception.getMessage())
        .contains("Result expression must return a JSON object")
        .contains("number")
        .contains("42");
  }

  @Test
  void shouldProvideGoodErrorMessage_WhenResultExpressionReturnsBoolean() {
    // given - result expression that produces a boolean
    final String resultExpression = "= true";
    final Object responseContent = Map.of();

    // when - should throw exception with clear message
    final var exception =
        assertThrows(
            ConnectorInputException.class,
            () ->
                connectorResultHandler.createOutputVariables(
                    responseContent, null, resultExpression, null));

    // then - should indicate that a boolean was returned and JSON object is expected
    assertThat(exception.getMessage())
        .contains("Result expression must return a JSON object")
        .contains("boolean")
        .contains("true");
  }

  @Test
  void ensureErrorExpressionCanNotProduceIntrinsicFunction() {
    // examineErrorExpression previously lacked the same forbidden-literal guard
    // createOutputVariables already had, so an error expression that copies attacker-controlled
    // response data verbatim into its output could smuggle a "camunda.function.type" marker
    // through to the incident's variables, where a document/intrinsic-aware ObjectMapper would
    // execute it during deserialization.
    final Object responseContent =
        Map.of("camunda.function.type", "myfun", "params", List.of("test"));
    final String errorExpression = "=bpmnError(\"CODE\", \"msg\", {leaked: response})";
    final Map<String, String> jobHeaders =
        Map.of(Keywords.ERROR_EXPRESSION_KEYWORD, errorExpression);
    final ErrorExpressionJobContext jobContext =
        new ErrorExpressionJobContext(new ErrorExpressionJobContext.ErrorExpressionJob(3));

    final var exception =
        assertThrows(
            ConnectorInputException.class,
            () ->
                connectorResultHandler.examineErrorExpression(
                    responseContent, jobHeaders, jobContext, null));

    assertThat(exception)
        .hasMessageContaining(
            "The connector result contains a forbidden literal 'camunda.function.type'");
  }

  @Test
  void createOutputVariablesRejectsABareRootLevelCreateDocumentCall() {
    // =createDocument("...") with no wrapping object would otherwise have the resolved
    // Document's own reference fields (camunda.document.type, storeId, ...) parsed as generic
    // top-level output variables instead of one named variable holding the document reference.
    String resultExpression = "=createDocument(\"aGVsbG8=\")";

    final var exception =
        assertThrows(
            ConnectorInputException.class,
            () ->
                connectorResultHandler.createOutputVariables(
                    Map.of(), null, resultExpression, null));

    assertThat(exception).hasMessageContaining("must not be a bare createDocument");
    // the sentinel's discriminator is a deliberately unforgeable per-JVM secret — it must never
    // appear in an exception message, since that message becomes a job incident or HTTP error
    // response any caller who triggers this exact rejection can read.
    assertThat(exception.getMessage())
        .doesNotContain(FeelConnectorFunctionProvider.CREATE_DOCUMENT_TYPE_VALUE);
  }

  @Test
  void examineErrorExpressionRejectsABareRootLevelCreateDocumentCallWithoutCreatingADocument() {
    // On the error path this must be rejected BEFORE the factory is called: a bare
    // createDocument(...) error expression would otherwise upload a document and only then fail
    // to parse as a ConnectorError, orphaning the document it just created.
    var mockFactory = Mockito.mock(DocumentFactory.class);
    var handler = new ConnectorResultHandler(objectMapper, mockFactory);
    String errorExpression = "=createDocument(\"aGVsbG8=\")";
    Map<String, String> jobHeaders = Map.of(Keywords.ERROR_EXPRESSION_KEYWORD, errorExpression);
    ErrorExpressionJobContext jobContext =
        new ErrorExpressionJobContext(new ErrorExpressionJobContext.ErrorExpressionJob(3));

    final var exception =
        assertThrows(
            ConnectorInputException.class,
            () -> handler.examineErrorExpression(Map.of(), jobHeaders, jobContext, null));

    assertThat(exception).hasMessageContaining("must not be a bare createDocument");
    assertThat(exception.getMessage())
        .doesNotContain(FeelConnectorFunctionProvider.CREATE_DOCUMENT_TYPE_VALUE);
    Mockito.verifyNoInteractions(mockFactory);
  }

  @Test
  void forbiddenLiteralRejectionDoesNotLeakTheCreateDocumentSecretEvenWhenBothAppearTogether() {
    // A single expression can produce both a forbidden intrinsic-function literal AND a
    // createDocument() sentinel in the same evaluated tree — verifyNoForbiddenLiterals throws
    // first, but its exception must not embed the discriminator either.
    String resultExpression =
        "={leaked: {\"camunda.function.type\": \"x\", \"params\": []}, myDoc: createDocument(\"aGVsbG8=\")}";

    final var exception =
        assertThrows(
            ConnectorInputException.class,
            () ->
                connectorResultHandler.createOutputVariables(
                    Map.of(), null, resultExpression, null));

    assertThat(exception).hasMessageContaining("The connector result contains a forbidden literal");
    assertThat(exception.getMessage())
        .doesNotContain(FeelConnectorFunctionProvider.CREATE_DOCUMENT_TYPE_VALUE);
  }

  @Test
  void shouldProvideGoodErrorMessage_WhenErrorExpressionReturnsArray() {
    // given - error expression that produces an array (invalid type)
    final Object responseContent = Map.of("status", "error");
    final Map<String, String> jobHeaders = Map.of(Keywords.ERROR_EXPRESSION_KEYWORD, "= [1, 2, 3]");
    // ErrorExpressionJobContext is required as context for FEEL evaluation;
    // the retries count (3) is a dummy value that doesn't affect error message validation
    final ErrorExpressionJobContext jobContext =
        new ErrorExpressionJobContext(new ErrorExpressionJobContext.ErrorExpressionJob(3));

    // when - should throw exception with clear message
    final var exception =
        assertThrows(
            ConnectorInputException.class,
            () ->
                connectorResultHandler.examineErrorExpression(
                    responseContent, jobHeaders, jobContext, null));

    // then - should indicate that an array was returned and "Error expression" is mentioned
    assertThat(exception.getMessage())
        .contains("Error expression must return a JSON object")
        .contains("array")
        .contains("[1,2,3]");
  }

  @Test
  void createOutputVariablesResolvesCreateDocumentInResultExpression() {
    String base64 = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
    String resultExpression =
        "={myDoc: createDocument({content: \"" + base64 + "\", name: \"hello.txt\"})}";

    Map<String, Object> result =
        connectorResultHandler.createOutputVariables(Map.of(), null, resultExpression, null);

    assertThat(result).containsKey("myDoc");
    @SuppressWarnings("unchecked")
    Map<String, Object> documentReference = (Map<String, Object>) result.get("myDoc");
    assertThat(documentReference).containsEntry("camunda.document.type", "camunda");
    assertThat(documentReference).doesNotContainKey("connectorResultFunction");
  }

  @Test
  void createOutputVariablesDoesNotResolveAnInjectedSentinelFromResponseData() {
    // End-to-end injection scenario: a malicious/compromised remote API returns a response body
    // that happens to be shaped exactly like the createDocument() sentinel, guessing the pre-nonce
    // literal discriminator value ("createDocument"). The result expression here is a plain
    // pass-through of the response (no createDocument() call anywhere in the expression itself) —
    // exactly what an ordinary, unsuspecting connector user would write. This must NOT create a
    // document from attacker-controlled bytes: the forged object must survive untouched.
    String attackerBase64 =
        Base64.getEncoder().encodeToString("attacker payload".getBytes(StandardCharsets.UTF_8));
    Map<String, Object> responseContent =
        Map.of(
            "body", Map.of("connectorResultFunction", "createDocument", "value", attackerBase64));
    String resultExpression = "={result: response.body}";

    Map<String, Object> result =
        connectorResultHandler.createOutputVariables(responseContent, null, resultExpression, null);

    @SuppressWarnings("unchecked")
    Map<String, Object> passedThrough = (Map<String, Object>) result.get("result");
    assertThat(passedThrough)
        .containsEntry("connectorResultFunction", "createDocument")
        .containsEntry("value", attackerBase64);
    assertThat(passedThrough.values())
        .noneMatch(v -> v instanceof io.camunda.connector.api.document.Document);
  }

  @Test
  void createOutputVariablesLeavesLargeIntegerUntouchedWhenCreateDocumentIsNotUsed() {
    // Regression test for the fast-path guard in resolveDocumentsAsJson: a result expression
    // that never calls createDocument() must not have its output corrupted by an unconditional
    // parse/walk/reserialize round-trip. Note: FEEL's own number handling (JavaValueMapper)
    // already downcasts any FEEL-evaluated number exceeding Long.MAX_VALUE to a double *before*
    // ConnectorResultHandler ever sees it, so a value that large cannot be used here to
    // distinguish "guard skipped the walk" from "walk happened but no longer corrupts" -- that
    // distinction is covered directly against JsonNode trees in
    // ResultDocumentResolverTest#preservesBigNumberPrecisionForSiblingsOfSentinel. This test
    // instead pins the largest integer FEEL can round-trip exactly (Long.MAX_VALUE) and asserts
    // it survives without corruption.
    String resultExpression = "={bigNumber: " + Long.MAX_VALUE + "}";

    Map<String, Object> result =
        connectorResultHandler.createOutputVariables(Map.of(), null, resultExpression, null);

    assertThat(result.get("bigNumber")).isEqualTo(Long.MAX_VALUE);
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
            new ErrorExpressionJobContext(new ErrorExpressionJobContext.ErrorExpressionJob(3)),
            null);

    assertThat(result).isPresent();
    var bpmnError = (BpmnError) result.get();
    @SuppressWarnings("unchecked")
    Map<String, Object> documentReference =
        (Map<String, Object>) bpmnError.variables().get("myDoc");
    assertThat(documentReference).containsEntry("camunda.document.type", "camunda");
  }

  @Test
  void createOutputVariables_resolvesADocumentInTheResultThroughItsOwnPhysicalTenant() {
    // reproduces a real webhook bug: a Document created via the webhook is echoed back through a
    // result expression BEFORE the process instance is created, using the same map-based
    // (multi-tenant) ObjectMapper the runtime uses for property binding elsewhere. Without the
    // PHYSICAL_TENANT_ID_ATTRIBUTE, DocumentDeserializer.resolveDocumentFactory throws "no
    // physical tenant ID attribute was set", which surfaces as an HTTP 422 and no process
    // instance ever gets created.
    var factoryA = Mockito.mock(DocumentFactory.class);
    var factoryB = Mockito.mock(DocumentFactory.class);
    var expectedDocument = Mockito.mock(Document.class);
    Mockito.when(factoryB.resolve(Mockito.any())).thenReturn(expectedDocument);
    var multiTenantMapper =
        new ObjectMapper()
            .registerModule(
                new JacksonModuleDocumentDeserializer(
                    Map.of("tenant-a", factoryA, "tenant-b", factoryB),
                    Mockito.mock(IntrinsicFunctionExecutor.class),
                    JacksonModuleDocumentDeserializer.DocumentModuleSettings.create()));
    var handler =
        new ConnectorResultHandler(multiTenantMapper, Mockito.mock(DocumentFactory.class));

    var documentReference =
        Map.of(
            "camunda.document.type", "camunda",
            "storeId", "store-1",
            "documentId", "doc-1",
            "contentHash", "hash-1");
    var responseContent = Map.of("document", documentReference);

    var result =
        handler.createOutputVariables(responseContent, null, "={document: document}", "tenant-b");

    assertThat(result.get("document")).isEqualTo(expectedDocument);
    Mockito.verifyNoInteractions(factoryA);
  }
}
