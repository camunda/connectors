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
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.core.secret.SecretProviderDiscovery;
import io.camunda.connector.runtime.outbound.OutboundConnectorRuntimeConfiguration;
import io.camunda.connector.runtime.secret.ConsoleSecretProvider;
import io.camunda.connector.runtime.secret.EnvironmentSecretProvider;
import io.camunda.connector.runtime.secret.console.ConsoleSecretApiClient;
import io.camunda.connector.runtime.secret.console.JwtCredential;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties.ClientMode;
import io.camunda.zeebe.spring.common.json.SdkObjectMapper;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

@AutoConfiguration
@AutoConfigureBefore(JacksonAutoConfiguration.class)
@Import(OutboundConnectorRuntimeConfiguration.class)
@EnableConfigurationProperties(ConnectorProperties.class)
public class OutboundConnectorsAutoConfiguration {

  @Value("${camunda.connector.secretprovider.discovery.enabled:true}")
  Boolean secretProviderLookupEnabled;

  @Value("${camunda.connector.secretprovider.environment.prefix:}")
  String environmentSecretProviderPrefix;

  @Value(
      "${camunda.connector.secretprovider.console.endpoint:https://cluster-api.cloud.camunda.io/secrets}")
  String consoleSecretsApiEndpoint;

  @Value("${camunda.connector.secretprovider.console.audience:secrets.camunda.io}")
  String consoleSecretsApiAudience;

  private static final Logger LOG =
      LoggerFactory.getLogger(OutboundConnectorsAutoConfiguration.class);

  /** Provides a {@link FeelEngineWrapper} unless already present in the Spring Context */
  @Bean
  @ConditionalOnMissingBean(FeelEngineWrapper.class)
  public FeelEngineWrapper feelEngine() {
    return new FeelEngineWrapper();
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

  @Bean(name = "zeebeJsonMapper")
  @ConditionalOnMissingBean
  public JsonMapper jsonMapper(ObjectMapper objectMapper) {
    if (objectMapper == null) {
      return new ZeebeObjectMapper();
    }
    return new ZeebeObjectMapper(objectMapper.copy());
  }

  @Bean(name = "commonJsonMapper")
  @ConditionalOnMissingBean
  public io.camunda.zeebe.spring.common.json.JsonMapper commonJsonMapper(
      ObjectMapper objectMapper) {
    if (objectMapper == null) {
      return new SdkObjectMapper();
    }
    return new SdkObjectMapper(objectMapper.copy());
  }

  @Bean
  @ConditionalOnProperty(
      name = "camunda.connector.secretprovider.environment.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public EnvironmentSecretProvider defaultSecretProvider(Environment environment) {
    return new EnvironmentSecretProvider(environment, environmentSecretProviderPrefix);
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

    if (!clientProperties.getMode().equals(ClientMode.saas)) {
      throw new RuntimeException(
          "Console Secrets require a SaaS environment, but the client is configured for "
              + clientProperties.getMode());
    }

    var authProperties = clientProperties.getAuth();
    URL issuerUrl;
    try {
      issuerUrl = new URI(authProperties.getIssuer()).toURL();
    } catch (Exception e) {
      throw new RuntimeException("Invalid issuer URL: " + authProperties.getIssuer(), e);
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

  @Bean
  @ConditionalOnMissingBean
  public ObjectMapper objectMapper() {
    return ConnectorsObjectMapperSupplier.getCopy();
  }
}
