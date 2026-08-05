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

import static io.camunda.connector.feel.FeelEngineWrapperUtil.wrapResponse;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.document.jackson.IntrinsicFunctionModel;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.feel.FeelConnectorFunctionProvider;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.feel.LocalFeelExpressionEvaluator;
import io.camunda.connector.runtime.core.document.ResultDocumentResolver;
import io.camunda.connector.runtime.core.error.BpmnError;
import io.camunda.connector.runtime.core.error.ConnectorError;
import io.camunda.connector.runtime.core.outbound.ErrorExpressionJobContext;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public class ConnectorResultHandler {

  private static final String ERROR_CANNOT_PARSE_VARIABLES = "Cannot parse '%s' as '%s'.";
  public static List<String> FORBIDDEN_LITERALS = List.of(IntrinsicFunctionModel.DISCRIMINATOR_KEY);

  private final FeelExpressionEvaluator feelExpressionEvaluator =
      new LocalFeelExpressionEvaluator();
  private final ObjectMapper objectMapper;
  private final ObjectMapper documentSerializingObjectMapper;
  private final ResultDocumentResolver documentResolver;

  /**
   * @param objectMapper used for everything except serializing a resolved {@link
   *     io.camunda.connector.api.document.Document} back to JSON, which instead uses a private
   *     {@code .copy()} with {@code JacksonModuleDocumentSerializer} registered on it: a caller
   *     that omitted that module on {@code objectMapper} would otherwise not fail loudly — since
   *     {@code ConnectorsObjectMapperSupplier} disables {@code FAIL_ON_EMPTY_BEANS} — but instead
   *     silently serialize the just-created {@code Document} as {@code {}}, uploading it and then
   *     losing the only reference to it. Copying rather than mutating {@code objectMapper} in place
   *     avoids surprising callers who share that instance for unrelated serialization. Falls back
   *     to {@code objectMapper} itself if {@code copy()} returns {@code null} — a real {@code
   *     ObjectMapper} never does this, but a test double (e.g. a bare {@code
   *     mock(ObjectMapper.class)} with no stubbing) can, and such callers are by construction not
   *     relying on real Document serialization anyway.
   */
  public ConnectorResultHandler(ObjectMapper objectMapper, DocumentFactory documentFactory) {
    this.objectMapper = objectMapper;
    ObjectMapper copy = objectMapper.copy();
    this.documentSerializingObjectMapper =
        copy != null ? copy.registerModule(new JacksonModuleDocumentSerializer()) : objectMapper;
    this.documentResolver = new ResultDocumentResolver(documentFactory);
  }

  /**
   * Preserves source/binary compatibility for callers compiled against the pre-{@code
   * createDocument()} single-argument constructor. {@code createDocument()} is unavailable through
   * this instance: {@link ResultDocumentResolver} rejects it with a clear error instead of a bare
   * {@code NullPointerException}, since no {@link DocumentFactory} was supplied.
   */
  public ConnectorResultHandler(ObjectMapper objectMapper) {
    this(objectMapper, null);
  }

  /**
   * @return a map with output process variables for a given response from an {@link
   *     OutboundConnectorFunction} or an {@link InboundConnectorExecutable}. configured with
   *     headers from a Zeebe Job or inbound Connector properties.
   */
  public Map<String, Object> createOutputVariables(
      final Object responseContent,
      final @Nullable String resultVariableName,
      final @Nullable String resultExpression,
      final @Nullable String physicalTenantId) {
    final Map<String, Object> outputVariables = new HashMap<>();

    if (isNotBlank(resultVariableName)) {
      outputVariables.put(resultVariableName, responseContent);
    }

    if (isNotBlank(resultExpression)) {
      FeelConnectorFunctionProvider.beginCreateDocumentEvaluationScope();
      try {
        var mappedResponseJson =
            evaluateToJsonOrThrow(
                resultExpression,
                "Result expression",
                responseContent,
                wrapResponse(responseContent));
        if (mappedResponseJson != null) {
          verifyNoForbiddenLiterals(mappedResponseJson);
          var resolvedResponseJson =
              resolveDocumentsAsJson(
                  mappedResponseJson, resultExpression, "Result expression", physicalTenantId);
          var mappedResponse =
              parseJsonVarsAsTypeOrThrow(
                  resolvedResponseJson,
                  Map.class,
                  resultExpression,
                  "Result expression",
                  physicalTenantId);
          if (mappedResponse != null) {
            outputVariables.putAll(mappedResponse);
          }
        }
      } finally {
        FeelConnectorFunctionProvider.endCreateDocumentEvaluationScope();
      }
    }
    return outputVariables;
  }

  public Optional<ConnectorError> examineErrorExpression(
      final Object responseContent,
      final Map<String, String> jobHeaders,
      ErrorExpressionJobContext jobContext,
      final @Nullable String physicalTenantId) {
    final var errorExpression = jobHeaders.get(Keywords.ERROR_EXPRESSION_KEYWORD);
    if (errorExpression == null || errorExpression.isBlank()) {
      return Optional.empty();
    }
    // errorExpression is @NonNull below (NullAway flow narrowing)
    FeelConnectorFunctionProvider.beginCreateDocumentEvaluationScope();
    try {
      var evaluatedJson =
          evaluateToJsonOrThrow(
              errorExpression,
              "Error expression",
              responseContent,
              wrapResponse(responseContent),
              jobContext);
      if (evaluatedJson != null) {
        verifyNoForbiddenLiterals(evaluatedJson);
      }
      // The !isEmpty() filter below runs on the json BEFORE document resolution: an error
      // expression that evaluates to {} is filtered out ("not actually an error"), and resolving
      // documents afterward would have been wasted work — worse, a createDocument() sentinel
      // resolves to a real Document regardless of what happens to the map around it, so creating
      // it before this filter could orphan a document for an error expression result that's about
      // to be discarded entirely.
      return Optional.ofNullable(evaluatedJson)
          .filter(
              json ->
                  !parseJsonVarsAsTypeOrThrow(
                          json, Map.class, errorExpression, "Error expression", physicalTenantId)
                      .isEmpty())
          .map(
              json ->
                  resolveDocumentsAsJson(
                      json, errorExpression, "Error expression", physicalTenantId))
          .map(
              json ->
                  parseJsonVarsAsTypeOrThrow(
                      json,
                      ConnectorError.class,
                      errorExpression,
                      "Error expression",
                      physicalTenantId))
          .filter(
              error -> {
                if (error instanceof BpmnError bpmnError) {
                  return bpmnError.hasCode();
                }
                return true;
              });
    } finally {
      FeelConnectorFunctionProvider.endCreateDocumentEvaluationScope();
    }
  }

  /**
   * Evaluates a FEEL expression to JSON, re-throwing a {@link FeelEngineWrapperException} as a
   * {@link ConnectorInputException} naming which expression ({@code expressionNameForError}) failed
   * — otherwise the failure surfaces as an incident with no indication of which header or property
   * caused it.
   */
  private String evaluateToJsonOrThrow(
      final String expression, final String expressionNameForError, final Object... variables) {
    try {
      return feelExpressionEvaluator.evaluateToJson(expression, variables);
    } catch (FeelEngineWrapperException e) {
      throw new ConnectorInputException(
          "%s could not be evaluated: %s".formatted(expressionNameForError, e.getReason()), e);
    }
  }

  private <T> T parseJsonVarsAsTypeOrThrow(
      final String jsonVars,
      Class<T> type,
      final String expression,
      final String expressionNameForError,
      final @Nullable String physicalTenantId) {
    try {
      // When expecting a Map (from a FEEL evaluation), check if it's actually a JSON object
      if (type.equals(Map.class)) {
        JsonNode node = objectMapper.readTree(jsonVars);
        if (!node.isObject()) {
          throw new ConnectorInputException(
              new FeelEngineWrapperException(
                  String.format(
                      "%s must return a JSON object, but got %s. Evaluated value: %s",
                      expressionNameForError, node.getNodeType().name().toLowerCase(), jsonVars),
                  expression,
                  jsonVars));
        }
      }
      return objectMapper
          .reader()
          .withAttribute(DocumentFactory.PHYSICAL_TENANT_ID_ATTRIBUTE, physicalTenantId)
          .readValue(jsonVars, type);
    } catch (ConnectorInputException e) {
      // Re-throw our custom exception
      throw e;
    } catch (JsonProcessingException e) {
      // For other types (like ConnectorError), keep the original message
      throw new ConnectorInputException(
          new FeelEngineWrapperException(
              String.format(ERROR_CANNOT_PARSE_VARIABLES, jsonVars, type.getName()),
              expression,
              jsonVars,
              e));
    } catch (IOException e) {
      // ObjectReader#readValue declares the broader IOException (unlike ObjectMapper#readValue's
      // JsonProcessingException), though in practice this path only ever throws for JSON-parsing
      // reasons already covered above; kept for exhaustiveness.
      throw new ConnectorInputException(
          new FeelEngineWrapperException(
              String.format(ERROR_CANNOT_PARSE_VARIABLES, jsonVars, type.getName()),
              expression,
              jsonVars,
              e));
    }
  }

  private String resolveDocumentsAsJson(
      final String json,
      final String expression,
      final String expressionNameForError,
      final @Nullable String physicalTenantId) {
    // Fast path: skip the parse/walk/reserialize round-trip entirely when the createDocument
    // sentinel isn't present anywhere in the JSON. This is the common case (createDocument not
    // used) and avoids both a performance cost and unconditional numeric precision loss (see
    // ResultDocumentResolver#scalarValue) on every connector result/error expression. It's also
    // what keeps FeelConnectorFunctionProvider#currentCreateDocumentNonce() from generating a UUID
    // on every evaluation regardless of whether createDocument() was used.
    if (!json.contains(FeelConnectorFunctionProvider.RESULT_FUNCTION_TYPE_PROPERTY)) {
      return json;
    }
    final String expectedNonce = FeelConnectorFunctionProvider.currentCreateDocumentNonce();
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
    // Reject a wrong-shaped root before ever invoking the factory: a result/error expression must
    // ultimately parse as a JSON object (see parseJsonVarsAsTypeOrThrow's own Map-shape check), so
    // e.g. `=[{d: createDocument("...")}]` (an array at the root) would otherwise have its
    // document created here and only THEN fail that later shape check — uploading a document that
    // can never be referenced from a rejected result.
    if (!node.isObject()) {
      throw new ConnectorInputException(
          new FeelEngineWrapperException(
              String.format(
                  "%s must return a JSON object, but got %s. Evaluated value: %s",
                  expressionNameForError, node.getNodeType().name().toLowerCase(), json),
              expression,
              json));
    }
    // Reject a root-level createDocument(...) call before ever invoking the factory: if the whole
    // expression IS the sentinel (e.g. `=createDocument("...")`, no wrapping object), resolving it
    // would either spread the Document's own reference fields into unrelated top-level output
    // variables (result expression) or upload a document only to then fail to parse as a
    // ConnectorError (error expression) — in both cases surprising the user instead of failing
    // clearly, and in the error case only after the document was already created.
    if (documentResolver.isCreateDocumentSentinel(node, expectedNonce)) {
      throw new ConnectorInputException(
          new FeelEngineWrapperException(
              String.format(
                  "%s must not be a bare createDocument(...) call — wrap it inside a field, e.g."
                      + " {myDocument: createDocument(...)}",
                  expressionNameForError),
              expression,
              json));
    }
    final Object resolved = documentResolver.resolve(node, physicalTenantId, expectedNonce);
    try {
      return documentSerializingObjectMapper.writeValueAsString(resolved);
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

  private void verifyNoForbiddenLiterals(String json) {
    FORBIDDEN_LITERALS.forEach(
        literal -> {
          if (json.contains(literal)) {
            throw new ConnectorInputException(
                new FeelEngineWrapperException(
                    String.format(
                        "The connector result contains a forbidden literal '%s'.", literal),
                    literal,
                    json));
          }
        });
  }
}
