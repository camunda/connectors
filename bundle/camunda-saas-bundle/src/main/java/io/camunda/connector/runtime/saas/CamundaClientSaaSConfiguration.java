package io.camunda.connector.runtime.saas;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.CredentialsProvider;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.spring.client.configuration.CamundaClientConfigurationImpl;
import io.camunda.spring.client.jobhandling.CamundaClientExecutorService;
import io.camunda.spring.client.properties.CamundaClientProperties;
import io.grpc.ClientInterceptor;
import java.util.List;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class CamundaClientSaaSConfiguration {

  public static String SECRET_NAME_CLIENT_ID = "M2MClientId";
  public static String SECRET_NAME_SECRET = "M2MSecret";

  private final SecretProvider internalSecretProvider;

  @Value("${camunda.operate.client.authUrl}")
  private String operateAuthUrl;

  public CamundaClientSaaSConfiguration(@Autowired SaaSConfiguration saaSConfiguration) {
    this.internalSecretProvider = saaSConfiguration.getInternalSecretProvider();
  }

  private static class SaaSZeebeClientConfiguration extends CamundaClientConfigurationImpl {

    public SaaSZeebeClientConfiguration(
        CamundaClientProperties camundaClientProperties,
        JsonMapper jsonMapper,
        List<ClientInterceptor> interceptors,
        List<AsyncExecChainHandler> chainHandlers,
        CamundaClientExecutorService zeebeClientExecutorService,
        CredentialsProvider credentialsProvider) {
      super(camundaClientProperties, jsonMapper, interceptors, chainHandlers,
          zeebeClientExecutorService, credentialsProvider);
    }
  }

  @Bean
  @Primary
  public CamundaClientConfiguration saasZeebeClientConfiguration(
      final CamundaClientProperties camundaClientProperties,
      final JsonMapper jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final CamundaClientExecutorService zeebeClientExecutorService) {
    return new SaaSZeebeClientConfiguration(camundaClientProperties, jsonMapper,
        interceptors, chainHandlers, zeebeClientExecutorService, credentialsProvider());
  }

  private CredentialsProvider credentialsProvider() {
    final var builder = new OAuthCredentialsProviderBuilder();
    builder.clientId(internalSecretProvider.getSecret(SECRET_NAME_CLIENT_ID));
    builder.clientSecret(internalSecretProvider.getSecret(SECRET_NAME_SECRET));
    builder.authorizationServerUrl(operateAuthUrl);
    builder.audience("zeebe.dev.ultrawombat.com");
    return builder.build();
  }
}
