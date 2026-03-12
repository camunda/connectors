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
 * <p>The server port and base path can be configured via environment variables:
 *
 * <ul>
 *   <li>{@code SERVER_PORT} — defaults to {@code 8080}
 *   <li>{@code MANAGEMENT_SERVER_PORT} — if set, takes precedence over {@code SERVER_PORT}
 *   <li>{@code MANAGEMENT_SERVER_BASE_PATH} — defaults to {@code /actuator}
 * </ul>
 */
public class HealthCheckCommand {

  public static void main(String[] args) {
    HttpURLConnection connection = null;
    try {
      String port = resolvePort();
      String basePath = resolveBasePath();
      String url = "http://localhost:" + port + basePath + "/health/readiness";

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

  private static String resolvePort() {
    String managementPort = System.getenv("MANAGEMENT_SERVER_PORT");
    if (managementPort != null && !managementPort.isBlank()) {
      return managementPort;
    }
    String serverPort = System.getenv("SERVER_PORT");
    if (serverPort != null && !serverPort.isBlank()) {
      return serverPort;
    }
    return "8080";
  }

  private static String resolveBasePath() {
    String basePath = System.getenv("MANAGEMENT_SERVER_BASE_PATH");
    if (basePath != null && !basePath.isBlank()) {
      return basePath;
    }
    return "/actuator";
  }
}
