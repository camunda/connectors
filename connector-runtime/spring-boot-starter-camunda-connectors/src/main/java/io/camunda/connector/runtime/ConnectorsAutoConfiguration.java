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
package io.camunda.connector.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.client.spring.configuration.CamundaAutoConfiguration;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.feel.FeelExpressionEvaluatorBuilder;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.feel.jackson.JacksonModuleSecretReference;
import io.camunda.connector.hostvalidator.CidrRange;
import io.camunda.connector.hostvalidator.VerifiedHostValidator;
import io.camunda.connector.http.client.authentication.OAuthTokenCache;
import io.camunda.connector.http.client.authentication.OAuthTokenCacheHolder;
import io.camunda.connector.http.client.authentication.cacheimpl.CaffeineOAuthTokenCache;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import io.camunda.connector.runtime.annotation.OutboundConnectorObjectMapper;
import io.camunda.connector.runtime.core.FeelEvaluationResultMapper;
import io.camunda.connector.runtime.core.intrinsic.DefaultIntrinsicFunctionExecutor;
import io.camunda.connector.runtime.core.secret.CentralStoreSecretProvider;
import io.camunda.connector.runtime.core.secret.LegacySecretMode;
import io.camunda.connector.runtime.core.secret.LegacySecretsDisabledProvider;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.core.secret.SecretProviderDiscovery;
import io.camunda.connector.runtime.core.secret.SecretReferenceResolver;
import io.camunda.connector.runtime.inbound.PhysicalTenantIds;
import io.camunda.connector.runtime.metrics.MeteredSecretProviderAggregator;
import io.camunda.connector.runtime.outbound.job.ConfigurableSecretFilterFactory.SecretFilterMode;
import io.camunda.connector.runtime.secret.ConsoleSecretProvider;
import io.camunda.connector.runtime.secret.EnvironmentSecretProvider;
import io.camunda.connector.runtime.secret.console.ConsoleSecretApiClient;
import io.camunda.connector.runtime.secret.console.JwtCredential;
import io.camunda.connector.runtime.tenant.PhysicalTenantClients;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.Validation;
import java.net.URL;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;

