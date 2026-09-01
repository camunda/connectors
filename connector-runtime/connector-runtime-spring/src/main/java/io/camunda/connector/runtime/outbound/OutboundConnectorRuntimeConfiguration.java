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
import io.camunda.operate.CamundaOperateClient;
import io.camunda.zeebe.spring.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.zeebe.spring.client.jobhandling.JobWorkerManager;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboundConnectorRuntimeConfiguration {

  @Bean
  public OutboundConnectorFactory outboundConnectorFactory() {
    return new DefaultOutboundConnectorFactory(
        OutboundConnectorDiscovery.loadConnectorConfigurations());
  }

  /**
   * Wrapped in {@link SecretKeyCacheHolder} rather than exposed as a plain {@code Cache} bean:
   * registering an unqualified cache bean, just like an unqualified {@code CacheManager}, would
   * collide with a host application's own cache bean of that exact type — the same ambiguous-bean
   * problem this replaces, one type level down. The holder is package-private, so no code outside
   * this configuration class can declare or depend on a bean of this type either.
   *
   * <p>Built directly from Caffeine, not from Spring's cache abstraction: see {@link
   * SecretKeyCacheHolder}'s javadoc for why. The "disabled" cache is a dedicated {@link NoOpCache},
   * not a {@code maximumSize(0)} Caffeine cache — Caffeine's own eviction runs on its maintenance
   * cycle, not synchronously on every write, so a value can still be observed on an
   * immediately-following get, unlike Spring's {@code NoOpCache} this replaces.
   */
  @Bean
  SecretKeyCacheHolder secretKeyCacheStore(
      @Value("${camunda.connector.secret-resolver.secret-filter.cache.enabled:true}")
          boolean cacheEnabled,
      @Value("${camunda.connector.secret-resolver.secret-filter.cache.max-size:1000}")
          int cacheMaxSize) {
    if (!cacheEnabled) {
      return new SecretKeyCacheHolder(new NoOpCache<>());
    }
    int boundedMaxSize = cacheMaxSize > 0 ? cacheMaxSize : 1000;
    return new SecretKeyCacheHolder(Caffeine.newBuilder().maximumSize(boundedMaxSize).build());
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
      ObjectMapper objectMapper,
      MetricsRecorder metricsRecorder,
      SecretFilterFactory secretFilterFactory) {
    return new OutboundConnectorManager(
        jobWorkerManager,
        connectorFactory,
        commandExceptionHandlingStrategy,
        secretProviderAggregator,
        validationProvider,
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
