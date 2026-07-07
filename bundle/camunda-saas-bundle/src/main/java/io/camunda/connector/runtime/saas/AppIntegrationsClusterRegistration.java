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
package io.camunda.connector.runtime.saas;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reports this SaaS cluster's App Integrations connector availability to the App Integrations
 * backend when the runtime starts.
 *
 * <p>If the App Integrations connector settings are provisioned for the cluster, the runtime
 * registers the cluster with {@code PUT /api/connector/{orgId}/{clusterId}}; otherwise it
 * deregisters it with {@code DELETE /api/connector/{orgId}/{clusterId}}. Both requests are
 * authenticated with a shared API key ({@code APP_INTEGRATIONS_SECRET}) sent in the {@code
 * X-API-KEY} header — no OAuth client is built here.
 *
 * <p>{@code APP_INTEGRATIONS_BASE_URL} and {@code APP_INTEGRATIONS_SECRET} are fleet-level values
 * that address and authenticate the reporting endpoint (including the disable/{@code DELETE} case).
 * "Settings present" therefore refers to the per-cluster connector configuration, detected by the
 * presence of the connector's OAuth client credentials.
 */
@Component
@Profile("!test")
public class AppIntegrationsClusterRegistration {

  static final String API_KEY_HEADER = "X-API-KEY";

  private static final Logger LOGGER =
      LoggerFactory.getLogger(AppIntegrationsClusterRegistration.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final int MAX_RETRIES = 3;
  private static final Duration RETRY_DELAY = Duration.ofSeconds(2);

  private final String baseUrl;
  private final String apiKey;
  private final boolean settingsPresent;
  private final String organizationId;
  private final String clusterId;
  private final HttpClient httpClient;
  private final int maxRetries;
  private final Duration retryDelay;

  @Autowired
  public AppIntegrationsClusterRegistration(
      @Value("${APP_INTEGRATIONS_BASE_URL:}") String baseUrl,
      @Value("${APP_INTEGRATIONS_SECRET:}") String apiKey,
      @Value("${APP_INTEGRATIONS_OAUTH_CLIENT_ID:}") String oauthClientId,
      @Value("${APP_INTEGRATIONS_OAUTH_CLIENT_SECRET:}") String oauthClientSecret,
      @Value("${camunda.connector.cloud.organization.id:}") String organizationId,
      @Value("${camunda.client.cloud.clusterId:}") String clusterId) {
    this(
        baseUrl,
        apiKey,
        // The App Integrations connector is provisioned for this cluster when its per-cluster OAuth
        // client credentials are present.
        isPresent(oauthClientId) && isPresent(oauthClientSecret),
        organizationId,
        clusterId,
        HttpClient.newBuilder().connectTimeout(TIMEOUT).build(),
        MAX_RETRIES,
        RETRY_DELAY);
  }

  AppIntegrationsClusterRegistration(
      String baseUrl,
      String apiKey,
      boolean settingsPresent,
      String organizationId,
      String clusterId,
      HttpClient httpClient) {
    // Convenience for tests: short retry delay so the retry path runs quickly.
    this(
        baseUrl,
        apiKey,
        settingsPresent,
        organizationId,
        clusterId,
        httpClient,
        2,
        Duration.ofMillis(1));
  }

  AppIntegrationsClusterRegistration(
      String baseUrl,
      String apiKey,
      boolean settingsPresent,
      String organizationId,
      String clusterId,
      HttpClient httpClient,
      int maxRetries,
      Duration retryDelay) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.settingsPresent = settingsPresent;
    this.organizationId = organizationId;
    this.clusterId = clusterId;
    this.httpClient = httpClient;
    this.maxRetries = maxRetries;
    this.retryDelay = retryDelay;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void reportAvailability() {
    if (!isPresent(baseUrl)
        || !isPresent(apiKey)
        || !isPresent(organizationId)
        || !isPresent(clusterId)) {
      LOGGER.info(
          "Skipping App Integrations cluster registration: base URL, API key, organization id or "
              + "cluster id is not configured");
      return;
    }

    var uri =
        URI.create(
            baseUrl.replaceAll("/+$", "") + "/api/connector/" + organizationId + "/" + clusterId);
    var method = settingsPresent ? "PUT" : "DELETE";
    var request =
        HttpRequest.newBuilder(uri)
            .timeout(TIMEOUT)
            .header(API_KEY_HEADER, apiKey)
            .method(method, BodyPublishers.noBody())
            .build();

    // Retry transient failures (I/O errors and 5xx responses) with a bounded, backing-off policy,
    // mirroring the Failsafe usage elsewhere in the runtime. Client errors (4xx) are not retried.
    RetryPolicy<HttpResponse<String>> retryPolicy =
        RetryPolicy.<HttpResponse<String>>builder()
            .handle(IOException.class)
            .handleResultIf(response -> response != null && response.statusCode() >= 500)
            .withBackoff(retryDelay, retryDelay.multipliedBy(8))
            .withMaxRetries(maxRetries)
            .onRetry(
                event ->
                    LOGGER.warn(
                        "Retrying App Integrations cluster registration ({} {}) after attempt {} failed",
                        method,
                        uri,
                        event.getAttemptCount()))
            .build();

    try {
      HttpResponse<String> response =
          Failsafe.with(retryPolicy).get(() -> httpClient.send(request, BodyHandlers.ofString()));
      if (response.statusCode() >= 400) {
        LOGGER.warn(
            "App Integrations cluster registration ({} {}) returned status {}",
            method,
            uri,
            response.statusCode());
      } else {
        LOGGER.info(
            "Reported App Integrations availability: {} {} -> {}",
            method,
            uri,
            response.statusCode());
      }
    } catch (Exception e) {
      // Never let a reporting failure prevent the runtime from starting.
      if (e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      LOGGER.warn(
          "Failed to report App Integrations availability ({} {}): {}",
          method,
          uri,
          e.getMessage(),
          e);
    }
  }

  private static boolean isPresent(String value) {
    return value != null && !value.isBlank();
  }
}
