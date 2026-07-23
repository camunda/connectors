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
package io.camunda.connector.runtime.saas.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.client.spring.properties.CamundaClientAuthProperties.AuthMethod;
import io.camunda.client.spring.properties.CamundaClientProperties;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.saas.CamundaClientSaaSConfiguration;
import io.camunda.connector.runtime.saas.SaaSSecretConfiguration;
import java.net.URI;
import java.net.URL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CamundaClientSaaSConfigurationTest {

  @Mock private SecretProvider mockSecretProvider;
  @Mock private SaaSSecretConfiguration mockSaaSConfig;

  @BeforeEach
  void setup() {
    when(mockSaaSConfig.getInternalSecretProvider()).thenReturn(mockSecretProvider);
  }

  private CamundaClientSaaSConfiguration createConfig() {
    var config = new CamundaClientSaaSConfiguration(mockSaaSConfig);
    ReflectionTestUtils.setField(
        config, "camundaClientTokenUrl", "https://token.example.com/oauth/token");
    ReflectionTestUtils.setField(config, "camundaClientAudience", "test-audience");
    return config;
  }

  @Test
  void whenCredentialsMissing_fetchesBothSecretsFromInternalProvider() {
    when(mockSecretProvider.getSecret(CamundaClientSaaSConfiguration.SECRET_NAME_CLIENT_ID, null))
        .thenReturn("gcp-client-id");
    when(mockSecretProvider.getSecret(CamundaClientSaaSConfiguration.SECRET_NAME_SECRET, null))
        .thenReturn("gcp-client-secret");

    var config = createConfig();
    var properties = new CamundaClientProperties();
    // auth.clientId and auth.clientSecret are null by default

    config.credentialsProviderConfiguration().camundaClientCredentialsProvider(properties);

    verify(mockSecretProvider)
        .getSecret(CamundaClientSaaSConfiguration.SECRET_NAME_CLIENT_ID, null);
    verify(mockSecretProvider).getSecret(CamundaClientSaaSConfiguration.SECRET_NAME_SECRET, null);
  }

  @Test
  void whenCredentialsPresent_delegatesToSuperAndReturnsOAuthProvider() {
    var config = createConfig();
    var properties = new CamundaClientProperties();
    properties.getAuth().setMethod(AuthMethod.oidc);
    properties.getAuth().setClientId("provided-client-id");
    properties.getAuth().setClientSecret("provided-client-secret");
    properties.getAuth().setAudience("test-audience");
    properties.getAuth().setTokenUrl(URI.create("https://token.example.com/oauth/token"));

    var result =
        config.credentialsProviderConfiguration().camundaClientCredentialsProvider(properties);

    assertThat(result).isInstanceOf(OAuthCredentialsProvider.class);
    verify(mockSecretProvider, never()).getSecret(any(), any());
  }

  @Test
  void whenCredentialsMissingAndPerClientAuthValuesSet_usesPerClientValuesNotGlobal()
      throws Exception {
    when(mockSecretProvider.getSecret(CamundaClientSaaSConfiguration.SECRET_NAME_CLIENT_ID, null))
        .thenReturn("gcp-client-id");
    when(mockSecretProvider.getSecret(CamundaClientSaaSConfiguration.SECRET_NAME_SECRET, null))
        .thenReturn("gcp-client-secret");

    var config = new CamundaClientSaaSConfiguration(mockSaaSConfig);
    ReflectionTestUtils.setField(
        config, "camundaClientTokenUrl", "https://global.token.example.com/oauth/token");
    ReflectionTestUtils.setField(config, "camundaClientAudience", "global-audience");

    var properties = new CamundaClientProperties();
    properties
        .getAuth()
        .setTokenUrl(URI.create("https://per-client.token.example.com/oauth/token"));
    properties.getAuth().setAudience("per-client-audience");
    // clientId and clientSecret remain null -> uses internal secret provider

    var result =
        config.credentialsProviderConfiguration().camundaClientCredentialsProvider(properties);

    assertThat(result).isInstanceOf(OAuthCredentialsProvider.class);
    var usedUrl = (URL) ReflectionTestUtils.getField(result, "authorizationServerUrl");
    assertThat(usedUrl.toString()).isEqualTo("https://per-client.token.example.com/oauth/token");
    verify(mockSecretProvider)
        .getSecret(CamundaClientSaaSConfiguration.SECRET_NAME_CLIENT_ID, null);
    verify(mockSecretProvider).getSecret(CamundaClientSaaSConfiguration.SECRET_NAME_SECRET, null);
  }

  @Test
  void whenCredentialsMissing_perClientScopeResourceStillReachTheBuiltProvider() {
    when(mockSecretProvider.getSecret(CamundaClientSaaSConfiguration.SECRET_NAME_CLIENT_ID, null))
        .thenReturn("gcp-client-id");
    when(mockSecretProvider.getSecret(CamundaClientSaaSConfiguration.SECRET_NAME_SECRET, null))
        .thenReturn("gcp-client-secret");

    var config = createConfig();
    var properties = new CamundaClientProperties();
    properties.getAuth().setScope("cluster-scope");
    properties.getAuth().setResource("cluster-resource");
    // clientId and clientSecret remain null -> uses internal secret provider

    var result =
        config.credentialsProviderConfiguration().camundaClientCredentialsProvider(properties);

    assertThat(ReflectionTestUtils.getField(result, "scope")).isEqualTo("cluster-scope");
    assertThat(ReflectionTestUtils.getField(result, "resource")).isEqualTo("cluster-resource");
  }

  @Test
  void whenCredentialsMissingAndNoCachePathConfigured_usesInMemoryCacheNotSharedFile() {
    when(mockSecretProvider.getSecret(CamundaClientSaaSConfiguration.SECRET_NAME_CLIENT_ID, null))
        .thenReturn("gcp-client-id");
    when(mockSecretProvider.getSecret(CamundaClientSaaSConfiguration.SECRET_NAME_SECRET, null))
        .thenReturn("gcp-client-secret");

    var config = new CamundaClientSaaSConfiguration(mockSaaSConfig);
    ReflectionTestUtils.setField(
        config, "camundaClientTokenUrl", "https://token.example.com/oauth/token");
    ReflectionTestUtils.setField(config, "camundaClientAudience", "test-audience");
    // no per-client credentials-cache-path configured, and there is no global fallback for it:
    // every internal-secret client resolves the same client id, so a shared cache file would let
    // one client's cached token leak into another client with a different audience/token-url.

    var properties = new CamundaClientProperties();

    var result =
        config.credentialsProviderConfiguration().camundaClientCredentialsProvider(properties);

    var cache = ReflectionTestUtils.getField(result, "credentialsCache");
    assertThat(ReflectionTestUtils.getField(cache, "cacheFile")).isNull();
  }
}
