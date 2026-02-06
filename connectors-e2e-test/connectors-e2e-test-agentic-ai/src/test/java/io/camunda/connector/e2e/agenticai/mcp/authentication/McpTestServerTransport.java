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
  HTTP(null, "http", "/mcp"),
  SSE("transport-sse", "sse", "/sse");

  private final String testServerProfile;
  private final String type;
  private final String urlPath;

  McpTestServerTransport(String testServerProfile, String type, String urlPath) {
    this.testServerProfile = testServerProfile;
    this.type = type;
    this.urlPath = urlPath;
  }

  public Optional<String> getTestServerProfile() {
    return Optional.ofNullable(testServerProfile);
  }

  public String springConfigPrefix(String clientName) {
    return "camunda.connector.agenticai.mcp.client.clients.%s.%s".formatted(clientName, type);
  }

  public String remoteConnectorInputMappingPrefix() {
    return "data.transport.%s".formatted(type);
  }

  public void applySpringConfigProperties(
      Map<String, String> properties, String clientName, String mcpServerBaseUrl) {
    properties.put(
        "camunda.connector.agenticai.mcp.client.clients.%s.type".formatted(clientName), type);
    properties.put("%s.url".formatted(springConfigPrefix(clientName)), mcpServerBaseUrl + urlPath);
  }

  public void applyRemoteConnectorInputMappings(
      Map<String, String> inputMappings, String mcpServerBaseUrl) {
    inputMappings.put("data.transport.type", type);
    inputMappings.put(
        "%s.url".formatted(remoteConnectorInputMappingPrefix()), mcpServerBaseUrl + urlPath);
  }
}
