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
package io.camunda.connector.runtime.core.outbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.AbstractConnectorContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractOutboundConnectorContext extends AbstractConnectorContext
    implements OutboundConnectorContext {

  protected final ObjectMapper objectMapper;

  private final Object variables;
  private JsonNode variablesWithReplacedSecrets = null;

  private String jsonWithSecrets = null;

  public AbstractOutboundConnectorContext(
      final SecretProvider secretProvider,
      final ValidationProvider validationProvider,
      final ObjectMapper objectMapper,
      Object variables) {
    super(secretProvider, validationProvider);
    this.variables = variables;
    this.objectMapper = objectMapper;
  }

  @Override
  public <T> T bindVariables(Class<T> cls) {
    T mappedObject = getVariablesWithReplacedSecrets(cls);
    getValidationProvider().validate(mappedObject);
    return mappedObject;
  }

  @SuppressWarnings("unchecked")
  protected <T> T getVariablesWithReplacedSecrets(Class<T> cls) {
    try {
      if (variablesWithReplacedSecrets == null) {
        variablesWithReplacedSecrets = getVariablesWithReplacedSecretsInternal();
      }
      if (cls == String.class) {
        return (T) objectMapper.writeValueAsString(variablesWithReplacedSecrets);
      }
      return objectMapper.convertValue(variablesWithReplacedSecrets, cls);
    } catch (JsonProcessingException e) {
      throw new ConnectorException("JSON_MAPPING", "Error during json mapping.");
    }
  }

  protected JsonNode getVariablesWithReplacedSecretsInternal() throws JsonProcessingException {
    if (variables instanceof String stringVars) {
      String stringVarsWithSecrets = getSecretHandler().replaceSecrets(stringVars);
      return objectMapper.readTree(stringVarsWithSecrets);
    }
    JsonNode convertedVars = objectMapper.valueToTree(variables);
    return replaceSecretsViaReflection(convertedVars);
  }

  private JsonNode replaceSecretsViaReflection(JsonNode node) {
    if (node.isTextual()) {
      return new TextNode(getSecretHandler().replaceSecrets(node.asText()));
    } else if (node.isObject()) {
      Map<String, JsonNode> fields =
          node.properties().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey, e -> replaceSecretsViaReflection(e.getValue())));
      return new ObjectNode(objectMapper.getNodeFactory(), fields);
    } else if (node.isArray()) {
      List<JsonNode> elements = new ArrayList<>();
      for (JsonNode element : node) {
        elements.add(replaceSecretsViaReflection(element));
      }
      return new ArrayNode(objectMapper.getNodeFactory(), elements);
    } else {
      return node;
    }
  }
}
