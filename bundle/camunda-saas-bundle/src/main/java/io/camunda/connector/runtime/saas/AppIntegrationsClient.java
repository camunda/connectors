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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Talks to the App Integrations backend to (de)register this SaaS cluster.
 *
 * <p>{@code registerCluster} performs a single {@code PUT} (settings present) or {@code DELETE}
 * (settings absent) against {@code /api/connector/{orgId}/{clusterId}}, authenticated with the
 * shared {@code APP_INTEGRATIONS_CONNECTOR_SECRET} sent in the {@code X-API-KEY} header. It never
 * throws — the returned {@link RegistrationOutcome} tells the caller whether the failure was
 * transient and worth retrying. Scheduling/retrying is the caller's concern (see {@link
 * AppIntegrationsClusterRegistration}).
 *
 * <p>{@code APP_INTEGRATIONS_BASE_URL} and {@code APP_INTEGRATIONS_CONNECTOR_SECRET} are
 * fleet-level values that address and authenticate the endpoint (including the disable/{@code
 * DELETE} case).
 */
@Component
public class AppIntegrationsClient {

  /** Outcome of a registration attempt, telling the caller whether to retry. */
  public enum RegistrationOutcome {
    /** Reported successfully, or failed permanently — do not retry. */
    DONE,
    /** Transient failure — the caller should retry later. */
    RETRY
  }

  static final String API_KEY_HEADER = "X-API-KEY";

  private static final Logger LOGGER = LoggerFactory.getLogger(AppIntegrationsClient.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final String baseUrl;
  private final String apiKey;
  private final HttpClient httpClient;

  @Autowired
  public AppIntegrationsClient(
      @Value("${APP_INTEGRATIONS_BASE_URL:}") String baseUrl,
      @Value("${APP_INTEGRATIONS_CONNECTOR_SECRET:}") String apiKey) {
    this(baseUrl, apiKey, HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
  }

  AppIntegrationsClient(String baseUrl, String apiKey, HttpClient httpClient) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.httpClient = httpClient;
  }

  /** Whether the fleet-level endpoint configuration (base URL and API key) is available. */
  boolean isConfigured() {
    return isPresent(baseUrl) && isPresent(apiKey);
  }

  /**
   * Registers ({@code settingsPresent}) or deregisters the cluster with the App Integrations
   * backend. Never throws.
   */
  RegistrationOutcome registerCluster(
      String organizationId, String clusterId, boolean settingsPresent) {
    var method = settingsPresent ? "PUT" : "DELETE";

    final HttpRequest request;
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
      // A malformed base URL (or invalid org/cluster id) is a misconfiguration that a retry cannot
      // fix, so stop rather than loop.
      LOGGER.warn(
          "Cannot build App Integrations registration request for base URL '{}': {}",
          baseUrl,
          e.getMessage());
      return RegistrationOutcome.DONE;
    }

    try {
      HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());
      int status = response.statusCode();
      if (status < 400) {
        LOGGER.info(
            "Reported App Integrations availability: {} {} -> {}", method, request.uri(), status);
        return RegistrationOutcome.DONE;
      }
      if (status < 500) {
        // Client error — retrying will not help.
        LOGGER.warn(
            "App Integrations cluster registration ({} {}) returned client error {}; giving up",
            method,
            request.uri(),
            status);
        return RegistrationOutcome.DONE;
      }
      LOGGER.warn(
          "App Integrations cluster registration ({} {}) returned {}; will retry",
          method,
          request.uri(),
          status);
      return RegistrationOutcome.RETRY;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOGGER.warn(
          "Interrupted while reporting App Integrations availability ({} {})",
          method,
          request.uri());
      return RegistrationOutcome.DONE;
    } catch (Exception e) {
      LOGGER.warn(
          "Failed to report App Integrations availability ({} {}); will retry: {}",
          method,
          request.uri(),
          e.getMessage());
      return RegistrationOutcome.RETRY;
    }
  }

  private static boolean isPresent(String value) {
    return value != null && !value.isBlank();
  }
}
