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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Reports this SaaS cluster's App Integrations connector availability to the App Integrations
 * backend when the runtime starts.
 *
 * <p>If the App Integrations connector settings are provisioned for the cluster, the runtime
 * registers the cluster with {@code PUT /api/connector/{orgId}/{clusterId}}; otherwise it
 * deregisters it with {@code DELETE /api/connector/{orgId}/{clusterId}}. Both requests are
 * authenticated with a shared API key ({@code APP_INTEGRATIONS_CONNECTOR_SECRET}) sent in the
 * {@code X-API-KEY} header — no OAuth client is built here.
 *
 * <p>{@code APP_INTEGRATIONS_BASE_URL} and {@code APP_INTEGRATIONS_CONNECTOR_SECRET} are
 * fleet-level values that address and authenticate the reporting endpoint (including the
 * disable/{@code DELETE} case). "Settings present" therefore refers to the per-cluster connector
 * configuration, detected by the presence of the connector's OAuth client credentials.
 *
 * <p>The report runs off the startup thread as a one-time scheduled task. A transient failure (I/O
 * error or {@code 5xx}) reschedules the task with a capped exponential backoff, so the runtime
 * keeps trying until the report succeeds. A client error ({@code 4xx}) is treated as permanent and
 * is not retried. Nothing here can prevent the runtime from starting.
 */
@Component
@Profile("!test")
public class AppIntegrationsClusterRegistration {

  static final String API_KEY_HEADER = "X-API-KEY";

  private static final Logger LOGGER =
      LoggerFactory.getLogger(AppIntegrationsClusterRegistration.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(5);
  private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(5);

  private final String baseUrl;
  private final String apiKey;
  private final boolean settingsPresent;
  private final String organizationId;
  private final String clusterId;
  private final HttpClient httpClient;
  private final TaskScheduler taskScheduler;
  private final Duration initialRetryDelay;
  private final Duration maxRetryDelay;

  @Autowired
  public AppIntegrationsClusterRegistration(
      @Value("${APP_INTEGRATIONS_BASE_URL:}") String baseUrl,
      @Value("${APP_INTEGRATIONS_CONNECTOR_SECRET:}") String apiKey,
      @Value("${APP_INTEGRATIONS_OAUTH_CLIENT_ID:}") String oauthClientId,
      @Value("${APP_INTEGRATIONS_OAUTH_CLIENT_SECRET:}") String oauthClientSecret,
      @Value("${camunda.connector.cloud.organization.id:}") String organizationId,
      @Value("${camunda.client.cloud.clusterId:}") String clusterId,
      TaskScheduler taskScheduler) {
    this(
        baseUrl,
        apiKey,
        // The App Integrations connector is provisioned for this cluster when its per-cluster OAuth
        // client credentials are present.
        isPresent(oauthClientId) && isPresent(oauthClientSecret),
        organizationId,
        clusterId,
        HttpClient.newBuilder().connectTimeout(TIMEOUT).build(),
        taskScheduler,
        INITIAL_RETRY_DELAY,
        MAX_RETRY_DELAY);
  }

  AppIntegrationsClusterRegistration(
      String baseUrl,
      String apiKey,
      boolean settingsPresent,
      String organizationId,
      String clusterId,
      HttpClient httpClient,
      TaskScheduler taskScheduler) {
    // Convenience for tests: short retry delays so the backoff numbers stay small.
    this(
        baseUrl,
        apiKey,
        settingsPresent,
        organizationId,
        clusterId,
        httpClient,
        taskScheduler,
        Duration.ofMillis(1),
        Duration.ofMillis(10));
  }

  AppIntegrationsClusterRegistration(
      String baseUrl,
      String apiKey,
      boolean settingsPresent,
      String organizationId,
      String clusterId,
      HttpClient httpClient,
      TaskScheduler taskScheduler,
      Duration initialRetryDelay,
      Duration maxRetryDelay) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.settingsPresent = settingsPresent;
    this.organizationId = organizationId;
    this.clusterId = clusterId;
    this.httpClient = httpClient;
    this.taskScheduler = taskScheduler;
    this.initialRetryDelay = initialRetryDelay;
    this.maxRetryDelay = maxRetryDelay;
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

    // Build the request up front so a malformed base URL (or invalid org/cluster id) fails fast
    // here rather than escaping the startup thread or looping forever on the scheduler.
    final HttpRequest request;
    var method = settingsPresent ? "PUT" : "DELETE";
    try {
      var uri =
          URI.create(
              baseUrl.replaceAll("/+$", "") + "/api/connector/" + organizationId + "/" + clusterId);
      request =
          HttpRequest.newBuilder(uri)
              .timeout(TIMEOUT)
              .header(API_KEY_HEADER, apiKey)
              .method(method, BodyPublishers.noBody())
              .build();
    } catch (RuntimeException e) {
      LOGGER.warn(
          "Skipping App Integrations cluster registration: cannot build request for base URL '{}': {}",
          baseUrl,
          e.getMessage());
      return;
    }

    // Run off the startup thread; retry transient failures until the report succeeds.
    taskScheduler.schedule(() -> attempt(request, method, initialRetryDelay), Instant.now());
  }

  private void attempt(HttpRequest request, String method, Duration retryDelay) {
    try {
      HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
      int status = response.statusCode();
      if (status < 400) {
        LOGGER.info(
            "Reported App Integrations availability: {} {} -> {}", method, request.uri(), status);
        return;
      }
      if (status < 500) {
        // Client error — retrying will not help, so stop.
        LOGGER.warn(
            "App Integrations cluster registration ({} {}) returned client error {}; giving up",
            method,
            request.uri(),
            status);
        return;
      }
      LOGGER.warn(
          "App Integrations cluster registration ({} {}) returned {}; retrying in {}",
          method,
          request.uri(),
          status,
          retryDelay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.warn(
          "Interrupted while reporting App Integrations availability ({} {})",
          method,
          request.uri());
      return;
    } catch (Exception e) {
      LOGGER.warn(
          "Failed to report App Integrations availability ({} {}); retrying in {}: {}",
          method,
          request.uri(),
          retryDelay,
          e.getMessage());
    }

    var doubled = retryDelay.multipliedBy(2);
    var nextDelay = doubled.compareTo(maxRetryDelay) > 0 ? maxRetryDelay : doubled;
    taskScheduler.schedule(
        () -> attempt(request, method, nextDelay), Instant.now().plus(retryDelay));
  }

  private static boolean isPresent(String value) {
    return value != null && !value.isBlank();
  }
}
