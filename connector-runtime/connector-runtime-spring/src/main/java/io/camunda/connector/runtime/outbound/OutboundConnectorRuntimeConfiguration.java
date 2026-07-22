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
import io.camunda.client.CamundaClient;
import io.camunda.client.jobhandling.JobCallbackCommandWrapperFactory;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import io.camunda.connector.runtime.annotation.OutboundConnectorObjectMapper;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStoreImpl;
import io.camunda.connector.runtime.core.outbound.DefaultOutboundConnectorFactory;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.core.secret.SecretFilterFactory;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.core.validation.ValidationUtil;
import io.camunda.connector.runtime.instances.InstanceForwardingConfiguration;
import io.camunda.connector.runtime.instances.service.OutboundConnectorsService;
import io.camunda.connector.runtime.outbound.controller.OutboundConnectorsRestController;
import io.camunda.connector.runtime.outbound.job.ConfigurableSecretFilterFactory;
import io.camunda.connector.runtime.outbound.job.ConfigurableSecretFilterFactory.SecretFilterMode;
import io.camunda.connector.runtime.outbound.jobstream.BrokerJobStreamClient;
import io.camunda.connector.runtime.outbound.lifecycle.OutboundConnectorManager;
import io.camunda.connector.runtime.outbound.secret.ProcessDefinitionSecretKeyCache;
import io.camunda.connector.runtime.outbound.secret.SecretKeyCache;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

@Configuration
@Import({OutboundConnectorsRestController.class, InstanceForwardingConfiguration.class})
public class OutboundConnectorRuntimeConfiguration {

  @Bean
  @ConditionalOnMissingBean(OutboundConnectorFactory.class)
  public DefaultOutboundConnectorFactory outboundConnectorConfigurationRegistry(
      @ConnectorsObjectMapper ObjectMapper mapper,
      ValidationProvider validationProvider,
      Environment environment,
      List<OutboundConnectorFunction> functions,
      List<OutboundConnectorProvider> providers) {

    return new DefaultOutboundConnectorFactory(
        mapper, validationProvider, functions, providers, environment::getProperty);
  }

  @Bean
  public CamundaDocumentStore documentStore(CamundaClient camundaClient) {
    return new CamundaDocumentStoreImpl(camundaClient);
  }

  @Bean
  public DocumentFactory documentFactory(CamundaDocumentStore documentStore) {
    return new DocumentFactoryImpl(documentStore);
  }

  /**
   * Enumerates the configured client names: the {@link CamundaClientRegistry}'s own names when a
   * registry bean exists, otherwise a single synthetic {@code "default"} name representing a
   * directly-supplied legacy {@code CamundaClient} bean — some minimal/test contexts wire a raw
   * {@code CamundaClient} bean without the full {@code camunda-spring-boot-starter} auto-config
   * chain that would normally register a {@link CamundaClientRegistry}.
   */
  private static Set<String> clientNames(
      CamundaClientRegistry registry, CamundaClient legacyCamundaClient) {
    if (registry != null) {
      return registry.clientNames();
    }
    if (legacyCamundaClient != null) {
      return Set.of("default");
    }
    throw new IllegalStateException("No CamundaClient or CamundaClientRegistry configured");
  }

