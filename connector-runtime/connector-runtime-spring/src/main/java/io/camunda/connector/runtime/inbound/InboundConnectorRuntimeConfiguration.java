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
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.validation.ValidationProvider;
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
import io.camunda.connector.runtime.instances.service.InboundInstancesService;
import io.camunda.connector.runtime.metrics.ConnectorsInboundMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

@Configuration
@Import({
  ProcessDefinitionImportConfiguration.class,
  ProcessInstanceClientConfiguration.class,
  InboundConnectorRestController.class,
  InboundInstancesRestController.class,
  GlobalExceptionHandler.class
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

  /**
   * Plain (non-{@code @Bean}) helper so this can be called from every {@code @Bean} method that
   * needs the per-physical-tenant correlation-handler map, without any of them declaring a {@code
   * Map<String, InboundCorrelationHandler>}-typed parameter: Spring's dependency resolution
   * special-cases any {@code Map<String, X>}-typed {@code @Bean} parameter by collecting *all*
   * beans of type {@code X} by name — including the {@code @Lazy} scalar {@code
   * inboundCorrelationHandler} bean kept below for backward compatibility — instead of using the
   * bean whose own declared type is the map. That silently produces a map keyed by the scalar
   * bean's name (and forces it to instantiate, which throws once more than one physical tenant is
   * configured) rather than the real per-physical-tenant map.
   */
  private Map<String, InboundCorrelationHandler> buildCorrelationHandlersByPhysicalTenantId(
      CamundaClientRegistry registry,
      CamundaClient legacyCamundaClient,
      ObjectMapper objectMapper,
      ConnectorsInboundMetrics connectorsInboundMetrics) {
    return registry.clientNames().stream()
        .collect(
            toMapByPhysicalTenantId(
                registry,
                legacyCamundaClient,
                name ->
                    new MeteredInboundCorrelationHandler(
                        resolveClient(registry, name, legacyCamundaClient),
                        objectMapper,
                        messageTtl,
                        connectorsInboundMetrics)));
  }

  @Bean
  public Map<String, InboundCorrelationHandler> correlationHandlersByPhysicalTenantId(
      final CamundaClientRegistry registry,
      @Autowired(required = false) final CamundaClient legacyCamundaClient,
      @ConnectorsObjectMapper final ObjectMapper objectMapper,
      final ConnectorsInboundMetrics connectorsInboundMetrics) {
    return buildCorrelationHandlersByPhysicalTenantId(
        registry, legacyCamundaClient, objectMapper, connectorsInboundMetrics);
  }

  /**
   * Backward-compatible scalar bean for existing single-physical-tenant call sites that
   * {@code @Autowired} {@link InboundCorrelationHandler} directly rather than the
   * per-physical-tenant map. {@code @Lazy} so it is only resolved (and only then required to be
   * unambiguous) if something actually injects it — a genuine multi-physical-tenant context that
   * never does so is unaffected.
   */
  @Bean
  @Lazy
  public InboundCorrelationHandler inboundCorrelationHandler(
      CamundaClientRegistry registry,
      @Autowired(required = false) CamundaClient legacyCamundaClient,
      @ConnectorsObjectMapper ObjectMapper objectMapper,
      ConnectorsInboundMetrics connectorsInboundMetrics) {
    return onlyValue(
        buildCorrelationHandlersByPhysicalTenantId(
            registry, legacyCamundaClient, objectMapper, connectorsInboundMetrics),
        InboundCorrelationHandler.class);
  }

  @Bean
  public InboundConnectorContextFactory springInboundConnectorContextFactory(
      @ConnectorsObjectMapper ObjectMapper mapper,
      ConnectorsInboundMetrics connectorsInboundMetrics,
      SecretProviderAggregator secretProviderAggregator,
      @Autowired(required = false) ValidationProvider validationProvider,
      Map<String, ProcessInstanceClient> processInstanceClientsByPhysicalTenantId,
      DocumentFactory documentFactory,
      CamundaClientRegistry registry,
      @Autowired(required = false) CamundaClient legacyCamundaClient) {
    Map<String, InboundCorrelationHandler> correlationHandlersByPhysicalTenantId =
        buildCorrelationHandlersByPhysicalTenantId(
            registry, legacyCamundaClient, mapper, connectorsInboundMetrics);
    Map<String, InboundConnectorContextFactory> delegatesByPhysicalTenantId =
        registry.clientNames().stream()
            .collect(
                toMapByPhysicalTenantId(
                    registry,
                    legacyCamundaClient,
                    name -> {
                      var physicalTenantId =
                          resolvePhysicalTenantId(registry, name, legacyCamundaClient);
                      return new DefaultInboundConnectorContextFactory(
                          mapper,
                          correlationHandlersByPhysicalTenantId.get(physicalTenantId),
                          secretProviderAggregator,
                          validationProvider,
                          processInstanceClientsByPhysicalTenantId.get(physicalTenantId),
                          documentFactory,
                          resolveClient(registry, name, legacyCamundaClient));
                    }));
    return new PhysicalTenantIdRoutingInboundConnectorContextFactory(delegatesByPhysicalTenantId);
  }

  /**
   * Resolves the {@link CamundaClient} for the given client name. Prefers the {@link
   * CamundaClientRegistry} entry, but falls back to a directly-supplied single {@code
   * CamundaClient} bean when the registry claims the name exists (e.g. the "default" entry always
   * synthesized for legacy single-client configs) but no matching bean was actually registered —
   * for example when a {@code CamundaClient} bean is supplied manually/overridden (as in test
   * fixtures) instead of via {@code camunda.clients.*}. Note {@link
   * CamundaClientRegistry#find(String)} does not guard against this case itself: it still resolves
   * the underlying bean and throws if it is missing, so the fallback must catch that failure
   * directly rather than rely on an empty {@code Optional}.
   */
  private static CamundaClient resolveClient(
      CamundaClientRegistry registry, String name, CamundaClient legacyCamundaClient) {
    try {
      return registry.get(name);
    } catch (RuntimeException e) {
      if (legacyCamundaClient == null) {
        throw new IllegalStateException("No CamundaClient configured for client '" + name + "'", e);
      }
      return legacyCamundaClient;
    }
  }

  /**
   * Resolves the physical tenant ID for a configured {@code CamundaClientRegistry} client name: the
   * explicitly configured {@code physical-tenant-id} if present, otherwise the client name itself
   * (which is always present, unlike the optional physical-tenant-id configuration). Falls back to
   * the client name if the configuration cannot be read at all — some test doubles (e.g. the {@code
   * camunda-process-test-spring} client proxy) defer real initialization until the test container
   * is ready and throw if queried during Spring context startup.
   */
  private static String resolvePhysicalTenantId(
      CamundaClientRegistry registry, String name, CamundaClient legacyCamundaClient) {
    try {
      var physicalTenantId =
          resolveClient(registry, name, legacyCamundaClient)
              .getConfiguration()
              .getPhysicalTenantId();
      return physicalTenantId != null ? physicalTenantId : name;
    } catch (RuntimeException e) {
      return name;
    }
  }

  /**
   * Builds a {@code Collectors.toMap} collector keyed by the resolved physical tenant ID for each
   * client name, failing clearly if two clients resolve to the same physical tenant ID (a
   * misconfiguration) rather than silently dropping one.
   */
  private static <T> Collector<String, ?, Map<String, T>> toMapByPhysicalTenantId(
      CamundaClientRegistry registry,
      CamundaClient legacyCamundaClient,
      Function<String, T> valueFn) {
    return Collectors.toMap(
        name -> resolvePhysicalTenantId(registry, name, legacyCamundaClient),
        valueFn,
        (a, b) -> {
          throw new IllegalStateException(
              "Multiple CamundaClients resolve to the same physical tenant ID; "
                  + "each configured client must have a unique physical-tenant-id");
        });
  }

  /**
   * Resolves the single value of a per-physical-tenant map for backward-compatible scalar beans,
   * failing clearly when more than one physical tenant is configured instead of silently picking an
   * arbitrary one.
   */
  private static <T> T onlyValue(Map<String, T> byPhysicalTenantId, Class<T> beanType) {
    if (byPhysicalTenantId.size() != 1) {
      throw new IllegalStateException(
          "No single "
              + beanType.getSimpleName()
              + " bean available: "
              + byPhysicalTenantId.size()
              + " physical tenants are configured; inject Map<String, "
              + beanType.getSimpleName()
              + "> instead.");
    }
    return byPhysicalTenantId.values().iterator().next();
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
      BatchExecutableProcessor batchExecutableProcessor,
      ActivityLogRegistry activityLogRegistry) {
    return new InboundExecutableRegistryImpl(
        inboundConnectorFactory, batchExecutableProcessor, activityLogRegistry);
  }

  @Bean
  @ConditionalOnMissingBean
  public InboundInstancesService inboundInstancesService(
      InboundExecutableRegistry inboundExecutableRegistry) {
    return new InboundInstancesService(inboundExecutableRegistry);
  }

  /**
   * Plain (non-{@code @Bean}), {@code public static} so it can also be called directly from other
   * {@code @Configuration} classes ({@link ProcessDefinitionImportConfiguration}, {@link
   * ProcessInstanceClientConfiguration}) that need this same per-physical-tenant map, without any
   * of them declaring a {@code Map<String, SearchQueryClient>}-typed {@code @Bean} parameter — see
   * {@link #buildCorrelationHandlersByPhysicalTenantId} for why that matters: several E2E test
   * suites add a scalar {@code @MockitoBean SearchQueryClient}, and a {@code Map<String,
   * SearchQueryClient>}-typed parameter would silently collect just that scalar bean (keyed by its
   * bean name) instead of using this method's real per-physical-tenant map.
   *
   * <p>Builds one {@link SearchQueryClient} per configured physical tenant. When a {@code
   * SearchQueryClient} bean is manually supplied (e.g. that {@code @MockitoBean}, used to control
   * process-definition search results), that bean is used in place of constructing a real client —
   * mirroring the {@code legacyCamundaClient} fallback above, since overriding this bean only makes
   * sense for a single, legacy-style client configuration.
   */
  public static Map<String, SearchQueryClient> buildSearchQueryClientsByPhysicalTenantId(
      CamundaClientRegistry registry,
      CamundaClient legacyCamundaClient,
      SearchQueryClient legacySearchQueryClient,
      int limit) {
    return registry.clientNames().stream()
        .collect(
            toMapByPhysicalTenantId(
                registry,
                legacyCamundaClient,
                name ->
                    legacySearchQueryClient != null
                        ? legacySearchQueryClient
                        : new SearchQueryClientImpl(
                            resolveClient(registry, name, legacyCamundaClient), limit)));
  }

  @Bean
  Map<String, SearchQueryClient> searchQueryClientsByPhysicalTenantId(
      CamundaClientRegistry registry,
      @Autowired(required = false) CamundaClient legacyCamundaClient,
      @Autowired(required = false) SearchQueryClient legacySearchQueryClient,
      @Value("${camunda.connector.process-definition-search.page-size:200}") int limit) {
    return buildSearchQueryClientsByPhysicalTenantId(
        registry, legacyCamundaClient, legacySearchQueryClient, limit);
  }

  @Bean
  public CacheManager processDefinitionCacheManager(
      @Value("${camunda.connector.inbound.process-definition-cache.enabled:true}")
          boolean cacheEnabled,
      @Value("${camunda.connector.inbound.process-definition-cache.max-size:1000}")
          int cacheMaxSize) {
    if (!cacheEnabled) {
      return new NoOpCacheManager();
    }
    int boundedMaxSize = cacheMaxSize > 0 ? cacheMaxSize : 1000;
    CaffeineCacheManager cacheManager =
        new CaffeineCacheManager(ProcessDefinitionInspector.PROCESS_DEFINITION_CACHE_NAME);
    cacheManager.setCaffeine(Caffeine.newBuilder().maximumSize(boundedMaxSize));
    return cacheManager;
  }

  @Bean
  public ProcessDefinitionInspector processDefinitionInspector(
      CamundaClientRegistry registry,
      @Autowired(required = false) CamundaClient legacyCamundaClient,
      @Autowired(required = false) SearchQueryClient legacySearchQueryClient,
      @Value("${camunda.connector.process-definition-search.page-size:200}") int limit,
      @Qualifier("processDefinitionCacheManager") CacheManager cacheManager,
      ConnectorsInboundMetrics connectorsInboundMetrics) {
    Cache cache =
        Objects.requireNonNull(
            cacheManager.getCache(ProcessDefinitionInspector.PROCESS_DEFINITION_CACHE_NAME),
            "processDefinitions cache must be configured");
    return new ProcessDefinitionInspector(
        buildSearchQueryClientsByPhysicalTenantId(
            registry, legacyCamundaClient, legacySearchQueryClient, limit),
        cache,
        connectorsInboundMetrics);
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
