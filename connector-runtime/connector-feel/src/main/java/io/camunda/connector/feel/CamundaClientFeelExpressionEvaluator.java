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
package io.camunda.connector.feel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import java.util.Map;

/**
 * Implementation of {@link FeelExpressionEvaluator} that uses the Camunda cluster for FEEL
 * expression evaluation. This allows access to cluster variables (camunda.vars.env.*) and other
 * cluster-side features.
 */
public class CamundaClientFeelExpressionEvaluator implements FeelExpressionEvaluator {

  private final CamundaClient camundaClient;
  private final ObjectMapper objectMapper;

  /**
   * Creates a new evaluator with a custom ObjectMapper for result conversion.
   *
   * @param camundaClient the CamundaClient instance to use for expression evaluation
   * @param objectMapper the ObjectMapper to use for JSON conversion of the results
   */
  public CamundaClientFeelExpressionEvaluator(
      CamundaClient camundaClient, ObjectMapper objectMapper) {
    this.camundaClient = camundaClient;
    this.objectMapper = objectMapper;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T evaluate(String expression, Object... variables) {
    try {
      return (T) evaluateInternal(expression, variables);
    } catch (Exception e) {
      throw new FeelEngineWrapperException(e.getMessage(), expression, variables, e);
    }
  }

  @Override
  public <T> T evaluate(String expression, Class<T> targetType, Object... variables) {
    var result = evaluateInternal(expression, variables);
    return convertResult(result, targetType, expression, variables);
  }

  @Override
  public <T> T evaluate(String expression, JavaType targetType, Object... variables) {
    var result = evaluateInternal(expression, variables);
    return convertResultWithJavaType(result, targetType, expression, variables);
  }

  @Override
  public String evaluateToJson(String expression, Object... variables) {
    try {
      var result = evaluateInternal(expression, variables);
      if (result == null) {
        return null;
      }
      return objectMapper.writeValueAsString(result);
    } catch (JsonProcessingException e) {
      throw new FeelEngineWrapperException(
          "Failed to serialize FEEL result to JSON", expression, variables, e);
    } catch (Exception e) {
      throw new FeelEngineWrapperException(e.getMessage(), expression, variables, e);
    }
  }

  private Object evaluateInternal(String expression, Object[] variables) {
    var request = camundaClient.newEvaluateExpressionCommand().expression(expression);

    Map<String, Object> mergedVariables = FeelEngineWrapperUtil.mergeMapVariables(variables);
    if (!mergedVariables.isEmpty()) {
      request.variables(mergedVariables);
    }

    var response = request.send().join();
    return response.getResult();
  }

  private <T> T convertResult(
      Object result, Class<T> targetType, String expression, Object[] variables) {
    try {
      if (result == null) {
        return null;
      }
      JsonNode jsonNode = objectMapper.valueToTree(result);
      if (targetType == String.class && jsonNode.isObject()) {
        return targetType.cast(objectMapper.writeValueAsString(jsonNode));
      }
      return objectMapper.treeToValue(jsonNode, targetType);
    } catch (JsonProcessingException e) {
      throw new FeelEngineWrapperException(
          "Failed to convert FEEL result to " + targetType.getName(), expression, variables, e);
    }
  }

  @SuppressWarnings("unchecked")
  private <T> T convertResultWithJavaType(
      Object result, JavaType targetType, String expression, Object[] variables) {
    try {
      if (result == null) {
        return null;
      }
      JsonNode jsonNode = objectMapper.valueToTree(result);
      if (targetType.getRawClass() == String.class && jsonNode.isObject()) {
        return (T) objectMapper.writeValueAsString(jsonNode);
      }
      return objectMapper.treeToValue(jsonNode, targetType);
    } catch (JsonProcessingException e) {
      throw new FeelEngineWrapperException(
          "Failed to convert FEEL result to " + targetType, expression, variables, e);
    }
  }
}