  /**
   * Resolves the {@link CamundaClient} for the given client name. Prefers the {@link
   * CamundaClientRegistry} entry, but falls back to a directly-supplied single {@code
   * CamundaClient} bean when the registry is absent entirely, or when the registry claims the name
   * exists but no matching bean was actually registered — e.g. when a {@code CamundaClient} bean is
   * supplied manually/overridden (as in test fixtures) instead of via {@code camunda.clients.*}.
   */
  private static CamundaClient resolveClient(
      CamundaClientRegistry registry, String name, CamundaClient legacyCamundaClient) {
    if (registry == null) {
      if (legacyCamundaClient == null) {
        throw new IllegalStateException("No CamundaClient configured for client '" + name + "'");
      }
      return legacyCamundaClient;
    }
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
   * explicitly configured {@code physical-tenant-id} if present, otherwise the client name itself.
   * Falls back to the client name if the configuration cannot be read at all — some test doubles
   * defer real initialization until a test container is ready and throw if queried too early.
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
   * client name, failing clearly if two clients resolve to the same physical tenant ID rather than
   * silently dropping one.
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
   * Plain (non-{@code @Bean}) helper so this can be called both from the {@code @Bean} method below
   * and directly from {@link #outboundConnectorManager}, without either call going through Spring's
   * container-based parameter resolution: a {@code @Bean} method whose OWN parameter type is {@code
   * Map<String, X>} has that parameter resolved by the container even when called directly from
   * another method in this class (Spring's {@code @Configuration} CGLIB proxying intercepts calls
   * to {@code @Bean} methods and resolves their declared parameters from the container, discarding
   * whatever arguments were passed in code) — and since this class also keeps the legacy scalar
   * {@code documentStore}/{@code documentFactory} beans for backward compatibility, such a
   * Map-typed parameter would silently resolve to {@code {"documentStore": <scalar bean>}} instead
   * of the real per-physical-tenant map. Avoiding {@code Map<String, X>}-typed {@code @Bean}
   * parameters entirely sidesteps this.
   */
  private static Map<String, CamundaDocumentStore> buildDocumentStoresByPhysicalTenantId(
      CamundaClientRegistry registry, CamundaClient legacyCamundaClient) {
    return clientNames(registry, legacyCamundaClient).stream()
        .collect(
            toMapByPhysicalTenantId(
                registry,
                legacyCamundaClient,
                name ->
                    new CamundaDocumentStoreImpl(
                        resolveClient(registry, name, legacyCamundaClient))));
  }

  private static Map<String, DocumentFactory> buildDocumentFactoriesByPhysicalTenantId(
      CamundaClientRegistry registry, CamundaClient legacyCamundaClient) {
    return buildDocumentStoresByPhysicalTenantId(registry, legacyCamundaClient).entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> new DocumentFactoryImpl(e.getValue())));
  }

  @Bean
  public Map<String, CamundaDocumentStore> documentStoresByPhysicalTenantId(
      @Autowired(required = false) CamundaClientRegistry registry,
      @Autowired(required = false) CamundaClient legacyCamundaClient) {
    return buildDocumentStoresByPhysicalTenantId(registry, legacyCamundaClient);
  }

  @Bean
  public Map<String, DocumentFactory> documentFactoriesByPhysicalTenantId(
      @Autowired(required = false) CamundaClientRegistry registry,
      @Autowired(required = false) CamundaClient legacyCamundaClient) {
    return buildDocumentFactoriesByPhysicalTenantId(registry, legacyCamundaClient);
  }

  @Bean
  @ConditionalOnMissingBean(ValidationProvider.class)
  ValidationProvider validationProvider() {
    return ValidationUtil.discoverDefaultValidationProviderImplementation();
  }

