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
package io.camunda.connector.runtime.core.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.EvictingQueue;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.InboundIntermediateConnectorContext;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.document.factory.DocumentFactory;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.Consumer;

public class DefaultInboundConnectorContextFactory implements InboundConnectorContextFactory {
  private final ObjectMapper objectMapper;
  private final InboundCorrelationHandler correlationHandler;
  private final SecretProviderAggregator secretProviderAggregator;
  private final ValidationProvider validationProvider;
  private final ProcessInstanceClient operateClientAdapter;
  private final DocumentFactory documentFactory;

  public DefaultInboundConnectorContextFactory(
      final ObjectMapper mapper,
      final InboundCorrelationHandler correlationHandler,
      final SecretProviderAggregator secretProviderAggregator,
      final ValidationProvider validationProvider,
      final ProcessInstanceClient operateClientAdapter,
      final DocumentFactory documentFactory) {
    this.objectMapper = mapper;
    this.correlationHandler = correlationHandler;
    this.secretProviderAggregator = secretProviderAggregator;
    this.validationProvider = validationProvider;
    this.operateClientAdapter = operateClientAdapter;
    this.documentFactory = documentFactory;
  }

  @Override
  public <T extends InboundConnectorExecutable<?>> InboundConnectorContext createContext(
      final ValidInboundConnectorDetails connectorDetails,
      final Consumer<Throwable> cancellationCallback,
      final Class<T> executableClass,
      final EvictingQueue<Activity> queue) {

    InboundConnectorReportingContext inboundContext =
        new InboundConnectorContextImpl(
            secretProviderAggregator,
            validationProvider,
            documentFactory,
            connectorDetails,
            correlationHandler,
            cancellationCallback,
            objectMapper,
            queue);

    if (isIntermediateContext(executableClass)) {
      inboundContext =
          new InboundIntermediateConnectorContextImpl(
              inboundContext,
              operateClientAdapter,
              validationProvider,
              objectMapper,
              correlationHandler);
    }

    return inboundContext;
  }

  private <T> boolean isIntermediateContext(Class<T> executableClass) {
    // Retrieve all generic interfaces implemented by the executable class.
    Type[] genericInterfaces = executableClass.getGenericInterfaces();

    for (Type genericInterface : genericInterfaces) {
      // Check if the current type is a parameterized type (i.e., has generic parameters).
      if (genericInterface instanceof ParameterizedType parameterizedType) {
        // Retrieve actual type arguments of the parameterized type.
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();

        // If the first generic type argument matches InboundIntermediateConnectorContext,
        // then this class represents an intermediate context.
        if (actualTypeArguments.length > 0
            && InboundIntermediateConnectorContext.class.equals(actualTypeArguments[0])) {
          return true;
        }
      }
    }
    return false;
  }
}
