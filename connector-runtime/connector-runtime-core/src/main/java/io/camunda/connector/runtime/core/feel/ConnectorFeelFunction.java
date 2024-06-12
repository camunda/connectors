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
package io.camunda.connector.runtime.core.feel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.external.ExternalOutboundConnectorContext;
import io.camunda.connector.runtime.core.outbound.DefaultOutboundConnectorFactory;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorDiscovery;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import java.util.Arrays;
import org.camunda.feel.context.JavaFunction;
import org.camunda.feel.syntaxtree.ValContext;
import org.camunda.feel.syntaxtree.ValString;
import org.camunda.feel.valuemapper.ValueMapper;

public class ConnectorFeelFunction {

  private static final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER;
  private static final ValueMapper feelValueMapper = ValueMapper.defaultValueMapper();
  private static final OutboundConnectorFactory factory =
      new DefaultOutboundConnectorFactory(OutboundConnectorDiscovery.loadConnectorConfigurations());

  private static volatile SecretProvider
      secretProvider; // TODO: consider setting some default value?
  private static volatile ValidationProvider validationProvider;

  public static void setSecretProvider(SecretProvider provider) {
    secretProvider = provider;
  }

  public static void setValidationProvider(ValidationProvider provider) {
    validationProvider = provider;
  }

  public static JavaFunction function =
      new JavaFunction(
          Arrays.asList("type", "variables"),
          args -> {
            if (secretProvider == null) {
              throw new IllegalStateException("Secret provider is not set");
            }

            if (validationProvider == null) {
              throw new IllegalStateException("Validation provider is not set");
            }

            final ValString type = (ValString) args.get(0);
            String typeValue = type.value();

            final ValContext variables = (ValContext) args.get(1);
            Object unpackedVariables = feelValueMapper.unpackVal(variables);
            String variablesAsString;
            try {
              variablesAsString = objectMapper.writeValueAsString(unpackedVariables);
            } catch (JsonProcessingException e) {
              throw new RuntimeException(e);
            }

            var connector = factory.getInstance(typeValue);
            var context =
                new ExternalOutboundConnectorContext(
                    secretProvider, validationProvider, objectMapper, variablesAsString);

            Object result;
            try {
              result = connector.execute(context);
            } catch (Exception e) {
              throw new RuntimeException("Failed to execute connector", e);
            }
            return feelValueMapper.toVal(result);
          });
}
