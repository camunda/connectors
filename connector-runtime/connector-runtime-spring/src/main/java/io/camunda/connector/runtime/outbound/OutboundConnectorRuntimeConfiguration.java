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

import static io.camunda.connector.runtime.tenant.PhysicalTenantClients.clientNames;
import static io.camunda.connector.runtime.tenant.PhysicalTenantClients.resolveClient;
import static io.camunda.connector.runtime.tenant.PhysicalTenantClients.resolvePhysicalTenantId;
import static io.camunda.connector.runtime.tenant.PhysicalTenantClients.toMapByPhysicalTenantId;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.client.CamundaClient;
import io.camunda.client.jobhandling.JobCallbackCommandWrapperFactory;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.feel.FeelExpressionEvaluatorBuilder;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import io.camunda.connector.runtime.annotation.OutboundConnectorObjectMapper;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStoreImpl;
import io.camunda.connector.runtime.core.intrinsic.DefaultIntrinsicFunctionExecutor;
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
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
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

  /**
   * Tracks every connector instance created via {@code beanFactory.createBean(type)} in {@link
   * #functionRegistrations} / {@link #providerRegistrations}, so that {@link #destroyCreatedBeans}
   * can invoke {@code beanFactory.destroyBean(...)} on each of them when this configuration bean is
   * destroyed (i.e. on application context shutdown), running any {@code @PreDestroy}/{@link
   * org.springframework.beans.factory.DisposableBean} cleanup that would otherwise never run since
   * these instances are not registered with the container.
   */
  private final List<Object> createdConnectorInstances =
      new java.util.concurrent.CopyOnWriteArrayList<>();

  /**
   * Set by {@link #functionRegistrations}/{@link #providerRegistrations}; used by {@link
   * #destroyCreatedBeans} to destroy the tracked instances on shutdown.
   */
  private volatile ConfigurableListableBeanFactory beanFactory;

  /**
   * Builds one {@link DefaultOutboundConnectorFactory.FunctionRegistration} per Spring-registered
   * {@link OutboundConnectorFunction} bean, backed by a supplier that yields a fresh instance per
   * call regardless of the bean's declared Spring scope (#6961):
   *
   * <ul>
   *   <li>Prototype-scoped beans are resolved via {@code beanFactory.getBean(name, type)}, which
   *       already returns a brand-new instance on every call while going through the bean's real,
   *       originating bean definition — i.e. any {@code @Bean} factory method (with whatever custom
   *       construction logic it contains) or {@code @Component}-scanned constructor is honored
   *       exactly as Spring would normally invoke it.
   *   <li>(Default) singleton-scoped beans instead use {@code beanFactory.createBean(type)}, since
   *       {@code getBean(name, ...)} would return the same cached singleton instance every time.
   *       This is the only public Spring API able to produce a distinct instance per call for a
   *       singleton-scoped bean definition, but it comes with a caveat: it constructs a new,
   *       autowired instance purely from the bean's {@code Class}, bypassing any {@code @Bean}
   *       factory method the bean might have originally been defined with (i.e. any custom
   *       construction logic in that method's body is skipped in favor of straightforward
   *       constructor autowiring). Connector authors relying on such custom singleton-scoped
   *       {@code @Bean} construction logic should mark the bean {@code @Scope("prototype")}
   *       instead, so {@code getBean(name, type)} is used and the factory method is preserved.
   * </ul>
   *
   * <p>Instances created via {@code createBean(type)} are not registered with the container, so
   * they're tracked in {@link #createdConnectorInstances} and explicitly destroyed via {@link
   * #destroyCreatedBeans}. Instances resolved via {@code getBean(name, type)} are already
   * container-managed and need no such tracking.
   */
  private List<DefaultOutboundConnectorFactory.FunctionRegistration> functionRegistrations(
      ConfigurableListableBeanFactory beanFactory) {
    this.beanFactory = beanFactory;
    return Arrays.stream(beanFactory.getBeanNamesForType(OutboundConnectorFunction.class))
        .map(
            name -> {
              var type = resolveConcreteType(beanFactory, name, OutboundConnectorFunction.class);
              return new DefaultOutboundConnectorFactory.FunctionRegistration(
                  type, () -> resolveFreshInstance(beanFactory, name, type));
            })
        .toList();
  }

  /** See {@link #functionRegistrations}; the equivalent for {@link OutboundConnectorProvider}. */
  private List<DefaultOutboundConnectorFactory.ProviderRegistration> providerRegistrations(
      ConfigurableListableBeanFactory beanFactory) {
    this.beanFactory = beanFactory;
    return Arrays.stream(beanFactory.getBeanNamesForType(OutboundConnectorProvider.class))
        .map(
            name -> {
              var type = resolveConcreteType(beanFactory, name, OutboundConnectorProvider.class);
              return new DefaultOutboundConnectorFactory.ProviderRegistration(
                  type, () -> resolveFreshInstance(beanFactory, name, type));
            })
        .toList();
  }

  /**
   * Resolves the concrete implementation class to use for {@code @OutboundConnector} annotation
   * discovery for the given bean {@code name}. {@code beanFactory.getType(name)} returns the
   * bean-definition-declared type, which for a {@code @Bean} factory method is that method's
   * declared return type — e.g. plain {@code OutboundConnectorFunction}/{@code
   * OutboundConnectorProvider} for a method like {@code @Bean OutboundConnectorFunction
   * myConnector() { return new MyConnectorImpl(); }}. That declared type never carries the
   * {@code @OutboundConnector} annotation (only the concrete {@code MyConnectorImpl} does), so
   * relying on it would silently filter such connectors out entirely.
   *
   * <p>Falls back to instantiating the bean once via {@code getBean(name)} to discover its real
   * runtime class, mirroring the pre-#6961 behavior (list-injecting {@code
   * List<OutboundConnectorFunction>} beans and calling {@code instance.getClass()}), only when the
   * declared type doesn't already carry the annotation.
   */
  private <T> Class<? extends T> resolveConcreteType(
      ConfigurableListableBeanFactory beanFactory, String name, Class<T> supertype) {
    Class<?> declaredType = beanFactory.getType(name);
    if (declaredType != null && declaredType.isAnnotationPresent(OutboundConnector.class)) {
      return declaredType.asSubclass(supertype);
    }
    return beanFactory.getBean(name, supertype).getClass().asSubclass(supertype);
  }

  /**
   * Resolves a fresh instance of the given bean {@code name}/{@code type} per call: via {@code
   * getBean(name, type)} for prototype-scoped beans (preserving the original bean definition, e.g.
   * a {@code @Bean} factory method), or via {@code createBean(type)} for singleton-scoped beans
   * (the only way to get a distinct instance per call, at the cost of bypassing any factory
   * method). See {@link #functionRegistrations} for the full rationale.
   */
  private <T> T resolveFreshInstance(
      ConfigurableListableBeanFactory beanFactory, String name, Class<T> type) {
    if (beanFactory.isPrototype(name)) {
      return beanFactory.getBean(name, type);
    }
    T instance = beanFactory.createBean(type);
    createdConnectorInstances.add(instance);
    return instance;
  }

  /**
   * Invokes {@code beanFactory.destroyBean(...)} on every connector instance tracked in {@link
   * #createdConnectorInstances}, running any {@code @PreDestroy}/{@link
   * org.springframework.beans.factory.DisposableBean} cleanup on them. Called when this
   * configuration bean is destroyed, i.e. on application context shutdown.
   */
  @PreDestroy
  public void destroyCreatedBeans() {
    if (beanFactory != null) {
      createdConnectorInstances.forEach(beanFactory::destroyBean);
    }
    createdConnectorInstances.clear();
  }

  @Bean
  @ConditionalOnMissingBean(OutboundConnectorFactory.class)
  public DefaultOutboundConnectorFactory outboundConnectorConfigurationRegistry(
      @ConnectorsObjectMapper ObjectMapper mapper,
      ValidationProvider validationProvider,
      Environment environment,
      ConfigurableListableBeanFactory beanFactory) {

    return DefaultOutboundConnectorFactory.fromRegistrations(
        mapper,
        validationProvider,
        functionRegistrations(beanFactory),
        providerRegistrations(beanFactory),
        environment::getProperty);
  }

  @Bean
  public CamundaDocumentStore documentStore(CamundaClient camundaClient) {
    return new CamundaDocumentStoreImpl(
        camundaClient, readPhysicalTenantIdIfAvailable(camundaClient));
  }

  /**
   * Reads the client's configured physical tenant ID, tolerating the case where its configuration
   * cannot be read at all — some test doubles (e.g. the {@code camunda-process-test-spring} client
   * proxy) defer real initialization until the test container is ready and throw if queried during
   * Spring context startup. Returning {@code null} here simply means the resulting {@link
   * CamundaDocumentStoreImpl} skips its physical-tenant sanity check, rather than failing startup.
   */
  private static String readPhysicalTenantIdIfAvailable(CamundaClient camundaClient) {
    try {
      return camundaClient.getConfiguration().getPhysicalTenantId();
    } catch (RuntimeException e) {
      return null;
    }
  }

  @Bean
  public DocumentFactory documentFactory(CamundaDocumentStore documentStore) {
    return new DocumentFactoryImpl(documentStore);
  }

  // clientNames / resolveClient / resolvePhysicalTenantId / toMapByPhysicalTenantId live in
  // PhysicalTenantClients: the same four helpers are needed by the direction-agnostic
  // ConfigurationValidationConfiguration, which builds one FEEL evaluator per physical tenant.

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
                        resolveClient(registry, name, legacyCamundaClient),
                        resolvePhysicalTenantId(registry, name, legacyCamundaClient))));
  }

  /**
   * Builds the per-physical-tenant {@link DocumentFactory} map, for the outbound path. In the
   * single-physical-tenant case, the already-resolved {@code injectedDocumentFactory} bean is
   * reused instead of always constructing a new client-backed one: this preserves pre-#6961
   * behavior for single-client/custom runtimes that override the {@code documentFactory} bean (e.g.
   * an in-memory document store in tests), which would otherwise be silently bypassed. This must
   * check the actual client count rather than {@code registry == null}: {@link
   * CamundaClientRegistry} is registered unconditionally by the camunda-client Spring Boot starter,
   * so it is never actually null. The inbound path has its own equivalent, {@code
   * PhysicalTenantIds#buildDocumentFactoriesByPhysicalTenantId} in {@code
   * connector-runtime/connector-runtime-spring/.../inbound/}, using the same condition.
   */
  private static Map<String, DocumentFactory> buildDocumentFactoriesByPhysicalTenantId(
      CamundaClientRegistry registry,
      CamundaClient legacyCamundaClient,
      DocumentFactory injectedDocumentFactory) {
    if (injectedDocumentFactory != null && clientNames(registry, legacyCamundaClient).size() <= 1) {
      return clientNames(registry, legacyCamundaClient).stream()
          .collect(
              toMapByPhysicalTenantId(
                  registry, legacyCamundaClient, name -> injectedDocumentFactory));
    }
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
      @Autowired(required = false) CamundaClient legacyCamundaClient,
      @Autowired(required = false) DocumentFactory documentFactory) {
    return buildDocumentFactoriesByPhysicalTenantId(registry, legacyCamundaClient, documentFactory);
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
   * <p>This scalar bean monitors the brokers of a single {@code CamundaClient} (whichever is
   * {@code @Primary} when several physical tenants are configured) and is kept for backward
   * compatibility with runtimes injecting/overriding it directly. The {@code /outbound} endpoints
   * instead go through the per-physical-tenant map built by {@link
   * #buildBrokerJobStreamClientsByPhysicalTenantId}, which monitors every engine's brokers.
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
    List<URI> uris = parseMonitoringAddresses(addresses);
    if (!uris.isEmpty()) {
      return new BrokerJobStreamClient(uris, mapper);
    }
    return new BrokerJobStreamClient(camundaClient, monitoringPort, mapper);
  }

  private static List<URI> parseMonitoringAddresses(String addresses) {
    if (StringUtils.isBlank(addresses)) {
      return List.of();
    }
    return Arrays.stream(addresses.split(","))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .map(URI::create)
        .toList();
  }

  /**
   * Resolves every configured physical tenant (engine) ID, sorted for a stable {@code /outbound}
   * listing order.
   */
  private static List<String> physicalTenantIds(
      CamundaClientRegistry registry, CamundaClient legacyCamundaClient) {
    return clientNames(registry, legacyCamundaClient).stream()
        .map(name -> resolvePhysicalTenantId(registry, name, legacyCamundaClient))
        .distinct()
        .sorted()
        .toList();
  }

  /**
   * Builds one {@link BrokerJobStreamClient} per configured physical tenant, so that {@code
   * /outbound} can report each engine's own broker/stream connectivity (#7965).
   *
   * <p>{@code injectedBrokerJobStreamClient} doubles as the on/off switch: it is {@code null}
   * exactly when broker monitoring is disabled (its bean is {@code @ConditionalOnProperty}-gated),
   * in which case no client is built at all and every connector is reported with an {@code UNKNOWN}
   * connectivity state, as before.
   *
   * <p>With a single physical tenant, the already-resolved scalar bean is reused as-is, preserving
   * behavior (including any override) for existing single-engine deployments. In explicit-addresses
   * mode the configured addresses are global rather than per-engine, so the same client — querying
   * that same broker list — is shared by every physical tenant; only topology-discovery mode
   * resolves each engine's brokers separately, through that engine's own {@code CamundaClient}.
   *
   * <p>Deliberately a plain (non-{@code @Bean}) static method: see {@link
   * #buildOutboundConnectorObjectMappersByPhysicalTenantId} for why {@code Map<String,X>}-typed
   * {@code @Bean} methods/parameters are avoided in this class.
   */
  private static Map<String, BrokerJobStreamClient> buildBrokerJobStreamClientsByPhysicalTenantId(
      CamundaClientRegistry registry,
      CamundaClient legacyCamundaClient,
      BrokerJobStreamClient injectedBrokerJobStreamClient,
      ObjectMapper mapper,
      int monitoringPort,
      String addresses) {
    if (injectedBrokerJobStreamClient == null) {
      return Map.of();
    }
    boolean shared =
        clientNames(registry, legacyCamundaClient).size() <= 1
            || !parseMonitoringAddresses(addresses).isEmpty();
    return clientNames(registry, legacyCamundaClient).stream()
        .collect(
            toMapByPhysicalTenantId(
                registry,
                legacyCamundaClient,
                name ->
                    shared
                        ? injectedBrokerJobStreamClient
                        : new BrokerJobStreamClient(
                            resolveClient(registry, name, legacyCamundaClient),
                            monitoringPort,
                            mapper)));
  }

  @Bean
  public OutboundConnectorsService outboundConnectorsService(
      OutboundConnectorFactory outboundConnectorConfigurationRegistry,
      @Autowired(required = false) CamundaClientRegistry registry,
      @Autowired(required = false) CamundaClient legacyCamundaClient,
      @Autowired(required = false) BrokerJobStreamClient brokerJobStreamClient,
      @ConnectorsObjectMapper ObjectMapper mapper,
      @Value("${camunda.connector.broker.monitoring.port:9600}") int monitoringPort,
      @Value("${camunda.connector.broker.monitoring.addresses:#{null}}") String addresses) {
    return new OutboundConnectorsService(
        outboundConnectorConfigurationRegistry,
        physicalTenantIds(registry, legacyCamundaClient),
        buildBrokerJobStreamClientsByPhysicalTenantId(
            registry,
            legacyCamundaClient,
            brokerJobStreamClient,
            mapper,
            monitoringPort,
            addresses));
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

  /**
   * Resolves the physical tenant ID for the legacy scalar single-{@code CamundaClient} beans (
   * {@link #secretKeyCache}): the explicitly configured {@code physical-tenant-id} if present,
   * otherwise falls back to {@code "default"} rather than the client name (there is no client name
   * in the single-client case). Falls back the same way if the configuration cannot be read at all
   * — some test doubles defer real initialization until a test container is ready and throw if
   * queried too early.
   */
  private static String resolvePhysicalTenantIdOrDefault(CamundaClient camundaClient) {
    try {
      var physicalTenantId = camundaClient.getConfiguration().getPhysicalTenantId();
      return physicalTenantId != null ? physicalTenantId : "default";
    } catch (RuntimeException e) {
      return "default";
    }
  }

  @Bean
  public SecretKeyCache secretKeyCache(
      CamundaClient camundaClient, @Qualifier("secretKeyCacheManager") CacheManager cacheManager) {
    return new ProcessDefinitionSecretKeyCache(
        resolvePhysicalTenantIdOrDefault(camundaClient),
        camundaClient,
        cacheManager.getCache(SecretKeyCache.SECRET_KEY_CACHE_NAME));
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
    // Resolved once per client name (rather than via toMapByPhysicalTenantId, which would
    // recompute the same physical tenant ID a second time inside the value-mapper) and reused as
    // both the map key and the cache's own id.
    return clientNames(registry, legacyCamundaClient).stream()
        .map(
            name ->
                Map.entry(
                    resolvePhysicalTenantId(registry, name, legacyCamundaClient),
                    resolveClient(registry, name, legacyCamundaClient)))
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                e -> new ProcessDefinitionSecretKeyCache(e.getKey(), e.getValue(), sharedCache),
                (a, b) -> {
                  throw new IllegalStateException(
                      "Multiple CamundaClients resolve to the same physical tenant ID; "
                          + "each configured client must have a unique physical-tenant-id");
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
      @Autowired(required = false) DocumentFactory documentFactory,
      @Value("${camunda.connector.secret-resolver.secret-filter.mode:DISABLED}")
          SecretFilterMode secretFilterMode,
      @Qualifier("secretKeyCacheManager") CacheManager secretKeyCacheManager,
      @OutboundConnectorObjectMapper ObjectMapper outboundConnectorObjectMapper,
      Optional<MeterRegistry> meterRegistry) {
    var documentFactoriesByPhysicalTenantId =
        buildDocumentFactoriesByPhysicalTenantId(registry, legacyCamundaClient, documentFactory);
    var secretFilterFactoriesByPhysicalTenantId =
        buildSecretFilterFactoriesByPhysicalTenantId(
            registry, legacyCamundaClient, secretKeyCacheManager, secretFilterMode);
    var objectMappersByPhysicalTenantId =
        buildOutboundConnectorObjectMappersByPhysicalTenantId(
            documentFactoriesByPhysicalTenantId, outboundConnectorObjectMapper);
    return new OutboundConnectorManager(
        jobWorkerManager,
        connectorFactory,
        jobCallbackCommandWrapperFactory,
        secretProviderAggregator,
        validationProvider,
        documentFactoriesByPhysicalTenantId,
        objectMappersByPhysicalTenantId,
        metricsRecorder,
        secretFilterFactoriesByPhysicalTenantId,
        meterRegistry.orElse(null));
  }

  /**
   * Per-physical-tenant outbound {@link ObjectMapper}s, each wired to that tenant's {@link
   * DocumentFactory} so that {@code Document}-typed job variables are deserialized through the
   * correct engine's document store (see #6961) instead of always going through a single
   * globally-cached mapper. In the single-physical-tenant case, the already-built {@code
   * outboundConnectorObjectMapper} instance (injected above) is reused as-is: this mirrors {@link
   * #buildDocumentFactoriesByPhysicalTenantId}'s own condition for reusing the injected {@link
   * DocumentFactory} bean in that case — {@code documentFactoriesByPhysicalTenantId} has exactly
   * one entry, and it is (by construction) the very same {@code DocumentFactory} instance that
   * {@code outboundConnectorObjectMapper} was built from — so a custom/overridden {@code
   * DocumentFactory} bean (e.g. an in-memory one in tests) and the {@code ObjectMapper} that
   * deserializes {@code Document}-typed variables stay backed by the same factory instance.
   *
   * <p>Deliberately a plain (non-{@code @Bean}) static method rather than a {@code Map<String,
   * ObjectMapper>}-typed {@code @Bean}: as documented above for the document/secret-filter maps,
   * Spring special-cases {@code Map<String, X>}-typed {@code @Bean} parameters to auto-collect all
   * beans of type {@code X} keyed by bean name instead of resolving a single matching {@code Map}
   * bean — which would silently yield a map keyed by bean names like {@code
   * "outboundConnectorObjectMapper"} instead of physical tenant IDs.
   */
  private static Map<String, ObjectMapper> buildOutboundConnectorObjectMappersByPhysicalTenantId(
      Map<String, DocumentFactory> documentFactoriesByPhysicalTenantId,
      ObjectMapper outboundConnectorObjectMapper) {
    if (documentFactoriesByPhysicalTenantId.size() <= 1) {
      return documentFactoriesByPhysicalTenantId.keySet().stream()
          .collect(Collectors.toMap(id -> id, id -> outboundConnectorObjectMapper));
    }
    return documentFactoriesByPhysicalTenantId.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, e -> buildOutboundConnectorObjectMapper(e.getValue())));
  }

  private static ObjectMapper buildOutboundConnectorObjectMapper(DocumentFactory documentFactory) {
    final ObjectMapper copy = ConnectorsObjectMapperSupplier.getCopy();
    var functionExecutor = new DefaultIntrinsicFunctionExecutor(copy);

    var jacksonModuleDocumentDeserializer =
        new JacksonModuleDocumentDeserializer(
            documentFactory,
            functionExecutor,
            JacksonModuleDocumentDeserializer.DocumentModuleSettings.create());

    return copy.registerModules(
        jacksonModuleDocumentDeserializer,
        new JacksonModuleFeelFunction(
            false,
            FeelExpressionEvaluatorBuilder.local().build()), // FEEL annotation processing disabled
        new JacksonModuleDocumentSerializer());
  }
}
