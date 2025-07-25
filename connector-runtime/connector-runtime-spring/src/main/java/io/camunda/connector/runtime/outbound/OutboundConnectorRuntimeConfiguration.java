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
package io.camunda.connector.runtime.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.config.ConnectorConfigurationOverrides;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.DefaultOutboundConnectorFactory;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorDiscovery;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.metrics.ConnectorsOutboundMetrics;
import io.camunda.connector.runtime.outbound.lifecycle.OutboundConnectorAnnotationProcessor;
import io.camunda.connector.runtime.outbound.lifecycle.OutboundConnectorManager;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.document.factory.DocumentFactoryImpl;
import io.camunda.document.store.CamundaDocumentStore;
import io.camunda.document.store.CamundaDocumentStoreImpl;
import io.camunda.spring.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.spring.client.jobhandling.JobWorkerManager;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class OutboundConnectorRuntimeConfiguration {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Bean
  public OutboundConnectorFactory outboundConnectorFactory(
      Environment env, List<OutboundConnectorFunction> functions) {
    List<OutboundConnectorConfiguration> config =
        new ArrayList<>(OutboundConnectorDiscovery.loadConnectorConfigurations());
    functions.stream()
        .filter(f -> f.getClass().isAnnotationPresent(OutboundConnector.class))
        .map(
            f -> {
              final OutboundConnector outboundConnector =
                  f.getClass().getAnnotation(OutboundConnector.class);
              return createConnectorConfiguration(outboundConnector, f, env);
            })
        .forEach(config::add);
    return new DefaultOutboundConnectorFactory(config);
  }

  private OutboundConnectorConfiguration createConnectorConfiguration(
      OutboundConnector outboundConnector,
      OutboundConnectorFunction bean,
      Environment environment) {
    final var configurationOverrides =
        new ConnectorConfigurationOverrides(outboundConnector.name(), environment::getProperty);

    OutboundConnectorConfiguration configuration =
        new OutboundConnectorConfiguration(
            outboundConnector.name(),
            outboundConnector.inputVariables(),
            configurationOverrides.typeOverride().orElse(outboundConnector.type()),
            bean.getClass(),
            () -> bean,
            configurationOverrides.timeoutOverride().orElse(null));
    LOGGER.info("Configuring outbound connector {}", configuration);
    return configuration;
  }

  @Bean
  public CamundaDocumentStore documentStore(CamundaClient camundaClient) {
    return new CamundaDocumentStoreImpl(camundaClient);
  }

  @Bean
  public DocumentFactory documentFactory(CamundaDocumentStore documentStore) {
    return new DocumentFactoryImpl(documentStore);
  }

  @Bean
  public OutboundConnectorManager outboundConnectorManager(
      JobWorkerManager jobWorkerManager,
      OutboundConnectorFactory connectorFactory,
      CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      SecretProviderAggregator secretProviderAggregator,
      @Autowired(required = false) ValidationProvider validationProvider,
      ConnectorsOutboundMetrics outboundMetrics,
      DocumentFactory documentFactory,
      ObjectMapper objectMapper) {
    return new OutboundConnectorManager(
        jobWorkerManager,
        connectorFactory,
        commandExceptionHandlingStrategy,
        secretProviderAggregator,
        validationProvider,
        documentFactory,
        objectMapper,
        outboundMetrics);
  }

  @Bean
  public OutboundConnectorAnnotationProcessor annotationProcessor(
      OutboundConnectorManager manager) {
    return new OutboundConnectorAnnotationProcessor(manager);
  }

  @Bean
  public ConnectorsOutboundMetrics connectorsOutboundMetrics(MeterRegistry meterRegistry) {
    return new ConnectorsOutboundMetrics(meterRegistry);
  }
}