@AutoConfiguration
@AutoConfigureBefore({
  OutboundConnectorsAutoConfiguration.class,
  InboundConnectorsAutoConfiguration.class,
  CamundaAutoConfiguration.class
})
// Configuration (credential) validation is direction-agnostic, so it is wired here in the neutral
// runtime auto-configuration rather than the outbound-specific one.
@Import(io.camunda.connector.runtime.configuration.ConfigurationValidationConfiguration.class)
@EnableConfigurationProperties(ConnectorProperties.class)
public class ConnectorsAutoConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectorsAutoConfiguration.class);

  // Shared by the aggregator bean's name and by the guard that detects a replacement for it, so
  // the two cannot drift apart.
  static final String DEFAULT_AGGREGATOR_BEAN_NAME = "springSecretProviderAggregator";

  private final ObjectProvider<OAuthTokenCache> oAuthTokenCacheProvider;

  @Value("${camunda.connector.secretprovider.discovery.enabled:true}")
  Boolean secretProviderLookupEnabled;

  @Value("${camunda.connector.secretprovider.environment.prefix:SECRET_}")
  String environmentSecretProviderPrefix;

  @Value("${camunda.connector.secretprovider.environment.physicaltenantaware:false}")
  boolean environmentSecretProviderPhysicalTenantAware;

  @Value("${camunda.connector.secretprovider.environment.tenantaware:false}")
  boolean environmentSecretProviderTenantAware;

  @Value("${camunda.connector.secretprovider.environment.processdefinitionaware:false}")
  boolean environmentSecretProviderProcessDefinitionAware;

  @Value(
      "${camunda.connector.secretprovider.console.endpoint:https://cluster-api.cloud.camunda.io/secrets}")
  String consoleSecretsApiEndpoint;

  @Value("${camunda.connector.secretprovider.console.audience:secrets.camunda.io}")
  String consoleSecretsApiAudience;

  public ConnectorsAutoConfiguration(ObjectProvider<OAuthTokenCache> oAuthTokenCacheProvider) {
    this.oAuthTokenCacheProvider = oAuthTokenCacheProvider;
  }

  /**
   * Provides a {@link FeelExpressionEvaluator} unless already present in the Spring Context. Uses
   * cluster-based evaluation (enabling access to cluster variables like {@code
   * camunda.vars.env.*}).
   */
  @Bean
  @Primary
  @ConditionalOnMissingBean(FeelExpressionEvaluator.class)
  public FeelExpressionEvaluator camundaClientFeelExpressionEvaluator(CamundaClient camundaClient) {
    return FeelExpressionEvaluatorBuilder.camundaClient(camundaClient).build();
  }

  /**
   * Initializes and exposes the shared {@link OAuthTokenCache}, configured from {@code
   * camunda.connector.oauth.cache.skew-buffer} property.
   *
   * <p>The cache instance is also registered in {@link OAuthTokenCacheHolder} so that non-Spring
   * HTTP client code (which cannot use dependency injection) can access it.
   *
   * <p>Users can replace this bean by defining their own {@link OAuthTokenCache} bean. Custom
   * implementations will be picked up both by the Spring context and by the HTTP client via the
   * holder.
   */
  @Bean
  @ConditionalOnMissingBean(OAuthTokenCache.class)
  public OAuthTokenCache oAuthTokenCache(ConnectorProperties properties) {
    var cacheProps = properties.oauth() != null ? properties.oauth().cache() : null;
    Duration skewBuffer = cacheProps != null ? cacheProps.skewBuffer() : null;
    OAuthTokenCache cache = CaffeineOAuthTokenCache.initialize(skewBuffer);
    OAuthTokenCacheHolder.set(cache);
    return cache;
  }

  /**
   * Builds the aggregator every legacy ({@code {{secrets.X}}} and bare {@code secrets.X}) lookup
   * goes through; outbound, inbound and configuration validation all share this one bean. Under
   * {@link LegacySecretMode#OFF} none of the configured providers is consulted and every lookup
   * fails instead.
   *
   * <p>This has no effect on {@code camunda.secrets.<name>} resolution, which reads the
   * orchestration cluster's secret stores through a separate mechanism.
   */
  @Bean(DEFAULT_AGGREGATOR_BEAN_NAME)
  @ConditionalOnMissingBean
  public SecretProviderAggregator springSecretProviderAggregator(
      Optional<List<SecretProvider>> secretProviderBeans,
      @Value("${" + LegacySecretsDisabledProvider.PROPERTY + ":ON}") LegacySecretMode legacyMode,
      @Autowired(required = false) CamundaClientRegistry registry,
      @Autowired(required = false) CamundaClient legacyCamundaClient,
      @Autowired(required = false) MeterRegistry meterRegistry) {
    if (legacyMode == LegacySecretMode.OFF) {
      LOG.info(
          "Legacy secret resolution is disabled ({}={}); {{secrets.X}} and secrets.X will not"
              + " resolve.",
          LegacySecretsDisabledProvider.PROPERTY,
          LegacySecretMode.OFF);
      return new SecretProviderAggregator(List.of(new LegacySecretsDisabledProvider()));
    }
    var secretProviders = secretProviderBeans.orElseGet(LinkedList::new);
    LOG.debug("Using secret providers discovered as Spring beans: {}", secretProviderBeans);
    if (secretProviderLookupEnabled != Boolean.FALSE) {
      var discoveredSecretProviders = SecretProviderDiscovery.discoverSecretProviders();
      LOG.debug("Using secret providers discovered by lookup: {}", discoveredSecretProviders);
      secretProviders.addAll(discoveredSecretProviders);
    }
    if (legacyMode == LegacySecretMode.FALLBACK) {
      // Last in the chain: a name a configured provider holds still comes from there, so moving
      // values into the central store one at a time works without touching any diagram.
      LOG.info(
          "Legacy secret names not held by any configured provider will be read from the cluster's"
              + " secret stores ({}={})",
          LegacySecretsDisabledProvider.PROPERTY,
          LegacySecretMode.FALLBACK);
      secretProviders.add(
          new CentralStoreSecretProvider(
              secretReferenceResolversByPhysicalTenantId(registry, legacyCamundaClient)));
    }
    return meterRegistry == null
        ? new SecretProviderAggregator(secretProviders)
        : new MeteredSecretProviderAggregator(secretProviders, meterRegistry);
  }

  private static Map<String, SecretReferenceResolver> secretReferenceResolversByPhysicalTenantId(
      CamundaClientRegistry registry, CamundaClient legacyCamundaClient) {
    return PhysicalTenantClients.clientNames(registry, legacyCamundaClient).stream()
        .collect(
            PhysicalTenantClients.toMapByPhysicalTenantId(
                registry,
                legacyCamundaClient,
                name ->
                    new SecretReferenceResolver(
                        PhysicalTenantClients.resolveClient(registry, name, legacyCamundaClient))));
  }

  /**
   * Refuses to start under {@link LegacySecretMode#FALLBACK} unless the outbound secret filter is
   * strict.
   *
   * <p>The fallback lets the legacy syntax reach the cluster's secret stores, and the only thing
   * restricting which secrets a legacy reference may name is that filter. It ships disabled, and
   * its lax setting resolves everything whenever the process-definition lookup fails. Pairing the
   * two is a deployment invariant either way; refusing to start makes it one the runtime enforces
   * rather than one a runbook describes.
   */
  @Bean
  public Object legacyFallbackSecretFilterGuard(
      @Value("${" + LegacySecretsDisabledProvider.PROPERTY + ":ON}") LegacySecretMode legacyMode,
      @Value("${camunda.connector.secret-resolver.secret-filter.mode:DISABLED}")
          SecretFilterMode secretFilterMode) {
    if (legacyMode == LegacySecretMode.FALLBACK && secretFilterMode != SecretFilterMode.STRICT) {
      throw new IllegalStateException(
          LegacySecretsDisabledProvider.PROPERTY
              + "="
              + LegacySecretMode.FALLBACK
              + " requires camunda.connector.secret-resolver.secret-filter.mode="
              + SecretFilterMode.STRICT
              + ", but it is "
              + secretFilterMode
              + ". The fallback lets a legacy secret reference read the cluster's secret stores,"
              + " and the secret filter is what restricts which secrets a process may name.");
    }
    return new Object();
  }

  /**
   * Refuses to start when legacy secret resolution is switched off but the application supplies its
   * own {@link SecretProviderAggregator}. Such a bean replaces {@link
   * #springSecretProviderAggregator} outright, since that one exists only through
   * {@code @ConditionalOnMissingBean}, so the setting would be silently ignored rather than
   * enforced. This bean carries no conditions of its own, so it runs whichever aggregator won.
   */
  @Bean
  public Object secretProviderAggregatorLegacySwitchGuard(
      ApplicationContext applicationContext,
      @Value("${" + LegacySecretsDisabledProvider.PROPERTY + ":ON}") LegacySecretMode legacyMode) {
    if (legacyMode != LegacySecretMode.OFF) {
      return new Object();
    }
    List<String> replacements =
        Arrays.stream(applicationContext.getBeanNamesForType(SecretProviderAggregator.class))
            .filter(name -> !DEFAULT_AGGREGATOR_BEAN_NAME.equals(name))
            .toList();
    if (!replacements.isEmpty()) {
      throw new IllegalStateException(
          LegacySecretsDisabledProvider.PROPERTY
              + "="
              + LegacySecretMode.OFF
              + " cannot be enforced: the application supplies its own SecretProviderAggregator"
              + " bean "
              + replacements
              + ", which replaces the one that applies the setting. Remove that bean, or set the"
              + " mode back to "
              + LegacySecretMode.ON
              + ".");
    }
    return new Object();
  }

  @Bean
  @ConditionalOnProperty(
      name = "camunda.connector.secretprovider.environment.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public EnvironmentSecretProvider defaultSecretProvider(Environment environment) {
    return new EnvironmentSecretProvider(
        environment,
        environmentSecretProviderPrefix,
        environmentSecretProviderPhysicalTenantAware,
        environmentSecretProviderTenantAware,
        environmentSecretProviderProcessDefinitionAware);
  }

  @Bean
  @ConditionalOnProperty(
      name = "camunda.connector.secretprovider.console.enabled",
      havingValue = "true")
  public ConsoleSecretProvider consoleSecretProvider(
      ConsoleSecretApiClient consoleSecretApiClient) {
    return new ConsoleSecretProvider(consoleSecretApiClient, Duration.ofSeconds(20));
  }

  @Bean
  @ConditionalOnProperty(
      name = "camunda.connector.secretprovider.console.enabled",
      havingValue = "true")
  public ConsoleSecretApiClient consoleSecretApiClient(CamundaClientProperties clientProperties) {

    if (!clientProperties.getMode().equals(CamundaClientProperties.ClientMode.saas)) {
      throw new RuntimeException(
          "Console Secrets require a SaaS environment, but the client is configured for "
              + clientProperties.getMode());
    }

    var authProperties = clientProperties.getAuth();
    URL issuerUrl;
    try {
      issuerUrl = authProperties.getTokenUrl().toURL();
    } catch (Exception e) {
      throw new RuntimeException("Invalid token URL: " + authProperties.getTokenUrl(), e);
    }

    var jwtCredential =
        new JwtCredential(
            authProperties.getClientId(),
            authProperties.getClientSecret(),
            consoleSecretsApiAudience,
            issuerUrl,
            null);
    return new ConsoleSecretApiClient(consoleSecretsApiEndpoint, jwtCredential);
  }

  @Bean(name = "camundaJsonMapper")
  @ConditionalOnMissingBean
  public CamundaObjectMapper jsonMapper() {
    return new CamundaObjectMapper(
        ConnectorsObjectMapperSupplier.getCopy()
            .registerModules(
                new JacksonModuleFeelFunction(
                    true,
                    FeelExpressionEvaluatorBuilder.local().build(),
                    null,
                    FeelEvaluationResultMapper.create()),
                new JacksonModuleDocumentSerializer()));
  }

  @Bean(defaultCandidate = false)
  @ConnectorsObjectMapper
  @ConditionalOnMissingBean(name = "connectorObjectMapper")
  public ObjectMapper connectorObjectMapper(
      CamundaClientRegistry registry,
      @Autowired(required = false) CamundaClient legacyCamundaClient,
      DocumentFactory legacyDocumentFactory,
      FeelExpressionEvaluator feelExpressionEvaluator) {
    final ObjectMapper copy = ConnectorsObjectMapperSupplier.getCopy();
    // default intrinsic function contains a pointer of the copy
    var functionExecutor = new DefaultIntrinsicFunctionExecutor(copy);

    // The deserializer module contains the function executor, which contains the pointer of the
    // object mapper
    var documentFactoriesByPhysicalTenantId =
        PhysicalTenantIds.buildDocumentFactoriesByPhysicalTenantId(
            registry, legacyCamundaClient, legacyDocumentFactory);
    var jacksonModuleDocumentDeserializer =
        new JacksonModuleDocumentDeserializer(
            documentFactoriesByPhysicalTenantId,
            functionExecutor,
            JacksonModuleDocumentDeserializer.DocumentModuleSettings.create());

    // Function/Supplier always use local evaluation to avoid serializing runtime objects
    // (e.g., Documents) to the cluster. The injected evaluator is used for @FEEL-annotated fields.
    // Values returned by an evaluation are bound by a mapper of their own, which registers neither
    // of the two modules below, so no string in a result is treated as expression source.
    return copy.registerModules(
        jacksonModuleDocumentDeserializer,
        new JacksonModuleFeelFunction(
            true,
            feelExpressionEvaluator,
            FeelExpressionEvaluatorBuilder.local().build(),
            FeelEvaluationResultMapper.create(documentFactoriesByPhysicalTenantId)),
        new JacksonModuleSecretReference(),
        new JacksonModuleDocumentSerializer());
  }

  /**
   * ObjectMapper for OutboundConnectorManager with FEEL annotation processing disabled. This
   * prevents {@code @FEEL}-annotated properties from being evaluated as FEEL expressions during
   * outbound connector variable binding, which would otherwise conflict with other modules (e.g.
   * the document module) and can prevent the correct deserializer from being picked. {@code @FEEL}
   * is not relevant for outbound connectors anyway, as FEEL for jobs is evaluated by Zeebe.
   */
  @Bean(defaultCandidate = false)
  @OutboundConnectorObjectMapper
  @ConditionalOnMissingBean(name = "outboundConnectorObjectMapper")
  public ObjectMapper outboundConnectorObjectMapper(DocumentFactory documentFactory) {
    return buildOutboundConnectorObjectMapper(documentFactory);
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
            FeelExpressionEvaluatorBuilder.local().build(), // FEEL annotation processing disabled
            null,
            FeelEvaluationResultMapper.create(documentFactory)),
        new JacksonModuleDocumentSerializer());
  }

  @Scheduled(fixedRate = 60_000, initialDelay = 60_000)
  public void logOAuthTokenCacheStats() {
    if (!LOG.isDebugEnabled()) {
      return;
    }

    OAuthTokenCache cache = oAuthTokenCacheProvider.getIfAvailable(OAuthTokenCacheHolder::get);
    LOG.debug("OAuth token cache stats: {}", cache.getStats());
  }

  @Bean
  @ConditionalOnMissingBean(ValidationProvider.class)
  VerifiedHostValidator verifiedHostValidator(ConnectorProperties connectorProperties) {
    var validation =
        Optional.ofNullable(connectorProperties.validation())
            .orElseGet(
                () ->
                    new ConnectorProperties.Validation(
                        new ConnectorProperties.Validation.Hosts(false, null, null, false, false)));
    var allowRanges =
        Optional.ofNullable(validation.hosts().allowRanges()).orElseGet(List::of).stream()
            .map(CidrRange::parse)
            .toList();
    List<CidrRange> denyRanges =
        Optional.ofNullable(validation.hosts().denyRanges()).orElseGet(List::of).stream()
            .map(CidrRange::parse)
            .toList();
    var config =
        new VerifiedHostValidator.Config(
            validation.hosts().enabled(),
            allowRanges,
            denyRanges,
            validation.hosts().unsafeAllowPrivateRanges(),
            validation.hosts().unsafeAllowLoopback());
    return new VerifiedHostValidator(config);
  }

  @Bean
  @ConditionalOnMissingBean(ValidationProvider.class)
  SpringBeanConstraintValidatorFactory springConstraintValidatorFactory(
      AutowireCapableBeanFactory autowireCapableBeanFactory) {
    return new SpringBeanConstraintValidatorFactory(autowireCapableBeanFactory);
  }

  @Bean
  @ConditionalOnMissingBean(ValidationProvider.class)
  ValidationProvider validationProvider(ConstraintValidatorFactory constraintValidatorFactory) {
    var validationFactory =
        Validation.byDefaultProvider()
            .configure()
            .messageInterpolator(new ParameterMessageInterpolator())
            .constraintValidatorFactory(constraintValidatorFactory)
            .buildValidatorFactory();
    return new DefaultValidationProvider(validationFactory);
  }
}
