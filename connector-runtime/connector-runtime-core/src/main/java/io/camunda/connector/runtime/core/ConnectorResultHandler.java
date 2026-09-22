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
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.document.jackson.IntrinsicFunctionModel;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.feel.LocalFeelExpressionEvaluator;
import io.camunda.connector.runtime.core.error.BpmnError;
import io.camunda.connector.runtime.core.error.ConnectorError;
import io.camunda.connector.runtime.core.outbound.ErrorExpressionJobContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ConnectorResultHandler {

  private static final String ERROR_CANNOT_PARSE_VARIABLES = "Cannot parse '%s' as '%s'.";

  private final FeelExpressionEvaluator feelExpressionEvaluator =
      new LocalFeelExpressionEvaluator();
  private final ObjectMapper objectMapper;

  public ConnectorResultHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * @return a map with output process variables for a given response from an {@link
   *     OutboundConnectorFunction} or an {@link InboundConnectorExecutable}. configured with
   *     headers from a Zeebe Job or inbound Connector properties.
   */
  public Map<String, Object> createOutputVariables(
      final Object responseContent,
      final String resultVariableName,
      final String resultExpression) {
    final Map<String, Object> outputVariables = new HashMap<>();

    if (isNotBlank(resultVariableName)) {
      verifyResultVariableHasNoForbiddenLiterals(responseContent);
      outputVariables.put(resultVariableName, responseContent);
    }

    if (isNotBlank(resultExpression)) {
      var mappedResponseJson =
          feelExpressionEvaluator.evaluateToJson(
              resultExpression, responseContent, wrapResponse(responseContent));
      if (mappedResponseJson != null) {
        verifyNoForbiddenLiterals(mappedResponseJson);
        var mappedResponse =
            parseJsonVarsAsTypeOrThrow(
                mappedResponseJson, Map.class, resultExpression, "Result expression");
        if (mappedResponse != null) {
          outputVariables.putAll(mappedResponse);
        }
      }
    }
    return outputVariables;
  }

  public Optional<ConnectorError> examineErrorExpression(
      final Object responseContent,
      final Map<String, String> jobHeaders,
      ErrorExpressionJobContext jobContext) {
    final var errorExpression = jobHeaders.get(Keywords.ERROR_EXPRESSION_KEYWORD);
    return Optional.ofNullable(errorExpression)
        .filter(s -> !s.isBlank())
        .map(
            expression ->
                feelExpressionEvaluator.evaluateToJson(
                    expression, responseContent, wrapResponse(responseContent), jobContext))
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

  private <T> T parseJsonVarsAsTypeOrThrow(
      final String jsonVars,
      Class<T> type,
      final String expression,
      final String expressionNameForError) {
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
      return objectMapper.readValue(jsonVars, type);
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
    }
  }

  /**
   * Named distinctly from {@link #verifyNoForbiddenLiterals(String)} rather than overloading it —
   * both take unrelated static types at their one respective call site each, but CodeQL flags an
   * {@code Object}/{@code String} overload pair as confusable overloading regardless, so a distinct
   * name is clearer for a reader too, not just quieter for the scanner.
   *
   * <p>Serializes with {@link #objectMapper}, which registers the document module, so a resolved
   * {@link io.camunda.connector.api.document.Document} in {@code responseContent} doesn't silently
   * serialize as {@code {}} and hide a forbidden literal nested under it.
   */
  private void verifyResultVariableHasNoForbiddenLiterals(Object responseContent) {
    try {
      verifyNoForbiddenLiterals(objectMapper.writeValueAsString(responseContent));
    } catch (JsonProcessingException e) {
      throw new ConnectorInputException(
          new FeelEngineWrapperException(
              "Failed to serialize the connector result to verify it contains no forbidden"
                  + " literals.",
              null,
              String.valueOf(responseContent),
              e));
    }
  }

  /**
   * A substring search over the serialized text would flag any string <em>value</em> that happens
   * to contain {@code camunda.function.type} too — a benign response body like {@code
   * {"message":"camunda.function.type"}} has no discriminator object anywhere in it, but would
   * still fail the job. Walking the parsed tree for the discriminator as an actual object key
   * (mirroring {@link io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionUtil}'s own
   * bound-tree walk) rejects only what could actually reach the live intrinsic-function executor.
   */
  private void verifyNoForbiddenLiterals(String json) {
    JsonNode tree;
    try {
      tree = objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new ConnectorInputException(
          new FeelEngineWrapperException(
              "Failed to parse the connector result to verify it contains no forbidden literals.",
              null,
              json,
              e));
    }
    if (containsForbiddenDiscriminatorKey(tree)) {
      throw new ConnectorInputException(
          new FeelEngineWrapperException(
              String.format(
                  "The connector result contains a forbidden literal '%s'.",
                  IntrinsicFunctionModel.DISCRIMINATOR_KEY),
              IntrinsicFunctionModel.DISCRIMINATOR_KEY,
              json));
    }
  }

  private static boolean containsForbiddenDiscriminatorKey(JsonNode node) {
    if (node.isObject()) {
      if (node.has(IntrinsicFunctionModel.DISCRIMINATOR_KEY)) {
        return true;
      }
      for (JsonNode child : node) {
        if (containsForbiddenDiscriminatorKey(child)) {
          return true;
        }
      }
      return false;
    }
    if (node.isArray()) {
      for (JsonNode child : node) {
        if (containsForbiddenDiscriminatorKey(child)) {
          return true;
        }
      }
    }
    return false;
  }
}
