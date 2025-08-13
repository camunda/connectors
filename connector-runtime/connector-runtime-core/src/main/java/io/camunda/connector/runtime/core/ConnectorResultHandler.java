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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.document.jackson.IntrinsicFunctionModel;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.runtime.core.error.BpmnError;
import io.camunda.connector.runtime.core.error.ConnectorError;
import io.camunda.connector.runtime.core.outbound.ErrorExpressionJobContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ConnectorResultHandler {

  private static final String ERROR_CANNOT_PARSE_VARIABLES = "Cannot parse '%s' as '%s'.";
  public static List<String> FORBIDDEN_LITERALS = List.of(IntrinsicFunctionModel.DISCRIMINATOR_KEY);

  private FeelEngineWrapper feelEngineWrapper = new FeelEngineWrapper();
  private ObjectMapper objectMapper;

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
      outputVariables.put(resultVariableName, responseContent);
    }

    if (isNotBlank(resultExpression)) {
      var mappedResponseJson =
          feelEngineWrapper.evaluateToJson(
              resultExpression, responseContent, wrapResponse(responseContent));
      if (mappedResponseJson != null) {
        verifyNoForbiddenLiterals(mappedResponseJson);
        var mappedResponse =
            parseJsonVarsAsTypeOrThrow(mappedResponseJson, Map.class, resultExpression);
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
                feelEngineWrapper.evaluateToJson(
                    expression, responseContent, wrapResponse(responseContent), jobContext))
        .filter(json -> !parseJsonVarsAsTypeOrThrow(json, Map.class, errorExpression).isEmpty())
        .map(json -> parseJsonVarsAsTypeOrThrow(json, ConnectorError.class, errorExpression))
        .filter(
            error -> {
              if (error instanceof BpmnError bpmnError) {
                return bpmnError.hasCode();
              }
              return true;
            });
  }

  private <T> T parseJsonVarsAsTypeOrThrow(
      final String jsonVars, Class<T> type, final String expression) {
    try {
      return objectMapper.readValue(jsonVars, type);
    } catch (JsonProcessingException e) {
      throw new ConnectorInputException(
          new FeelEngineWrapperException(
              String.format(ERROR_CANNOT_PARSE_VARIABLES, jsonVars, type.getName()),
              expression,
              jsonVars,
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
