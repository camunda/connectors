package io.camunda.connector.runtime.saas;

import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.ZeebeClientConfiguration;
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientConfigurationImpl;
import io.camunda.zeebe.spring.client.jobhandling.ZeebeClientExecutorService;
import io.camunda.zeebe.spring.client.properties.CamundaClientProperties;
import io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties;
import io.grpc.ClientInterceptor;
import java.util.List;
import org.apache.hc.client5.http.async.AsyncExecChainHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZeebeClientSaaSConfiguration {

  public static String SECRET_NAME_CLIENT_ID = "M2MClientId";
  public static String SECRET_NAME_SECRET = "M2MSecret";

  private final SecretProvider internalSecretProvider;

  @Value("${camunda.operate.client.authUrl}")
  private String operateAuthUrl;

  public ZeebeClientSaaSConfiguration(@Autowired SaaSConfiguration saaSConfiguration) {
    this.internalSecretProvider = saaSConfiguration.getInternalSecretProvider();
  }

  private static class SaaSZeebeClientConfiguration extends ZeebeClientConfigurationImpl {

    private final CredentialsProvider customCredentialsProvider;

    public SaaSZeebeClientConfiguration(
        ZeebeClientConfigurationProperties properties,
        CamundaClientProperties camundaClientProperties,
        JsonMapper jsonMapper,
        List<ClientInterceptor> interceptors,
        List<AsyncExecChainHandler> chainHandlers,
        ZeebeClientExecutorService zeebeClientExecutorService,
        CredentialsProvider credentialsProvider) {
      super(properties, camundaClientProperties, jsonMapper, interceptors, chainHandlers,
          zeebeClientExecutorService);
      this.customCredentialsProvider = credentialsProvider;
    }

    @Override
    public CredentialsProvider getCredentialsProvider() {
      return customCredentialsProvider;
    }
  }

  @Bean
  public ZeebeClientConfiguration zeebeClientConfiguration(
      final ZeebeClientConfigurationProperties properties,
      final CamundaClientProperties camundaClientProperties,
      final JsonMapper jsonMapper,
      final List<ClientInterceptor> interceptors,
      final List<AsyncExecChainHandler> chainHandlers,
      final ZeebeClientExecutorService zeebeClientExecutorService) {
    return new SaaSZeebeClientConfiguration(properties, camundaClientProperties, jsonMapper,
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
