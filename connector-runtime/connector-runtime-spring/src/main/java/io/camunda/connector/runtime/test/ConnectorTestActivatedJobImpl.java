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
package io.camunda.connector.runtime.test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ConnectorTestActivatedJobImpl implements ActivatedJob {

  public static FeelEngineWrapper FEEL_ENGINE_WRAPPER = new FeelEngineWrapper();

  @JsonIgnore private final JsonMapper jsonMapper;

  private final long key;
  private final String type;
  private final Map<String, String> customHeaders;
  private final long processInstanceKey;
  private final String bpmnProcessId;
  private final int processDefinitionVersion;
  private final long processDefinitionKey;
  private final String elementId;
  private final long elementInstanceKey;
  private final String tenantId;
  private final String worker;
  private final int retries;
  private final long deadline;
  private final String variables;

  private Map<String, Object> variablesAsMap;

  public ConnectorTestActivatedJobImpl(
      final JsonMapper jsonMapper, final ConnectorTestRq connectorTestRq) {
    this.jsonMapper = jsonMapper;

    var variables = resolveVariables(connectorTestRq, this.jsonMapper);

    key = 123l;
    type = connectorTestRq.type(); // "io.camunda:http-json:1";

    final String customHeaders = connectorTestRq.customHeaders(); // "{\"retryBackoff\":\"PT0S\"}";
    this.customHeaders =
        customHeaders.isEmpty() ? new HashMap<>() : jsonMapper.fromJsonAsStringMap(customHeaders);
    worker = "HTTP REST";
    retries = 3;
    deadline = 123l;
    this.variables =
        variables; // "{\"url\":\"https://abc.com/def\",\"method\":\"GET\",\"authentication\":{\"type\":\"noAuth\"},\"readTimeoutInSeconds\":\"20\",\"connectionTimeoutInSeconds\":\"20\"}";
    processInstanceKey = 123l;
    bpmnProcessId = "job.getBpmnProcessId()";
    processDefinitionVersion = 1;
    processDefinitionKey = 123l;
    elementId = "job.getElementId()";
    elementInstanceKey = 123l;
    tenantId = "job.getTenantId()";
  }

  private String resolveVariables(
      final ConnectorTestRq connectorTestRq, final JsonMapper jsonMapper) {
    var contextAsMap =
        Optional.ofNullable(connectorTestRq.context())
            .filter(context -> !connectorTestRq.context().isBlank())
            .map(context -> jsonMapper.fromJsonAsMap(context))
            .orElse(Collections.emptyMap());
    var variablesMap = jsonMapper.fromJsonAsMap(connectorTestRq.variables());
    evaluateAllFeelStringsInMap(variablesMap, contextAsMap);
    return jsonMapper.toJson(variablesMap);
  }

  public void evaluateAllFeelStringsInMap(
      Map<String, Object> variablesMap, Map<String, Object> contextAsMap) {
    for (Map.Entry<String, Object> entry : variablesMap.entrySet()) {
      Object value = entry.getValue();
      if (value instanceof String && ((String) entry.getValue()).startsWith("=")) {
        var newValue = FEEL_ENGINE_WRAPPER.evaluateToJson((String) entry.getValue(), contextAsMap);
        entry.setValue(newValue);
      } else if (value instanceof Map) {
        evaluateAllFeelStringsInMap((Map<String, Object>) value, contextAsMap);
      }
    }
  }

  @Override
  public long getKey() {
    return key;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public Map<String, String> getCustomHeaders() {
    return customHeaders;
  }

  @Override
  public String getWorker() {
    return worker;
  }

  @Override
  public int getRetries() {
    return retries;
  }

  @Override
  public long getDeadline() {
    return deadline;
  }

  @Override
  public String getVariables() {
    return variables;
  }

  @Override
  public Map<String, Object> getVariablesAsMap() {
    if (variablesAsMap == null) {
      variablesAsMap = jsonMapper.fromJsonAsMap(variables);
    }
    return variablesAsMap;
  }

  @Override
  public <T> T getVariablesAsType(final Class<T> variableType) {
    return jsonMapper.fromJson(variables, variableType);
  }

  @Override
  public Object getVariable(final String name) {
    final Map<String, Object> variables = getVariablesAsMap();
    if (!variables.containsKey(name)) {
      throw new ClientException(String.format("The variable %s is not available", name));
    }
    return getVariablesAsMap().get(name);
  }

  @Override
  public String toJson() {
    return jsonMapper.toJson(this);
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public String toString() {
    return ""; // return toJson();
  }
}
