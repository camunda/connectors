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
package io.camunda.connector.runtime.saas;

import io.camunda.client.CredentialsProvider;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.client.spring.configuration.CredentialsProviderConfiguration;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.connector.api.secret.SecretProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This class configures the custom credentials provider for the Camunda client. If the default
 * client ID and secret are not present, the client will use the internal secret provider to fetch
 * the client ID and secret from the SaaS secret manager.
 */
@Configuration
public class CamundaClientSaaSConfiguration {

  public static String SECRET_NAME_CLIENT_ID = "M2MClientId";
  public static String SECRET_NAME_SECRET = "M2MSecret";

  private final SecretProvider internalSecretProvider;

  @Value("${camunda.client.auth.token-url}")
  private String camundaClientTokenUrl;

  @Value("${camunda.client.auth.audience}")
  private String camundaClientAudience;

  @Value("${camunda.client.auth.credentials-cache-path:/tmp/connectors}")
  private String credentialsCachePath;

  public CamundaClientSaaSConfiguration(@Autowired SaaSSecretConfiguration saaSConfiguration) {
    this.internalSecretProvider = saaSConfiguration.getInternalSecretProvider();
  }

  /**
   * Provides a custom {@link CredentialsProviderConfiguration} that is always registered. When
   * client-id/secret are absent from a resolved client's properties, it fetches M2M credentials
   * from the internal secret manager (GCP or AWS). When credentials are present in the per-client
   * properties, it delegates to the parent implementation so that each client uses its own
   * configured credentials.
   *
   * <p>This bean replaces the default {@link CredentialsProviderConfiguration} from the Camunda
   * Spring Boot starter (which would register via {@code @ConditionalOnMissingBean}).
   */
  @Bean
  public CredentialsProviderConfiguration credentialsProviderConfiguration() {
    return new CredentialsProviderConfiguration() {
      @Override
      public CredentialsProvider camundaClientCredentialsProvider(
          final CamundaClientProperties properties) {
        if (properties.getAuth().getClientId() == null
            && properties.getAuth().getClientSecret() == null) {
          return new OAuthCredentialsProviderBuilder()
              .clientId(internalSecretProvider.getSecret(SECRET_NAME_CLIENT_ID, null))
              .clientSecret(internalSecretProvider.getSecret(SECRET_NAME_SECRET, null))
              .authorizationServerUrl(camundaClientTokenUrl)
              .audience(camundaClientAudience)
              .credentialsCachePath(credentialsCachePath)
              .build();
        }
        return super.camundaClientCredentialsProvider(properties);
      }
    };
  }
}
