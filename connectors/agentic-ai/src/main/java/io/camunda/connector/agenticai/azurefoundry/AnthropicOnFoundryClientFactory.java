/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientImpl;
import com.anthropic.core.ClientOptions;
import com.anthropic.foundry.backends.FoundryBackend;
import com.azure.core.credential.TokenCredential;
import com.azure.identity.AuthenticationUtil;
import com.azure.identity.ClientSecretCredentialBuilder;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelHttpProxySupport;
import io.camunda.connector.agenticai.aiagent.model.request.provider.AzureFoundryProviderConfiguration.AzureAiFoundryModel.AnthropicModel;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication.AzureApiKeyAuthentication;
import io.camunda.connector.agenticai.aiagent.model.request.provider.shared.AzureAuthentication.AzureClientCredentialsAuthentication;
import io.camunda.connector.agenticai.azurefoundry.http.BackendAwareAnthropicHttpClient;
import io.camunda.connector.agenticai.azurefoundry.http.JdkAnthropicHttpClient;
import io.camunda.connector.agenticai.azurefoundry.langchain4j.AnthropicOnFoundryChatModel;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;

public class AnthropicOnFoundryClientFactory {

  private static final String BEARER_SCOPE = "https://cognitiveservices.azure.com/.default";

  private final ChatModelHttpProxySupport proxySupport;

  public AnthropicOnFoundryClientFactory(ChatModelHttpProxySupport proxySupport) {
    this.proxySupport = proxySupport;
  }

  public AnthropicOnFoundryChatModel create(
      String endpoint,
      AzureAuthentication authentication,
      Duration timeout,
      AnthropicModel modelConfig) {

    String normalizedEndpoint = StringUtils.removeEnd(endpoint, "/");

    FoundryBackend backend = buildFoundryBackend(normalizedEndpoint, authentication);

    HttpClient jdkClient = buildJdkHttpClient();
    JdkAnthropicHttpClient jdkAnthropicHttp = new JdkAnthropicHttpClient(jdkClient);
    BackendAwareAnthropicHttpClient backendAwareHttp =
        new BackendAwareAnthropicHttpClient(jdkAnthropicHttp, backend);

    // The FoundryBackend.baseUrl() is the authoritative URL (either the user-supplied endpoint
    // or the SDK default computed from the resource name). It must be passed as ClientOptions
    // baseUrl so that the SDK's service implementations set it on each HttpRequest before
    // handing the request to our BackendAwareAnthropicHttpClient.
    ClientOptions.Builder optionsBuilder =
        ClientOptions.builder().httpClient(backendAwareHttp).baseUrl(backend.baseUrl());
    if (timeout != null) {
      optionsBuilder.timeout(timeout);
    }
    AnthropicClient anthropicClient = new AnthropicClientImpl(optionsBuilder.build());

    return new AnthropicOnFoundryChatModel(anthropicClient, modelConfig);
  }

  private HttpClient buildJdkHttpClient() {
    HttpClient.Builder jdkBuilder = HttpClient.newBuilder();
    proxySupport.getJdkHttpClientProxyConfigurator().configure(jdkBuilder);
    return jdkBuilder.build();
  }

  private FoundryBackend buildFoundryBackend(String endpoint, AzureAuthentication authentication) {
    // Use .baseUrl(endpoint) so the user's configured URL is authoritative (supports WireMock
    // in tests, private endpoints, etc.). The SDK default URL
    // (https://<resource>.services.ai.azure.com) would be used with .resource(), but .baseUrl()
    // gives callers full control without requiring the SDK to compute the URL.
    return switch (authentication) {
      case AzureApiKeyAuthentication key ->
          FoundryBackend.builder().baseUrl(endpoint).apiKey(key.apiKey()).build();

      case AzureClientCredentialsAuthentication creds -> {
        TokenCredential credential = buildTokenCredential(creds);
        Supplier<String> bearerSupplier =
            AuthenticationUtil.getBearerTokenSupplier(credential, BEARER_SCOPE);
        yield FoundryBackend.builder()
            .baseUrl(endpoint)
            .bearerTokenSupplier(bearerSupplier)
            .build();
      }
    };
  }

  private TokenCredential buildTokenCredential(AzureClientCredentialsAuthentication creds) {
    ClientSecretCredentialBuilder builder =
        new ClientSecretCredentialBuilder()
            .clientId(creds.clientId())
            .clientSecret(creds.clientSecret())
            .tenantId(creds.tenantId());
    if (StringUtils.isNotBlank(creds.authorityHost())) {
      builder.authorityHost(creds.authorityHost());
    }
    return builder.build();
  }
}
