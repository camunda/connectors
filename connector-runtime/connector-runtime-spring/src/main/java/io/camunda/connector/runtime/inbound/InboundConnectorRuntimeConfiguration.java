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
package io.camunda.connector.runtime.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.client.CamundaClient;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import io.camunda.connector.runtime.core.inbound.DefaultInboundConnectorContextFactory;
import io.camunda.connector.runtime.core.inbound.DefaultInboundConnectorFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextFactory;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import io.camunda.connector.runtime.core.inbound.ProcessInstanceClient;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogRegistry;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.inbound.controller.InboundConnectorRestController;
import io.camunda.connector.runtime.inbound.controller.InboundInstancesRestController;
import io.camunda.connector.runtime.inbound.controller.exception.GlobalExceptionHandler;
import io.camunda.connector.runtime.inbound.executable.BatchExecutableProcessor;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistryImpl;
import io.camunda.connector.runtime.inbound.importer.ProcessDefinitionImportConfiguration;
import io.camunda.connector.runtime.inbound.search.ProcessInstanceClientConfiguration;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import io.camunda.connector.runtime.inbound.search.SearchQueryClientImpl;
import io.camunda.connector.runtime.inbound.state.ProcessDefinitionInspector;
import io.camunda.connector.runtime.inbound.state.ProcessStateContainer;
import io.camunda.connector.runtime.inbound.state.ProcessStateContainerImpl;
import io.camunda.connector.runtime.inbound.state.ProcessStateManager;
import io.camunda.connector.runtime.inbound.state.ProcessStateManagerImpl;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorRegistry;
import io.camunda.connector.runtime.metrics.ConnectorsInboundMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

@Configuration
@Import({
  ProcessDefinitionImportConfiguration.class,
  ProcessInstanceClientConfiguration.class,
  InboundConnectorRestController.class,
  InboundInstancesRestController.class,
  GlobalExceptionHandler.class,
})
public class InboundConnectorRuntimeConfiguration {
  @Value("${camunda.connector.inbound.message.ttl:PT1H}")
  private Duration messageTtl;

  @Value("${camunda.connector.inbound.log.size:100}")
  private int activityLogSize;

  @Bean
  public static InboundConnectorBeanDefinitionProcessor inboundConnectorBeanDefinitionProcessor(
      Environment environment) {
    return new InboundConnectorBeanDefinitionProcessor(environment);
  }

  @Bean
  public InboundCorrelationHandler inboundCorrelationHandler(
      final CamundaClient camundaClient,
      final FeelEngineWrapper feelEngine,
      @ConnectorsObjectMapper final ObjectMapper objectMapper,
      final ConnectorsInboundMetrics connectorsInboundMetrics) {
    return new MeteredInboundCorrelationHandler(
        camundaClient, feelEngine, objectMapper, messageTtl, connectorsInboundMetrics);
  }

  @Bean
  public InboundConnectorContextFactory springInboundConnectorContextFactory(
      @ConnectorsObjectMapper ObjectMapper mapper,
      InboundCorrelationHandler correlationHandler,
      SecretProviderAggregator secretProviderAggregator,
      @Autowired(required = false) ValidationProvider validationProvider,
      ProcessInstanceClient processInstanceClient,
      DocumentFactory documentFactory) {
    return new DefaultInboundConnectorContextFactory(
        mapper,
        correlationHandler,
        secretProviderAggregator,
        validationProvider,
        processInstanceClient,
        documentFactory);
  }

  @Bean
  public InboundConnectorFactory springInboundConnectorFactory() {
    return new DefaultInboundConnectorFactory();
  }

  @Bean
  public ActivityLogRegistry activityLogRegistry() {
    return new ActivityLogRegistry(activityLogSize);
  }

  @Bean
  public BatchExecutableProcessor batchExecutableProcessor(
      InboundConnectorFactory connectorFactory,
      InboundConnectorContextFactory connectorContextFactory,
      ConnectorsInboundMetrics connectorsInboundMetrics,
      @Autowired(required = false) WebhookConnectorRegistry webhookConnectorRegistry,
      ActivityLogRegistry activityLogRegistry) {
    return new BatchExecutableProcessor(
        connectorFactory,
        connectorContextFactory,
        connectorsInboundMetrics,
        webhookConnectorRegistry,
        activityLogRegistry);
  }

  @Bean
  public ConnectorsInboundMetrics connectorsInboundMetrics(MeterRegistry meterRegistry) {
    return new ConnectorsInboundMetrics(meterRegistry);
  }

  @Bean
  @ConditionalOnMissingBean
  public InboundExecutableRegistry inboundExecutableRegistry(
      InboundConnectorFactory inboundConnectorFactory,
      BatchExecutableProcessor batchExecutableProcessor) {
    return new InboundExecutableRegistryImpl(inboundConnectorFactory, batchExecutableProcessor);
  }

  @Bean
  SearchQueryClient searchQueryClient(
      CamundaClient camundaClient,
      @Value("${camunda.connector.process-definition-search.page-size:200}") int limit) {
    return new SearchQueryClientImpl(camundaClient, limit);
  }

  @Bean
  public CacheManager processDefinitionCacheManager(
      @Value("${camunda.connector.inbound.process-definition-cache.max-size:1000}")
          int cacheMaxSize) {
    int boundedMaxSize = cacheMaxSize > 0 ? cacheMaxSize : 1000;
    CaffeineCacheManager cacheManager =
        new CaffeineCacheManager(ProcessDefinitionInspector.PROCESS_DEFINITION_CACHE_NAME);
    cacheManager.setCaffeine(Caffeine.newBuilder().maximumSize(boundedMaxSize));
    return cacheManager;
  }

  @Bean
  public ProcessDefinitionInspector processDefinitionInspector(
      SearchQueryClient searchQueryClient, CacheManager cacheManager) {
    Cache cache =
        Objects.requireNonNull(
            cacheManager.getCache(ProcessDefinitionInspector.PROCESS_DEFINITION_CACHE_NAME),
            "processDefinitions cache must be configured");
    return new ProcessDefinitionInspector(searchQueryClient, cache);
  }

  @Bean
  public ProcessStateContainer processStateContainer() {
    return new ProcessStateContainerImpl();
  }

  @Bean
  public ProcessStateManager processStateManager(
      InboundExecutableRegistry registry,
      ProcessDefinitionInspector inspector,
      ProcessStateContainer processStateContainer) {
    return new ProcessStateManagerImpl(processStateContainer, inspector, registry);
  }
}
