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

import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.impl.config.ConnectorPropertyResolver;
import io.camunda.connector.runtime.env.SpringConnectorPropertyResolver;
import io.camunda.connector.runtime.inbound.InboundConnectorRuntimeConfiguration;
import io.camunda.connector.runtime.outbound.OutboundConnectorRuntimeConfiguration;
import io.camunda.connector.runtime.util.secret.SecretProviderAggregator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@EnableScheduling
@Import({OutboundConnectorRuntimeConfiguration.class, InboundConnectorRuntimeConfiguration.class})
public class ConnectorsAutoConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectorsAutoConfiguration.class);

  @Bean
  @ConditionalOnMissingBean
  public SecretProviderAggregator springSecretProviderAggregator(
      List<SecretProvider> secretProviders) {
    if (secretProviders == null || secretProviders.isEmpty()) {
      LOG.debug(
          "No secret providers discovered as Spring beans. "
              + "Falling back to SPI discovery and environment variables.");
      return new SecretProviderAggregator();
    }
    LOG.debug("Using secret providers discovered as Spring beans: {}", secretProviders);
    return new SecretProviderAggregator(secretProviders);
  }

  @Bean
  @ConditionalOnMissingBean
  public ConnectorPropertyResolver springConnectorPropertyResolver(Environment environment) {
    return new SpringConnectorPropertyResolver(environment);
  }
}
