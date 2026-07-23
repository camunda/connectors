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
import io.camunda.client.spring.configuration.CredentialsProviderConfiguration;
import io.camunda.client.spring.properties.CamundaClientAuthProperties;
import io.camunda.client.spring.properties.CamundaClientAuthProperties.AuthMethod;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.connector.api.secret.SecretProvider;
import java.net.URI;
import org.springframework.beans.BeanUtils;
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

  @Value("${camunda.client.auth.token-url:#{null}}")
  private String camundaClientTokenUrl;

  @Value("${camunda.client.auth.audience:#{null}}")
  private String camundaClientAudience;

  public CamundaClientSaaSConfiguration(@Autowired SaaSSecretConfiguration saaSConfiguration) {
    this.internalSecretProvider = saaSConfiguration.getInternalSecretProvider();
  }

  /**
   * Provides a custom {@link CredentialsProviderConfiguration} that is always registered. When
   * client-id/secret are absent from a resolved client's properties, it fetches M2M credentials
   * from the internal secret manager (GCP or AWS) and delegates to the parent implementation on a
   * copy of the properties, so scope/resource/issuer/TLS/timeout/retry settings resolved for that
   * client are honored the same way they are for clients with explicit credentials. When
   * credentials are present in the per-client properties, it delegates to the parent implementation
   * directly so that each client uses its own configured credentials.
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
        var auth = properties.getAuth();
        if (auth.getClientId() == null && auth.getClientSecret() == null) {
          return super.camundaClientCredentialsProvider(
              withInternalSecretManagerCredentials(properties));
        }
        return super.camundaClientCredentialsProvider(properties);
      }
    };
  }

  /**
   * Copies the resolved client properties and fills in the M2M credentials from the internal secret
   * manager, without mutating the original Spring-bound properties (which would otherwise leak the
   * fetched secret into the shared configuration bean). The copy is created reflectively via {@link
   * BeanUtils} so any auth setting the starter adds in the future is carried through to the
   * delegated {@code super} call automatically.
   *
   * <p>The credentials cache path is intentionally NOT defaulted to a shared global value here:
   * every client falling back to the internal secret manager resolves the same client id, and
   * {@link io.camunda.client.impl.oauth.OAuthCredentialsCache} keys cached tokens by client id
   * only. Leaving it unset (unless the client explicitly configured its own path) makes the builder
   * use a private in-memory cache instead of a file shared across clients, preventing a client with
   * one audience/token-url from reusing another client's cached token.
   */
  private CamundaClientProperties withInternalSecretManagerCredentials(
      CamundaClientProperties properties) {
    var resolvedProperties = new CamundaClientProperties();
    BeanUtils.copyProperties(properties, resolvedProperties);
    var auth = new CamundaClientAuthProperties();
    BeanUtils.copyProperties(properties.getAuth(), auth);
    resolvedProperties.setAuth(auth);

    auth.setMethod(AuthMethod.oidc);
    auth.setClientId(internalSecretProvider.getSecret(SECRET_NAME_CLIENT_ID, null));
    auth.setClientSecret(internalSecretProvider.getSecret(SECRET_NAME_SECRET, null));
    if (auth.getTokenUrl() == null && camundaClientTokenUrl != null) {
      auth.setTokenUrl(URI.create(camundaClientTokenUrl));
    }
    if (auth.getAudience() == null) {
      auth.setAudience(camundaClientAudience);
    }
    return resolvedProperties;
  }
}
