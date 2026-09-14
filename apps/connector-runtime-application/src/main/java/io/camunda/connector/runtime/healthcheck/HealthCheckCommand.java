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
package io.camunda.connector.runtime.healthcheck;

import java.net.HttpURLConnection;
import java.net.URI;

/**
 * Lightweight health check command that can be invoked as a standalone Java process by Docker
 * HEALTHCHECK. This is necessary because hardened base images do not include wget or curl.
 *
 * <p>Checks the Spring Boot Actuator readiness endpoint and exits with code 0 on success (HTTP 200)
 * or code 1 on failure.
 *
 * <p>Actuator is served on the management port, kept separate from the public API port (see {@code
 * management.server.port} in {@code application.properties}) — so the defaults below follow that
 * port, not {@code server.port}. The health check URL can be configured via environment variables:
 *
 * <ul>
 *   <li>{@code HEALTHCHECK_URL} — full URL override; if set, all other variables are ignored
 *   <li>{@code MANAGEMENT_SERVER_PORT} — defaults to {@code 9080}
 *   <li>{@code MANAGEMENT_SERVER_BASE_PATH} — defaults to {@code /actuator}
 * </ul>
 */
public class HealthCheckCommand {

  private static final String DEFAULT_MANAGEMENT_PORT = "9080";
  private static final String DEFAULT_MANAGEMENT_BASE_PATH = "/actuator";

  public static void main(String[] args) {
    HttpURLConnection connection = null;
    try {
      String url = resolveUrl();

      connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);

      int responseCode = connection.getResponseCode();
      if (responseCode == HttpURLConnection.HTTP_OK) {
        System.exit(0);
      } else {
        System.err.println("Health check failed with HTTP status: " + responseCode);
        System.exit(1);
      }
    } catch (Exception e) {
      System.err.println("Health check failed: " + e.getMessage());
      System.exit(1);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  private static String resolveUrl() {
    String url = System.getenv("HEALTHCHECK_URL");
    if (url != null && !url.isBlank()) {
      return url;
    }
    String port = envOrDefault("MANAGEMENT_SERVER_PORT", DEFAULT_MANAGEMENT_PORT);
    String basePath = envOrDefault("MANAGEMENT_SERVER_BASE_PATH", DEFAULT_MANAGEMENT_BASE_PATH);
    return "http://localhost:" + port + normalizeBasePath(basePath) + "/health/readiness";
  }

  private static String envOrDefault(String name, String defaultValue) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }

  private static String normalizeBasePath(String basePath) {
    String normalized = basePath.startsWith("/") ? basePath : "/" + basePath;
    return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
  }
}
