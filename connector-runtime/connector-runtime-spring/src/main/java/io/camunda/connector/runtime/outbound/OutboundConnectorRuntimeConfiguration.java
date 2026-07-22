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
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import io.camunda.connector.runtime.annotation.OutboundConnectorObjectMapper;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStoreImpl;
import io.camunda.connector.runtime.core.outbound.DefaultOutboundConnectorFactory;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.core.outbound.configuration.ConfigurationValidationRegistry;
import io.camunda.connector.runtime.core.outbound.configuration.ConfigurationValidationService;
import io.camunda.connector.runtime.core.secret.SecretFilterFactory;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.core.validation.ValidationUtil;
import io.camunda.connector.runtime.instances.InstanceForwardingConfiguration;
import io.camunda.connector.runtime.instances.service.OutboundConnectorsService;
import io.camunda.connector.runtime.outbound.controller.ConfigurationValidationRestController;
import io.camunda.connector.runtime.outbound.controller.OutboundConnectorsRestController;
import io.camunda.connector.runtime.outbound.job.ConfigurableSecretFilterFactory;
import io.camunda.connector.runtime.outbound.job.ConfigurableSecretFilterFactory.SecretFilterMode;
import io.camunda.connector.runtime.outbound.jobstream.BrokerJobStreamClient;
import io.camunda.connector.runtime.outbound.lifecycle.OutboundConnectorManager;
import io.camunda.connector.runtime.outbound.secret.ProcessDefinitionSecretKeyCache;
import io.camunda.connector.runtime.outbound.secret.SecretKeyCache;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
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
@Import({
  OutboundConnectorsRestController.class,
  ConfigurationValidationRestController.class,
  InstanceForwardingConfiguration.class
})
public class OutboundConnectorRuntimeConfiguration {

  @Bean
  public ConfigurationValidationRegistry configurationValidationRegistry() {
    return new ConfigurationValidationRegistry(discoverConfigurationValidators());
  }

  /**
   * Discovers {@code ConfigurationValidator} implementations via the SPI {@link ServiceLoader},
   * mirroring how connectors themselves are discovered ({@code SPIConnectorDiscovery}). This is
   * package-independent: a third-party connector's validator (e.g. under {@code com.acme}) is found
   * as long as it is declared in {@code META-INF/services}, whereas a fixed base-package scan would
   * silently miss it and always answer {@code UNSUPPORTED}.
   */
  @SuppressWarnings("rawtypes")
  private static List<ConfigurationValidator<?>> discoverConfigurationValidators() {
    List<ConfigurationValidator<?>> validators = new ArrayList<>();
    for (ConfigurationValidator validator : ServiceLoader.load(ConfigurationValidator.class)) {
      validators.add(validator);
    }
    return validators;
  }

  @Bean
  public ConfigurationValidationService configurationValidationService(
      ConfigurationValidationRegistry configurationValidationRegistry,
      FeelExpressionEvaluator feelExpressionEvaluator,
      SecretProviderAggregator secretProviderAggregator,
      ValidationProvider validationProvider,
      @OutboundConnectorObjectMapper ObjectMapper objectMapper) {
    return new ConfigurationValidationService(
        configurationValidationRegistry,
        feelExpressionEvaluator,
        secretProviderAggregator,
        validationProvider,
        objectMapper);
  }

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
        camundaClient, cacheManager.getCache(SecretKeyCache.SECRET_KEY_CACHE_NAME));
  }

  @Bean
  public SecretFilterFactory secretFilterFactory(
      @Value("${camunda.connector.secret-resolver.secret-filter.mode:DISABLED}")
          SecretFilterMode secretFilterMode,
      SecretKeyCache secretKeyCache) {
    return new ConfigurableSecretFilterFactory(secretFilterMode, secretKeyCache);
  }

  @Bean
  public OutboundConnectorManager outboundConnectorManager(
      JobWorkerManager jobWorkerManager,
      OutboundConnectorFactory connectorFactory,
      JobCallbackCommandWrapperFactory jobCallbackCommandWrapperFactory,
      SecretProviderAggregator secretProviderAggregator,
      ValidationProvider validationProvider,
      MetricsRecorder metricsRecorder,
      DocumentFactory documentFactory,
      @OutboundConnectorObjectMapper ObjectMapper objectMapper,
      SecretFilterFactory secretFilterFactory,
      Optional<MeterRegistry> meterRegistry) {
    return new OutboundConnectorManager(
        jobWorkerManager,
        connectorFactory,
        jobCallbackCommandWrapperFactory,
        secretProviderAggregator,
        validationProvider,
        documentFactory,
        objectMapper,
        metricsRecorder,
        secretFilterFactory,
        meterRegistry.orElse(null));
  }
}