  /**
   * Creates a {@link BrokerJobStreamClient} when broker monitoring is enabled (on by default; set
   * {@code camunda.connector.broker.monitoring.enabled=false} to disable).
   *
   * <p>Two sub-modes, controlled by {@code camunda.connector.broker.monitoring.addresses}:
   *
   * <ul>
   *   <li><b>Explicit addresses</b> (recommended for Docker/NAT'd envs): set {@code
   *       camunda.connector.broker.monitoring.addresses} to a comma-separated list of base URLs
   *       (e.g. {@code http://localhost:9600,http://localhost:9601}). No topology request is made.
   *   <li><b>Topology discovery</b> (default fallback): when {@code addresses} is blank or resolves
   *       to an empty list, broker hosts are discovered via the Camunda topology API. The
   *       monitoring port defaults to {@code 9600} and can be overridden via {@code
   *       camunda.connector.broker.monitoring.port}.
   * </ul>
   *
   * <p>Pre-existing, out-of-scope-for-#6961 limitation: in topology-discovery mode this still
   * resolves a single {@code CamundaClient} (whichever is {@code @Primary} when multiple physical
   * tenants are configured), so it only monitors that one physical tenant's brokers. Broker
   * monitoring is informational/observability only, not part of job execution, so this is
   * deliberately deferred rather than converted to a per-physical-tenant map.
   */
  @Bean
  @ConditionalOnProperty(
      name = "camunda.connector.broker.monitoring.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public BrokerJobStreamClient brokerJobStreamClient(
      CamundaClient camundaClient,
      @ConnectorsObjectMapper ObjectMapper mapper,
      @Value("${camunda.connector.broker.monitoring.port:9600}") int monitoringPort,
      @Value("${camunda.connector.broker.monitoring.addresses:#{null}}") String addresses) {
    if (StringUtils.isNotBlank(addresses)) {
      List<URI> uris =
          Arrays.stream(addresses.split(","))
              .map(String::trim)
              .filter(s -> !s.isBlank())
              .map(URI::create)
              .toList();
      if (!uris.isEmpty()) {
        return new BrokerJobStreamClient(uris, mapper);
      }
    }
    return new BrokerJobStreamClient(camundaClient, monitoringPort, mapper);
  }

  @Bean
  public OutboundConnectorsService outboundConnectorsService(
      OutboundConnectorFactory outboundConnectorConfigurationRegistry,
      @Autowired(required = false) BrokerJobStreamClient brokerJobStreamClient) {
    return new OutboundConnectorsService(
        outboundConnectorConfigurationRegistry, brokerJobStreamClient);
  }

  @Bean
  public CacheManager secretKeyCacheManager(
      @Value("${camunda.connector.secret-resolver.secret-filter.cache.enabled:true}")
          boolean cacheEnabled,
      @Value("${camunda.connector.secret-resolver.secret-filter.cache.max-size:1000}")
          int cacheMaxSize) {
    if (!cacheEnabled) {
      return new NoOpCacheManager();
    }
    int boundedMaxSize = cacheMaxSize > 0 ? cacheMaxSize : 1000;
    CaffeineCacheManager cacheManager =
        new CaffeineCacheManager(SecretKeyCache.SECRET_KEY_CACHE_NAME);
    cacheManager.setCaffeine(Caffeine.newBuilder().maximumSize(boundedMaxSize));
    return cacheManager;
  }

  @Bean
  public SecretKeyCache secretKeyCache(
      CamundaClient camundaClient, @Qualifier("secretKeyCacheManager") CacheManager cacheManager) {
    return new ProcessDefinitionSecretKeyCache(
        "default", camundaClient, cacheManager.getCache(SecretKeyCache.SECRET_KEY_CACHE_NAME));
  }

  @Bean
  public SecretFilterFactory secretFilterFactory(
      @Value("${camunda.connector.secret-resolver.secret-filter.mode:DISABLED}")
          SecretFilterMode secretFilterMode,
      SecretKeyCache secretKeyCache) {
    return new ConfigurableSecretFilterFactory(secretFilterMode, secretKeyCache);
  }

  private static Map<String, SecretKeyCache> buildSecretKeyCachesByPhysicalTenantId(
      CamundaClientRegistry registry,
      CamundaClient legacyCamundaClient,
      CacheManager cacheManager) {
    var sharedCache = cacheManager.getCache(SecretKeyCache.SECRET_KEY_CACHE_NAME);
    return clientNames(registry, legacyCamundaClient).stream()
        .collect(
            toMapByPhysicalTenantId(
                registry,
                legacyCamundaClient,
                name -> {
                  var physicalTenantId =
                      resolvePhysicalTenantId(registry, name, legacyCamundaClient);
                  return new ProcessDefinitionSecretKeyCache(
                      physicalTenantId,
                      resolveClient(registry, name, legacyCamundaClient),
                      sharedCache);
                }));
  }

