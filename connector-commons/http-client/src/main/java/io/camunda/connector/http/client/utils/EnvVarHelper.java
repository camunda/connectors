package io.camunda.connector.http.client.utils;

public class EnvVarHelper {

  private static final String ENV_VAR_MAX_BODY_SIZE = "CONNECTOR_HTTP_CLIENT_MAX_BODY_SIZE";

  public static int getMaxInMemoryBodySize() {
    String envVar = System.getenv(ENV_VAR_MAX_BODY_SIZE);
    if (envVar != null) {
      try {
        int size = Integer.parseInt(envVar);
        if (size > 0) {
          return size;
        } else {
          throw new IllegalArgumentException(
              "Environment variable "
                  + ENV_VAR_MAX_BODY_SIZE
                  + " must be a positive integer, but was: "
                  + envVar);
        }
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "Environment variable "
                + ENV_VAR_MAX_BODY_SIZE
                + " must be a valid integer, but was: "
                + envVar,
            e);
      }
    }
    return 50 * 1024 * 1024; // Default to 50 MB
  }
}
