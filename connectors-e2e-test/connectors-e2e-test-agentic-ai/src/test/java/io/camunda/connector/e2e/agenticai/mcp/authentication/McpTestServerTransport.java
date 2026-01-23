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
package io.camunda.connector.e2e.agenticai.mcp.authentication;

import java.util.Map;
import java.util.Optional;

public enum McpTestServerTransport {
  HTTP(
      null,
      "http",
      "/mcp",
      "camunda.connector.agenticai.mcp.client.clients.%s.http",
      "data.transport.http"),
  SSE(
      "transport-sse",
      "sse",
      "/sse",
      "camunda.connector.agenticai.mcp.client.clients.%s.sse",
      "data.transport.sse");

  private final String profile;
  private final String type;
  private final String urlPath;
  private final String standaloneConfigPrefixPattern;
  private final String remoteConfigPrefix;

  McpTestServerTransport(
      String profile,
      String type,
      String urlPath,
      String standaloneConfigPrefixPattern,
      String remoteConfigPrefix) {
    this.profile = profile;
    this.type = type;
    this.urlPath = urlPath;
    this.standaloneConfigPrefixPattern = standaloneConfigPrefixPattern;
    this.remoteConfigPrefix = remoteConfigPrefix;
  }

  public Optional<String> getProfile() {
    return Optional.ofNullable(profile);
  }

  public String standaloneConfigPrefix(String clientName) {
    return standaloneConfigPrefixPattern.formatted(clientName);
  }

  public String remoteConfigPrefix() {
    return remoteConfigPrefix;
  }

  public void applyStandalone(
      Map<String, String> properties, String clientName, String mcpServerBaseUrl) {
    final var configPrefix = standaloneConfigPrefix(clientName);

    properties.put(
        "camunda.connector.agenticai.mcp.client.clients.%s.type".formatted(clientName), type);
    properties.put(configPrefix + ".url", mcpServerBaseUrl + urlPath);
  }

  public void applyRemote(Map<String, String> properties, String mcpServerBaseUrl) {
    properties.put("data.transport.type", type);
    properties.put(remoteConfigPrefix + ".url", mcpServerBaseUrl + urlPath);
  }
}
