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
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.outbound.DefaultOutboundConnectorFactory;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorDiscovery;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.core.secret.SecretFilterFactory;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.outbound.job.ConfigurableSecretFilterFactory;
import io.camunda.connector.runtime.outbound.job.ConfigurableSecretFilterFactory.SecretFilterMode;
import io.camunda.connector.runtime.outbound.lifecycle.OutboundConnectorAnnotationProcessor;
import io.camunda.connector.runtime.outbound.lifecycle.OutboundConnectorManager;
import io.camunda.connector.runtime.outbound.secret.ProcessDefinitionSecretKeyCache;
import io.camunda.connector.runtime.outbound.secret.SecretKeyCache;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.document.factory.DocumentFactoryImpl;
import io.camunda.document.store.CamundaDocumentStore;
import io.camunda.document.store.CamundaDocumentStoreImpl;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.zeebe.spring.client.jobhandling.JobWorkerManager;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.NoOpCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboundConnectorRuntimeConfiguration {

  @Bean
  public OutboundConnectorFactory outboundConnectorFactory() {
    return new DefaultOutboundConnectorFactory(
        OutboundConnectorDiscovery.loadConnectorConfigurations());
  }

  @Bean
  public CamundaDocumentStore documentStore(ZeebeClient zeebeClient) {
    return new CamundaDocumentStoreImpl(zeebeClient);
  }

  @Bean
  public DocumentFactory documentFactory(CamundaDocumentStore documentStore) {
    return new DocumentFactoryImpl(documentStore);
  }

  /**
   * Wrapped in {@link SecretKeyCacheHolder} rather than exposed as a plain {@link Cache} bean:
   * registering an unqualified {@code Cache}, just like an unqualified {@code CacheManager}, would
   * collide with a host application's own cache bean of that exact type — the same ambiguous-bean
   * problem this replaces, one type level down. The holder is package-private, so no code outside
   * this configuration class can declare or depend on a bean of this type either.
   */
  @Bean
  SecretKeyCacheHolder secretKeyCacheStore(
      @Value("${camunda.connector.secret-resolver.secret-filter.cache.enabled:true}")
          boolean cacheEnabled,
      @Value("${camunda.connector.secret-resolver.secret-filter.cache.max-size:1000}")
          int cacheMaxSize) {
    if (!cacheEnabled) {
      return new SecretKeyCacheHolder(new NoOpCache(SecretKeyCache.SECRET_KEY_CACHE_NAME));
    }
    int boundedMaxSize = cacheMaxSize > 0 ? cacheMaxSize : 1000;
    return new SecretKeyCacheHolder(
        new CaffeineCache(
            SecretKeyCache.SECRET_KEY_CACHE_NAME,
            Caffeine.newBuilder().maximumSize(boundedMaxSize).build()));
  }

  @Bean
  public SecretKeyCache secretKeyCache(
      @Autowired(required = false) CamundaOperateClient camundaOperateClient,
      SecretKeyCacheHolder secretKeyCacheStore) {
    return new ProcessDefinitionSecretKeyCache(camundaOperateClient, secretKeyCacheStore.cache());
  }

  @Bean
  public SecretFilterFactory secretFilterFactory(
      @Value("${camunda.connector.secret-resolver.secret-filter.mode:STRICT}")
          SecretFilterMode secretFilterMode,
      SecretKeyCache secretKeyCache) {
    return new ConfigurableSecretFilterFactory(secretFilterMode, secretKeyCache);
  }

  @Bean
  public OutboundConnectorManager outboundConnectorManager(
      JobWorkerManager jobWorkerManager,
      OutboundConnectorFactory connectorFactory,
      CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      SecretProviderAggregator secretProviderAggregator,
      @Autowired(required = false) ValidationProvider validationProvider,
      DocumentFactory documentFactory,
      ObjectMapper objectMapper,
      MetricsRecorder metricsRecorder,
      SecretFilterFactory secretFilterFactory) {
    return new OutboundConnectorManager(
        jobWorkerManager,
        connectorFactory,
        commandExceptionHandlingStrategy,
        secretProviderAggregator,
        validationProvider,
        documentFactory,
        objectMapper,
        metricsRecorder,
        secretFilterFactory);
  }

  @Bean
  public OutboundConnectorAnnotationProcessor annotationProcessor(
      OutboundConnectorManager manager, OutboundConnectorFactory factory) {
    return new OutboundConnectorAnnotationProcessor(manager, factory);
  }
}