  private static Map<String, SecretFilterFactory> buildSecretFilterFactoriesByPhysicalTenantId(
      CamundaClientRegistry registry,
      CamundaClient legacyCamundaClient,
      CacheManager cacheManager,
      SecretFilterMode secretFilterMode) {
    return buildSecretKeyCachesByPhysicalTenantId(registry, legacyCamundaClient, cacheManager)
        .entrySet()
        .stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                e -> new ConfigurableSecretFilterFactory(secretFilterMode, e.getValue())));
  }

  @Bean
  public Map<String, SecretKeyCache> secretKeyCachesByPhysicalTenantId(
      @Autowired(required = false) CamundaClientRegistry registry,
      @Autowired(required = false) CamundaClient legacyCamundaClient,
      @Qualifier("secretKeyCacheManager") CacheManager cacheManager) {
    return buildSecretKeyCachesByPhysicalTenantId(registry, legacyCamundaClient, cacheManager);
  }

  @Bean
  public Map<String, SecretFilterFactory> secretFilterFactoriesByPhysicalTenantId(
      @Autowired(required = false) CamundaClientRegistry registry,
      @Autowired(required = false) CamundaClient legacyCamundaClient,
      @Qualifier("secretKeyCacheManager") CacheManager cacheManager,
      @Value("${camunda.connector.secret-resolver.secret-filter.mode:DISABLED}")
          SecretFilterMode secretFilterMode) {
    return buildSecretFilterFactoriesByPhysicalTenantId(
        registry, legacyCamundaClient, cacheManager, secretFilterMode);
  }

  /**
   * Builds the per-physical-tenant {@code documentFactory}/{@code secretFilterFactory} maps via the
   * plain (non-{@code @Bean}) {@code build*} helper methods rather than declaring {@code
   * Map<String, X>}-typed parameters or calling the sibling {@code @Bean} methods: Spring's
   * {@code @Configuration} CGLIB proxying intercepts any call to a {@code @Bean} method — including
   * calls from within this very class — and re-resolves that method's parameters from the container
   * instead of using the arguments passed in code. Since a {@code Map<String, X>}-typed parameter
   * is itself special-cased by Spring to collect all beans of type {@code X} by name, and this
   * class also keeps legacy scalar {@code documentStore}/{@code documentFactory}/{@code
   * secretKeyCache} beans for backward compatibility, either path would silently resolve to a
   * single-entry map keyed by the legacy bean's name instead of the real per-physical-tenant map.
   */
  @Bean
  public OutboundConnectorManager outboundConnectorManager(
      JobWorkerManager jobWorkerManager,
      OutboundConnectorFactory connectorFactory,
      JobCallbackCommandWrapperFactory jobCallbackCommandWrapperFactory,
      SecretProviderAggregator secretProviderAggregator,
      ValidationProvider validationProvider,
      MetricsRecorder metricsRecorder,
      @Autowired(required = false) CamundaClientRegistry registry,
      @Autowired(required = false) CamundaClient legacyCamundaClient,
      @Value("${camunda.connector.secret-resolver.secret-filter.mode:DISABLED}")
          SecretFilterMode secretFilterMode,
      @Qualifier("secretKeyCacheManager") CacheManager secretKeyCacheManager,
      @OutboundConnectorObjectMapper ObjectMapper objectMapper,
      Optional<MeterRegistry> meterRegistry) {
    var documentFactoriesByPhysicalTenantId =
        buildDocumentFactoriesByPhysicalTenantId(registry, legacyCamundaClient);
    var secretFilterFactoriesByPhysicalTenantId =
        buildSecretFilterFactoriesByPhysicalTenantId(
            registry, legacyCamundaClient, secretKeyCacheManager, secretFilterMode);
    return new OutboundConnectorManager(
        jobWorkerManager,
        connectorFactory,
        jobCallbackCommandWrapperFactory,
        secretProviderAggregator,
        validationProvider,
        documentFactoriesByPhysicalTenantId,
        objectMapper,
        metricsRecorder,
        secretFilterFactoriesByPhysicalTenantId,
        meterRegistry.orElse(null));
  }
}
