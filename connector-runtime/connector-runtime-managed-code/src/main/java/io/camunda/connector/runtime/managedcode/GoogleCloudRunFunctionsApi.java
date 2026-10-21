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
package io.camunda.connector.runtime.managedcode;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.functions.v2.CreateFunctionRequest;
import com.google.cloud.functions.v2.DeleteFunctionRequest;
import com.google.cloud.functions.v2.Function;
import com.google.cloud.functions.v2.FunctionServiceClient;
import com.google.cloud.functions.v2.FunctionServiceSettings;
import com.google.cloud.functions.v2.GenerateUploadUrlRequest;
import com.google.cloud.functions.v2.StorageSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link CloudRunFunctionsApi} over the Cloud Functions v2 API, called directly rather than through
 * gcloud, which needs permissions that would undo the prefix scoping.
 *
 * <p>The identity is the deployer of the cluster: either the application default credentials, or a
 * service account they impersonate. It mints the ID tokens of invocations itself, with the email
 * claim, which the prefix-conditioned {@code run.invoker} binding needs.
 */
final class GoogleCloudRunFunctionsApi implements CloudRunFunctionsApi {

  /** A permission denial; retryable, since a new cluster's grants take time to apply. */
  static final String DENIED = "PERMISSION_DENIED";

  private static final String CLOUD_PLATFORM_SCOPE =
      "https://www.googleapis.com/auth/cloud-platform";
  private static final int IMPERSONATION_LIFETIME_SECONDS = 3600;
  private static final long OPERATION_ACCEPT_TIMEOUT_SECONDS = 60;

  private final FunctionServiceClient client;
  private final IdTokenProvider idTokenProvider;
  private final Map<String, IdTokenCredentials> idTokens = new ConcurrentHashMap<>();
  private final HttpClient http =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
  private final String location;

  private GoogleCloudRunFunctionsApi(
      FunctionServiceClient client, IdTokenProvider idTokenProvider, String location) {
    this.client = client;
    this.idTokenProvider = idTokenProvider;
    this.location = location;
  }

  /**
   * @param deployerServiceAccount service account to impersonate, or null to use the application
   *     default credentials as they are; those must then be able to mint ID tokens, as a service
   *     account can and a gcloud user login cannot
   */
  static GoogleCloudRunFunctionsApi create(String location, String deployerServiceAccount) {
    try {
      final var source =
          GoogleCredentials.getApplicationDefault().createScoped(List.of(CLOUD_PLATFORM_SCOPE));
      final GoogleCredentials credentials =
          deployerServiceAccount == null
              ? source
              : ImpersonatedCredentials.create(
                  source,
                  deployerServiceAccount,
                  null,
                  List.of(CLOUD_PLATFORM_SCOPE),
                  IMPERSONATION_LIFETIME_SECONDS);
      if (!(credentials instanceof IdTokenProvider idTokenProvider)) {
        throw new IllegalStateException(
            ("The application default credentials (%s) cannot mint ID tokens to invoke functions;"
                    + " set camunda.connector.managed-code.gcp.deployer-service-account")
                .formatted(credentials.getClass().getSimpleName()));
      }
      final var client =
          FunctionServiceClient.create(
              FunctionServiceSettings.newBuilder()
                  .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                  .build());
      return new GoogleCloudRunFunctionsApi(client, idTokenProvider, location);
    } catch (IOException e) {
      throw new IllegalStateException("Could not create the Cloud Functions client", e);
    }
  }

  @Override
  public Optional<Function> get(String functionName) {
    try {
      return Optional.of(client.getFunction(functionName));
    } catch (ApiException e) {
      if (code(e) == StatusCode.Code.NOT_FOUND) {
        return Optional.empty();
      }
      throw translate(e);
    }
  }

