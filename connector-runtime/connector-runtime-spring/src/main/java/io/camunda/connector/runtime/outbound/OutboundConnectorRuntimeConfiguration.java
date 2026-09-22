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
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.annotation.OutboundConnectorObjectMapper;
import io.camunda.connector.runtime.core.intrinsic.AllowedIntrinsicFunction;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionAllowListFactory;
import io.camunda.connector.runtime.core.outbound.DefaultOutboundConnectorFactory;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorDiscovery;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.core.secret.SecretFilterFactory;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.outbound.job.ConfigurableIntrinsicFunctionAllowListFactory;
import io.camunda.connector.runtime.outbound.job.ConfigurableIntrinsicFunctionAllowListFactory.IntrinsicFunctionAllowListMode;
import io.camunda.connector.runtime.outbound.job.ConfigurableSecretFilterFactory;
import io.camunda.connector.runtime.outbound.job.ConfigurableSecretFilterFactory.SecretFilterMode;
import io.camunda.connector.runtime.outbound.lifecycle.OutboundConnectorAnnotationProcessor;
import io.camunda.connector.runtime.outbound.lifecycle.OutboundConnectorManager;
import io.camunda.connector.runtime.outbound.secret.ProcessDefinitionIntrinsicFunctionAllowListCache;
import io.camunda.connector.runtime.outbound.secret.ProcessDefinitionModelCache;
import io.camunda.connector.runtime.outbound.secret.ProcessDefinitionSecretKeyCache;
import io.camunda.connector.runtime.outbound.secret.SecretKeyCache;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.document.factory.DocumentFactoryImpl;
import io.camunda.document.store.CamundaDocumentStore;
import io.camunda.document.store.CamundaDocumentStoreImpl;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.spring.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.zeebe.spring.client.jobhandling.JobWorkerManager;
import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import java.util.List;
import java.util.Map;
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

  @Bean
  public CamundaDocumentStore documentStore(ZeebeClient zeebeClient) {
    return new CamundaDocumentStoreImpl(zeebeClient);
  }

  @Bean
  public DocumentFactory documentFactory(CamundaDocumentStore documentStore) {
    return new DocumentFactoryImpl(documentStore);
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

  /**
   * Builds its own {@link ProcessDefinitionModelCache} backed by the shared {@code
   * bpmnModelCacheStore} rather than fetching the BPMN model itself: {@link
   * #intrinsicFunctionAllowListCache} builds one from the exact same underlying cache, so a process
   * definition fetched for one purpose is reused for the other instead of being fetched twice.
   */
  @Bean
  public SecretKeyCache secretKeyCache(
      @Autowired(required = false) CamundaOperateClient camundaOperateClient,
      SecretKeyCacheHolder secretKeyCacheStore,
      BpmnModelCacheHolder bpmnModelCacheStore) {
    var modelCache =
        new ProcessDefinitionModelCache(camundaOperateClient, asModelCache(bpmnModelCacheStore));
    return new ProcessDefinitionSecretKeyCache(modelCache, secretKeyCacheStore.cache());
  }

  @Bean
  public SecretFilterFactory secretFilterFactory(
      @Value("${camunda.connector.secret-resolver.secret-filter.mode:STRICT}")
          SecretFilterMode secretFilterMode,
      SecretKeyCache secretKeyCache) {
    return new ConfigurableSecretFilterFactory(secretFilterMode, secretKeyCache);
  }

  /**
   * Mirrors {@link #secretKeyCacheStore} exactly, one type level down: an unqualified {@code Cache}
   * bean would collide with a host application's own cache bean of that exact type.
   */
  @Bean
  BpmnModelCacheHolder bpmnModelCacheStore(
      @Value("${camunda.connector.intrinsic-function.allow-list.cache.enabled:true}")
          boolean cacheEnabled,
      @Value("${camunda.connector.intrinsic-function.allow-list.cache.max-size:1000}")
          int cacheMaxSize) {
    if (!cacheEnabled) {
      return new BpmnModelCacheHolder(new NoOpCache<>());
    }
    int boundedMaxSize = cacheMaxSize > 0 ? cacheMaxSize : 1000;
    return new BpmnModelCacheHolder(Caffeine.newBuilder().maximumSize(boundedMaxSize).build());
  }

  @Bean
  IntrinsicFunctionAllowListCacheHolder intrinsicFunctionAllowListCacheStore(
      @Value("${camunda.connector.intrinsic-function.allow-list.cache.enabled:true}")
          boolean cacheEnabled,
      @Value("${camunda.connector.intrinsic-function.allow-list.cache.max-size:1000}")
          int cacheMaxSize) {
    if (!cacheEnabled) {
      return new IntrinsicFunctionAllowListCacheHolder(new NoOpCache<>());
    }
    int boundedMaxSize = cacheMaxSize > 0 ? cacheMaxSize : 1000;
    return new IntrinsicFunctionAllowListCacheHolder(
        Caffeine.newBuilder().maximumSize(boundedMaxSize).build());
  }

  @Bean
  public ProcessDefinitionIntrinsicFunctionAllowListCache intrinsicFunctionAllowListCache(
      @Autowired(required = false) CamundaOperateClient camundaOperateClient,
      BpmnModelCacheHolder bpmnModelCacheStore,
      IntrinsicFunctionAllowListCacheHolder intrinsicFunctionAllowListCacheStore) {
    var modelCache =
        new ProcessDefinitionModelCache(camundaOperateClient, asModelCache(bpmnModelCacheStore));
    return new ProcessDefinitionIntrinsicFunctionAllowListCache(
        modelCache, asAllowListCache(intrinsicFunctionAllowListCacheStore));
  }

  /**
   * {@link BpmnModelCacheHolder} exposes a generic {@code Cache<Object, Object>} so the bean itself
   * can't collide with a host's own cache bean of any specific type (see its javadoc); the cast to
   * this cache's actual, narrower shape is confined to this one call site instead of living inside
   * {@link ProcessDefinitionModelCache} itself.
   */
  @SuppressWarnings("unchecked")
  private static Cache<Long, BpmnModelInstance> asModelCache(BpmnModelCacheHolder holder) {
    return (Cache<Long, BpmnModelInstance>) (Cache<?, ?>) holder.cache();
  }

  /** Mirrors {@link #asModelCache} for {@link IntrinsicFunctionAllowListCacheHolder}. */
  @SuppressWarnings("unchecked")
  private static Cache<Long, Map<String, List<AllowedIntrinsicFunction>>> asAllowListCache(
      IntrinsicFunctionAllowListCacheHolder holder) {
    return (Cache<Long, Map<String, List<AllowedIntrinsicFunction>>>) (Cache<?, ?>) holder.cache();
  }

  @Bean
  public IntrinsicFunctionAllowListFactory intrinsicFunctionAllowListFactory(
      @Value("${camunda.connector.intrinsic-function.allow-list.mode:ENABLED}")
          IntrinsicFunctionAllowListMode intrinsicFunctionAllowListMode,
      ProcessDefinitionIntrinsicFunctionAllowListCache intrinsicFunctionAllowListCache) {
    return new ConfigurableIntrinsicFunctionAllowListFactory(
        intrinsicFunctionAllowListMode, intrinsicFunctionAllowListCache);
  }

  @Bean
  public OutboundConnectorManager outboundConnectorManager(
      JobWorkerManager jobWorkerManager,
      OutboundConnectorFactory connectorFactory,
      CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      SecretProviderAggregator secretProviderAggregator,
      @Autowired(required = false) ValidationProvider validationProvider,
      DocumentFactory documentFactory,
      @OutboundConnectorObjectMapper ObjectMapper objectMapper,
      MetricsRecorder metricsRecorder,
      SecretFilterFactory secretFilterFactory,
      IntrinsicFunctionAllowListFactory intrinsicFunctionAllowListFactory) {
    return new OutboundConnectorManager(
        jobWorkerManager,
        connectorFactory,
        commandExceptionHandlingStrategy,
        secretProviderAggregator,
        validationProvider,
        documentFactory,
        objectMapper,
        metricsRecorder,
        secretFilterFactory,
        intrinsicFunctionAllowListFactory);
  }

  @Bean
  public OutboundConnectorAnnotationProcessor annotationProcessor(
      OutboundConnectorManager manager, OutboundConnectorFactory factory) {
    return new OutboundConnectorAnnotationProcessor(manager, factory);
  }
}
