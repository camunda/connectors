/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.gcp;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.google.cloud.functions.v2.FunctionServiceClient;
import com.google.cloud.functions.v2.FunctionServiceSettings;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * The GCP clients of the provider. Credentials come from the application default credentials only:
 * the file of {@code GOOGLE_APPLICATION_CREDENTIALS}, which may be a service account key or a
 * workload identity federation configuration, the gcloud credentials, then the metadata server. The
 * runtime therefore needs no key in its configuration, and works without a metadata server.
 */
final class GcpClients {

  private static final String CLOUD_PLATFORM_SCOPE =
      "https://www.googleapis.com/auth/cloud-platform";
  private static final int IMPERSONATION_LIFETIME_SECONDS = 3600;
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  private GcpClients() {}

  /**
   * The identity that creates and calls the executors: the deployer service account when one is
   * set, impersonated by the application default credentials, otherwise these credentials. It mints
   * the ID tokens of the invocations itself.
   *
   * @throws IllegalStateException when that identity cannot mint ID tokens, as a workload identity
   *     federation or a gcloud user login cannot
   */
  static GoogleCredentials deployer(
      GoogleCredentials applicationDefault, String deployerServiceAccount) {
    final var source = applicationDefault.createScoped(List.of(CLOUD_PLATFORM_SCOPE));
    final GoogleCredentials credentials =
        deployerServiceAccount == null
            ? source
            : ImpersonatedCredentials.create(
                source,
                deployerServiceAccount,
                null,
                List.of(CLOUD_PLATFORM_SCOPE),
                IMPERSONATION_LIFETIME_SECONDS);
    // A user login is an IdTokenProvider, but its ID token is the one of its own login, whatever
    // the audience, which Cloud Run refuses.
    if (!(credentials instanceof IdTokenProvider) || credentials instanceof UserCredentials) {
      throw new IllegalStateException(
          ("The application default credentials (%s) cannot mint the ID tokens that call the"
                  + " executors: set camunda.connector.managed-code.gcp.deployer-service-account"
                  + " to a service account they can impersonate")
              .formatted(applicationDefault.getClass().getSimpleName()));
    }
    return credentials;
  }

  /** Cloud Functions control plane, with the client's default retries. */
  static FunctionServiceClient functions(GoogleCredentials credentials) throws IOException {
    return FunctionServiceClient.create(
        FunctionServiceSettings.newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
            .build());
  }

  /**
   * Uploads and invocations. The JDK client never sends a POST again, so that a script runs at most
   * once per job attempt; each request sets its own timeout.
   */
  static HttpClient http() {
    return HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  /** The application default credentials; replaced in tests. */
  @FunctionalInterface
  interface ApplicationDefault {
    GoogleCredentials load() throws IOException;
  }
}
