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
package io.camunda.connector.runtime.core.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;

public class ExternalConnectorExecutor {

  private final OutboundConnectorFactory outboundConnectorFactory;

  private final SecretProvider secretProvider;
  private final ValidationProvider validationProvider;
  private final ObjectMapper objectMapper;

  public ExternalConnectorExecutor(
      OutboundConnectorFactory outboundConnectorFactory,
      SecretProvider secretProvider,
      ValidationProvider validationProvider,
      ObjectMapper objectMapper) {
    this.outboundConnectorFactory = outboundConnectorFactory;
    this.secretProvider = secretProvider;
    this.validationProvider = validationProvider;
    this.objectMapper = objectMapper;
  }

  public Object execute(String type, Object variables) throws Exception {
    var connector = outboundConnectorFactory.getInstance(type);
    String variablesJson;
    try {
      variablesJson = objectMapper.writeValueAsString(variables);
    } catch (Exception e) {
      throw new RuntimeException("Error while serializing variables", e);
    }
    var context =
        new ExternalOutboundConnectorContext(
            secretProvider, validationProvider, objectMapper, variablesJson);
    return connector.execute(context);
  }

  public <T> T execute(String type, Object variables, Class<T> cls) throws Exception {
    Object result = execute(type, variables);
    try {
      return cls.cast(result);
    } catch (ClassCastException e) {
      throw new RuntimeException("Error while casting result", e);
    }
  }
}
