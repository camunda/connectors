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
import io.camunda.connector.runtime.cloud.GcpSecretManagerSecretProvider;
import io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SaaSConfiguration {

  @Value("${camunda.saas.secrets.projectId}")
  private String secretsProjectId;

  // TODO: Default value?
  @Value("${camunda.saas.secrets.prefix}")
  private String secretsNamePrefix;

  /**
   * Internal prefix used for M2M tokens
   */
  @Value("${camunda.saas.secrets.internalPrefix:internal-connector-secrets-}")
  private String secretsInternalNamePrefix;

  private final ZeebeClientConfigurationProperties conf;

  @Autowired
  public SaaSConfiguration(ZeebeClientConfigurationProperties conf) {
    this.conf = conf;
  }

  @Bean
  public SecretProvider getSecretProvider() {
    return new GcpSecretManagerSecretProvider(conf.getCloud().getClusterId(), secretsProjectId, secretsNamePrefix);
  }

  public SecretProvider getInternalSecretProvider() {
    return new GcpSecretManagerSecretProvider(conf.getCloud().getClusterId(), secretsProjectId, secretsInternalNamePrefix);
  }

}
