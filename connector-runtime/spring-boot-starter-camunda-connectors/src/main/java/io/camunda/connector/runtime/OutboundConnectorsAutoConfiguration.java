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
import io.camunda.connector.runtime.env.SpringEnvironmentSecretProvider;
import io.camunda.connector.runtime.outbound.OutboundConnectorRuntimeConfiguration;
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

  @Value("${camunda.connector.secret-provider.environment.prefix:}")
  String environmentSecretProviderPrefix;

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

  @Bean
  @ConditionalOnProperty(
      name = "camunda.connector.secret-provider.environment.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public SpringEnvironmentSecretProvider defaultSecretProvider(Environment environment) {
    return new SpringEnvironmentSecretProvider(environment, environmentSecretProviderPrefix);
  }

  @Bean
  @ConditionalOnMissingBean
  public ObjectMapper objectMapper() {
    return ConnectorsObjectMapperSupplier.getCopy();
  }
}
