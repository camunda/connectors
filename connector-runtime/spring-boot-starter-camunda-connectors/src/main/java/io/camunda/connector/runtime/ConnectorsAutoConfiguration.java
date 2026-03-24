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
import io.camunda.client.spring.configuration.CamundaAutoConfiguration;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.feel.CamundaClientFeelExpressionEvaluator;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.feel.LocalFeelExpressionEvaluator;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.http.client.authentication.OAuthTokenCache;
import io.camunda.connector.http.client.authentication.OAuthTokenCacheHolder;
import io.camunda.connector.http.client.authentication.cacheimpl.CaffeineOAuthTokenCache;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import io.camunda.connector.runtime.annotation.OutboundConnectorObjectMapper;
import io.camunda.connector.runtime.core.intrinsic.DefaultIntrinsicFunctionExecutor;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.core.secret.SecretProviderDiscovery;
import io.camunda.connector.runtime.secret.ConsoleSecretProvider;
import io.camunda.connector.runtime.secret.EnvironmentSecretProvider;
import io.camunda.connector.runtime.secret.console.ConsoleSecretApiClient;
import io.camunda.connector.runtime.secret.console.JwtCredential;
import java.net.URL;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;

@AutoConfiguration
@AutoConfigureBefore({
  OutboundConnectorsAutoConfiguration.class,
  InboundConnectorsAutoConfiguration.class,
  CamundaAutoConfiguration.class
})
@EnableConfigurationProperties(ConnectorProperties.class)
public class ConnectorsAutoConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectorsAutoConfiguration.class);

  private final ObjectProvider<OAuthTokenCache> oAuthTokenCacheProvider;

  @Value("${camunda.connector.secretprovider.discovery.enabled:true}")
  Boolean secretProviderLookupEnabled;

  @Value(
      "${camunda.connector.secretprovider.environment.prefix:SECRET_}")
  String environmentSecretProviderPrefix;

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
   * Provides a {@link FeelExpressionEvaluator} unless already present in the Spring Context. When a
   * {@link CamundaClient} is available, uses cluster-based evaluation (enabling access to cluster
   * variables like {@code camunda.vars.env.*}). Otherwise, falls back to local FEEL engine.
   */
  @Bean
  @Primary
  @ConditionalOnMissingBean(FeelExpressionEvaluator.class)
  public FeelExpressionEvaluator feelExpressionEvaluator(Optional<CamundaClient> camundaClient) {
    return camundaClient
        .<FeelExpressionEvaluator>map(
            client ->
                new CamundaClientFeelExpressionEvaluator(
                    client, ConnectorsObjectMapperSupplier.getCopy()))
        .orElseGet(LocalFeelExpressionEvaluator::new);
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

  @Bean
  @ConditionalOnMissingBean
  public SecretProviderAggregator springSecretProviderAggregator(
      Optional<List<SecretProvider>> secretProviderBeans) {
    var secretProviders = secretProviderBeans.orElseGet(LinkedList::new);
    LOG.debug("Using secret providers discovered as Spring beans: {}", secretProviderBeans);
    if (secretProviderLookupEnabled != Boolean.FALSE) {
      var discoveredSecretProviders = SecretProviderDiscovery.discoverSecretProviders();
      LOG.debug("Using secret providers discovered by lookup: {}", discoveredSecretProviders);
      secretProviders.addAll(discoveredSecretProviders);
    }
    return new SecretProviderAggregator(secretProviders);
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
                new JacksonModuleFeelFunction(), new JacksonModuleDocumentSerializer()));
  }

  @Bean(defaultCandidate = false)
  @ConnectorsObjectMapper
  @ConditionalOnMissingBean(name = "connectorObjectMapper")
  public ObjectMapper connectorObjectMapper(
      DocumentFactory documentFactory, FeelExpressionEvaluator feelExpressionEvaluator) {
    final ObjectMapper copy = ConnectorsObjectMapperSupplier.getCopy();
    // default intrinsic function contains a pointer of the copy
    var functionExecutor = new DefaultIntrinsicFunctionExecutor(copy);

    // The deserializer module contains the function executor, which contains the pointer of the
    // object mapper
    var jacksonModuleDocumentDeserializer =
        new JacksonModuleDocumentDeserializer(
            documentFactory,
            functionExecutor,
            JacksonModuleDocumentDeserializer.DocumentModuleSettings.create());

    // Function/Supplier always use local evaluation to avoid serializing runtime objects
    // (e.g., Documents) to the cluster. The injected evaluator is used for @FEEL-annotated fields.
    return copy.registerModules(
        jacksonModuleDocumentDeserializer,
        new JacksonModuleFeelFunction(
            true, feelExpressionEvaluator, new LocalFeelExpressionEvaluator()),
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
            false, new LocalFeelExpressionEvaluator()), // FEEL annotation processing disabled
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
}
