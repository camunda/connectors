/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.configuration.McpClientConfigurationProperties.McpClientHttpTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.auth.BasicAuthHeadersSupplier;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.auth.BearerAuthHeadersSupplier;
import io.camunda.connector.agenticai.mcp.client.framework.langchain4j.auth.OAuthHeadersSupplier;
import io.camunda.connector.agenticai.mcp.client.model.auth.BasicAuthentication;
import io.camunda.connector.agenticai.mcp.client.model.auth.BearerAuthentication;
import io.camunda.connector.agenticai.mcp.client.model.auth.OAuthAuthentication;
import io.camunda.connector.http.client.authentication.OAuthService;
import io.camunda.connector.http.client.client.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;

public class Langchain4JMcpClientHeadersSupplierFactory {

  private final OAuthService oAuthService;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public Langchain4JMcpClientHeadersSupplierFactory(
      OAuthService oAuthService, HttpClient httpClient, ObjectMapper objectMapper) {
    this.oAuthService = oAuthService;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
  }

  public Supplier<Map<String, String>> createHttpHeadersSupplier(
      McpClientHttpTransportConfiguration httpTransportConfiguration) {
    final List<Supplier<Map<String, String>>> headerSuppliers = new ArrayList<>();
    if (httpTransportConfiguration.headers() != null) {
      headerSuppliers.add(httpTransportConfiguration::headers);
    }

    switch (httpTransportConfiguration.authentication().authentication()) {
      case BasicAuthentication basicAuthentication ->
          headerSuppliers.add(new BasicAuthHeadersSupplier(basicAuthentication));

      case BearerAuthentication bearerAuthentication ->
          headerSuppliers.add(new BearerAuthHeadersSupplier(bearerAuthentication));

      case OAuthAuthentication oAuthAuthentication ->
          headerSuppliers.add(
              new OAuthHeadersSupplier(
                  oAuthService, httpClient, objectMapper, oAuthAuthentication));

      default -> {
        // no authentication to apply
      }
    }

    return new CompositeHeadersSupplier(headerSuppliers);
  }

  public static class CompositeHeadersSupplier implements Supplier<Map<String, String>> {
    private final List<@NonNull Supplier<Map<String, String>>> suppliers;

    public CompositeHeadersSupplier(List<@NonNull Supplier<Map<String, String>>> suppliers) {
      this.suppliers = List.copyOf(suppliers);
    }

    @Override
    public Map<String, String> get() {
      Map<String, String> combinedHeaders = new LinkedHashMap<>();
      for (var supplier : suppliers) {
        combinedHeaders.putAll(supplier.get());
      }

      return combinedHeaders;
    }
  }
}