  /**
   * The upload URL is authorized against the location, which a cluster may only use for this
   * through a custom role: the developer role there would let it list every function.
   */
  @Override
  public StorageSource upload(byte[] zip) {
    final com.google.cloud.functions.v2.GenerateUploadUrlResponse upload;
    try {
      upload =
          client.generateUploadUrl(
              GenerateUploadUrlRequest.newBuilder().setParent(location).build());
    } catch (ApiException e) {
      throw translate(e);
    }
    try {
      final var response =
          http.send(
              HttpRequest.newBuilder(URI.create(upload.getUploadUrl()))
                  .timeout(Duration.ofMinutes(1))
                  .header("Content-Type", "application/zip")
                  .PUT(HttpRequest.BodyPublishers.ofByteArray(zip))
                  .build(),
              HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() / 100 != 2) {
        throw new ProvisioningException(
            "PLATFORM_UNAVAILABLE",
            "Source upload failed with HTTP " + response.statusCode(),
            response.statusCode() >= 500 || response.statusCode() == 429);
      }
      return upload.getStorageSource();
    } catch (IOException e) {
      throw new ProvisioningException(
          "PLATFORM_UNAVAILABLE", "Could not upload the function source", true, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProvisioningException("PLATFORM_UNAVAILABLE", "Upload was interrupted", true, e);
    }
  }

  @Override
  public boolean create(String functionId, Function function) {
    final var request =
        CreateFunctionRequest.newBuilder()
            .setParent(location)
            .setFunctionId(functionId)
            .setFunction(function)
            .build();
    try {
      // Returns once the long-running operation is accepted; the caller polls the function.
      client
          .createFunctionCallable()
          .futureCall(request)
          .get(OPERATION_ACCEPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      return true;
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ApiException api) {
        if (code(api) == StatusCode.Code.ALREADY_EXISTS) {
          return false;
        }
        throw translate(api);
      }
      throw new ProvisioningException(
          "PLATFORM_UNAVAILABLE", "Create failed: " + e.getCause(), true, e.getCause());
    } catch (TimeoutException e) {
      throw new ProvisioningException(
          "PLATFORM_UNAVAILABLE", "The create call was not answered in time", true, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProvisioningException("PLATFORM_UNAVAILABLE", "Create was interrupted", true, e);
    }
  }

  @Override
  public void delete(String functionName) {
    try {
      client
          .deleteFunctionCallable()
          .futureCall(DeleteFunctionRequest.newBuilder().setName(functionName).build())
          .get(OPERATION_ACCEPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ApiException api) {
        if (code(api) == StatusCode.Code.NOT_FOUND) {
          return;
        }
        throw translate(api);
      }
      throw new ProvisioningException(
          "PLATFORM_UNAVAILABLE", "Delete failed: " + e.getCause(), true, e.getCause());
    } catch (TimeoutException e) {
      throw new ProvisioningException(
          "PLATFORM_UNAVAILABLE", "The delete call was not answered in time", true, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProvisioningException("PLATFORM_UNAVAILABLE", "Delete was interrupted", true, e);
    }
  }

  @Override
  public HttpResult invoke(String uri, byte[] body, Duration timeout) {
    final String token;
    try {
      token = idToken(uri);
    } catch (IOException e) {
      // Also how a new impersonation grant looks until it applies, about 2 minutes.
      throw new ProvisioningException(
          "PLATFORM_UNAVAILABLE", "Could not mint an ID token: " + e.getMessage(), true, e);
    }
    try {
      final var response =
          http.send(
              HttpRequest.newBuilder(URI.create(uri))
                  .timeout(timeout)
                  .header("Authorization", "Bearer " + token)
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                  .build(),
              HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return new HttpResult(response.statusCode(), response.body());
    } catch (IOException e) {
      throw new ProvisioningException(
          "PLATFORM_UNAVAILABLE", "Could not invoke the function: " + e.getMessage(), true, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProvisioningException(
          "PLATFORM_UNAVAILABLE", "Invocation was interrupted", true, e);
    }
  }

  @Override
  public void close() {
    client.close();
  }

  /** One credential per function URL; it refreshes its token before expiry. */
  private String idToken(String audience) throws IOException {
    final var credentials =
        idTokens.computeIfAbsent(
            audience,
            key ->
                IdTokenCredentials.newBuilder()
                    .setIdTokenProvider(idTokenProvider)
                    .setTargetAudience(key)
                    .setOptions(List.of(IdTokenProvider.Option.INCLUDE_EMAIL))
                    .build());
    credentials.refreshIfExpired();
    return credentials.getIdToken().getTokenValue();
  }

  private static StatusCode.Code code(ApiException e) {
    return e.getStatusCode().getCode();
  }

  /**
   * Throttling and unavailability are retryable. So is a permission denial: a new cluster's grants
   * take up to about 2 minutes to apply. The provider stops waiting on a denial before the job
   * does, so a misconfiguration consumes the job's retries and ends in an incident.
   */
  static ProvisioningException translate(ApiException e) {
    final var code = code(e);
    return switch (code) {
      case RESOURCE_EXHAUSTED ->
          new ProvisioningException("PLATFORM_THROTTLED", message(e), true, e);
      case UNAVAILABLE, DEADLINE_EXCEEDED, INTERNAL, ABORTED ->
          new ProvisioningException("PLATFORM_UNAVAILABLE", message(e), true, e);
      case PERMISSION_DENIED, UNAUTHENTICATED ->
          new ProvisioningException(DENIED, message(e), true, e);
      default -> new ProvisioningException("PROVIDER_REJECTED", message(e), false, e);
    };
  }

  private static String message(ApiException e) {
    final var message = e.getMessage();
    final var text = message == null ? e.getStatusCode().getCode().name() : message;
    return text.length() <= 512 ? text : text.substring(0, 512);
  }
}
