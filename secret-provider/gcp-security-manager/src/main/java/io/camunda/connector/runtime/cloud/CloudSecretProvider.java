package io.camunda.connector.runtime.cloud;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.camunda.connector.api.secret.SecretProvider;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudSecretProvider implements SecretProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(CloudSecretProvider.class);
  private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
  public static final String SECRETS_PROJECT_ENV_NAME = "SECRETS_PROJECT_ID";
  public static final String SECRETS_PREFIX_ENV_NAME = "SECRETS_PREFIX";

  private final Map<String, String> secrets;

  public CloudSecretProvider(final Gson gson, final String clusterId) {
    String json = loadGoogleSecrets(clusterId);
    secrets = gson.fromJson(json, MAP_TYPE);
  }

  private static String loadGoogleSecrets(final String clusterId) {
    Objects.requireNonNull(clusterId, "You need to specify the clusterId to load secrets for");
    LOGGER.info("Fetching secrets for cluster {} from secret manager", clusterId);
    try (final SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
      final String projectId = Objects.requireNonNull(
              System.getenv(SECRETS_PROJECT_ENV_NAME),
              "Environment variable " + SECRETS_PROJECT_ENV_NAME + " is missing");
      final String secretPrefix = Objects.requireNonNull(
              System.getenv(SECRETS_PREFIX_ENV_NAME),
              "Environment variable " + SECRETS_PREFIX_ENV_NAME + " is missing");

      final String secretName = String.format("%s-%s", secretPrefix, clusterId);
      final SecretVersionName secretVersionName =
          SecretVersionName.of(projectId, secretName, "latest");
      final AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
      return response.getPayload().getData().toStringUtf8();
    } catch (final Exception e) {
      LOGGER.trace("Failed to load secrets from secret manager", e);
      throw new RuntimeException("Failed to load secrets from secret manager", e);
    }
  }

  @Override
  public String getSecret(String name) {
    return secrets.get(name);
  }
}
