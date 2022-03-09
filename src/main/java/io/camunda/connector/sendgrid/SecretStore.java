package io.camunda.connector.sendgrid;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecretStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(SecretStore.class);
  private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
  private static final Pattern SECRET_PATTERN = Pattern.compile("^secrets\\.(\\S+)$");
  public static final String SECRETS_ENV_NAME = "CONNECTOR_SECRETS";
  public static final String SECRETS_PROJECT_ENV_NAME = "SECRETS_PROJECT_ID";
  public static final String SECRETS_STAGE_ENV_NAME = "SECRETS_STAGE";

  private final Map<String, String> secrets;

  public SecretStore(final Gson gson, final String clusterId) {
    final String json =
        Optional.ofNullable(clusterId)
            .map(SecretStore::loadGoogleSecrets)
            .orElseGet(SecretStore::loadEnvironmentSecrets);

    Objects.requireNonNull(json, "Failed to load secrets");

    secrets = gson.fromJson(json, MAP_TYPE);
  }

  private static String loadGoogleSecrets(final String clusterId) {
    LOGGER.info("Fetching secrets for cluster {} from secret manager", clusterId);
    try (final SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
      final String projectId =
          Objects.requireNonNull(
              System.getenv(SECRETS_PROJECT_ENV_NAME),
              "Environment variable " + SECRETS_PROJECT_ENV_NAME + " is missing");
      final String stage =
          Objects.requireNonNull(
              System.getenv(SECRETS_STAGE_ENV_NAME),
              "Environment variable " + SECRETS_STAGE_ENV_NAME + " is missing");

      final String secretName = String.format("%s-%s", stage, clusterId);
      final SecretVersionName secretVersionName =
          SecretVersionName.of(projectId, secretName, "latest");
      final AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
      return response.getPayload().getData().toStringUtf8();
    } catch (final IOException e) {
      LOGGER.warn("Failed to load secrets from secret manager", e);
      return null;
    }
  }

  private static String loadEnvironmentSecrets() {
    LOGGER.info("Loading secrets from environment variable {}", SECRETS_ENV_NAME);
    return System.getenv(SECRETS_ENV_NAME);
  }

  public String replaceSecret(final String value) {
    return Optional.ofNullable(value)
        .map(String::trim)
        .map(SECRET_PATTERN::matcher)
        .filter(Matcher::matches)
        .map(matcher -> matcher.group(1))
        .map(secrets::get)
        .orElse(value);
  }
}
