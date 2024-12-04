package io.camunda.connector.runtime.saas;

import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.zeebe.client.CredentialsProvider;
import io.camunda.zeebe.client.impl.oauth.OAuthCredentialsProviderBuilder;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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

  @Bean
  @Primary
  public CredentialsProvider credentialsProvider() {
    final var builder = new OAuthCredentialsProviderBuilder();
    builder.clientId(internalSecretProvider.getSecret(SECRET_NAME_CLIENT_ID));
    builder.clientSecret(internalSecretProvider.getSecret(SECRET_NAME_SECRET));
    builder.authorizationServerUrl(operateAuthUrl);
    builder.audience("zeebe.dev.ultrawombat.com");
    return builder.build();
  }

}
