/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.mockito.Mockito.when;

import dev.langchain4j.mcp.client.McpBlobResourceContents;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpTextResourceContents;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyList;
import io.camunda.connector.agenticai.mcp.client.filters.AllowDenyListBuilder;
import io.camunda.connector.agenticai.mcp.client.model.result.ResourceData;
import io.camunda.connector.api.error.ConnectorException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadResourceRequestTest {

  private static final AllowDenyList EMPTY_FILTER = AllowDenyList.allowingEverything();

  @Mock private McpClient mcpClient;

  @InjectMocks private ReadResourceRequest testee;

  @Test
  void returnsProperlyMappedResources_whenReadResourceYieldsDifferentContent() {
    final Map<String, Object> requestParams = Map.of("uri", "contents-123");
    final var response =
        new McpReadResourceResult(
            List.of(
                new McpTextResourceContents("uri", "text content", "text/plain"),
                new McpBlobResourceContents(
                    "uri", "YmluYXJ5IGNvbnRlbnQ=", "application/octet-stream")));

    when(mcpClient.readResource("contents-123")).thenReturn(response);

    final var result = testee.execute(mcpClient, EMPTY_FILTER, requestParams);

    assertThat(result.contents())
        .asInstanceOf(LIST)
        .hasSize(2)
        .usingRecursiveComparison()
        .isEqualTo(
            List.of(
                new ResourceData.TextResourceData("uri", "text/plain", "text content"),
                new ResourceData.BlobResourceData(
                    "uri",
                    "application/octet-stream",
                    "binary content".getBytes(StandardCharsets.UTF_8))));
  }

  @Test
  void readsResource_whenResourcePassesAllowFilter() {
    when(mcpClient.readResource("allowed-resource"))
        .thenReturn(
            new McpReadResourceResult(
                List.of(new McpTextResourceContents("allowed-resource", "content", "text/plain"))));

    final var filter = AllowDenyListBuilder.builder().allowed(List.of("allowed-resource")).build();
    final var parameters = Map.<String, Object>of("uri", "allowed-resource");

    final var result = testee.execute(mcpClient, filter, parameters);

    assertThat(result.contents()).asInstanceOf(LIST).hasSize(1);
  }

  @Test
  void readsResource_whenResourceNotInDenyFilter() {
    when(mcpClient.readResource("safe-resource"))
        .thenReturn(
            new McpReadResourceResult(
                List.of(new McpTextResourceContents("safe-resource", "content", "text/plain"))));

    final var filter = AllowDenyListBuilder.builder().denied(List.of("blocked-resource")).build();
    final var parameters = Map.<String, Object>of("uri", "safe-resource");

    final var result = testee.execute(mcpClient, filter, parameters);

    assertThat(result.contents()).asInstanceOf(LIST).hasSize(1);
  }

  @Test
  void throwsConnectorException_whenClientErrorOccurs() {
    final Map<String, Object> requestParams = Map.of("uri", "non-existing-resource");

    when(mcpClient.readResource("non-existing-resource"))
        .thenThrow(new RuntimeException("Resource not found"));

    assertThatThrownBy(() -> testee.execute(mcpClient, EMPTY_FILTER, requestParams))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            exception ->
                assertThat(exception)
                    .returns("MCP_CLIENT_READ_RESOURCE_ERROR", ConnectorException::getErrorCode)
                    .returns(
                        "Failed to read resource from MCP server: Resource not found",
                        ConnectorException::getMessage));
  }

  @Test
  void throwsException_whenResourceUriIsNotPresent() {
    assertThatThrownBy(() -> testee.execute(mcpClient, EMPTY_FILTER, Map.of()))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            exception ->
                assertThat(exception)
                    .returns("MCP_CLIENT_INVALID_PARAMS", ConnectorException::getErrorCode)
                    .returns("Resource URI must be provided", ConnectorException::getMessage));
  }

  @Test
  void throwsConnectorException_whenResourceUriIsNotAString() {
    final Map<String, Object> requestParams = Map.of("uri", 12345);

    assertThatThrownBy(() -> testee.execute(mcpClient, EMPTY_FILTER, requestParams))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            exception ->
                assertThat(exception)
                    .returns("MCP_CLIENT_INVALID_PARAMS", ConnectorException::getErrorCode)
                    .returns("Resource URI must be a string", ConnectorException::getMessage));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void throwsConnectorException_whenResourceUriIsEmpty(String resourceUri) {
    final Map<String, Object> requestParams = Map.of("uri", resourceUri);

    assertThatThrownBy(() -> testee.execute(mcpClient, EMPTY_FILTER, requestParams))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            exception ->
                assertThat(exception)
                    .returns("MCP_CLIENT_INVALID_PARAMS", ConnectorException::getErrorCode)
                    .returns(
                        "Resource URI must not be blank or empty", ConnectorException::getMessage));
  }

  @Test
  void throwsException_whenResourceNotIncludedInFilter() {
    final var filter = AllowDenyListBuilder.builder().allowed(List.of("allowed-resource")).build();

    final var parameters = Map.<String, Object>of("uri", "blocked-resource");

    assertThatThrownBy(() -> testee.execute(mcpClient, filter, parameters))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo("MCP_CLIENT_READ_RESOURCE_ERROR");
              assertThat(exception.getMessage())
                  .isEqualTo(
                      "Reading resource 'blocked-resource' is not allowed by filter configuration: [allowed=[allowed-resource], denied=[]]");
            });
  }

  @Test
  void throwsException_whenResourceExcludedInFilter() {
    final var filter = AllowDenyListBuilder.builder().denied(List.of("blocked-resource")).build();

    final var parameters = Map.<String, Object>of("uri", "blocked-resource");

    assertThatThrownBy(() -> testee.execute(mcpClient, filter, parameters))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo("MCP_CLIENT_READ_RESOURCE_ERROR");
              assertThat(exception.getMessage())
                  .isEqualTo(
                      "Reading resource 'blocked-resource' is not allowed by filter configuration: [allowed=[], denied=[blocked-resource]]");
            });
  }

  @Test
  void throwsException_whenResourceInDenyListEvenIfInAllowList() {
    final var filter =
        AllowDenyListBuilder.builder()
            .allowed(List.of("conflicted-resource"))
            .denied(List.of("conflicted-resource"))
            .build();

    final var parameters = Map.<String, Object>of("uri", "conflicted-resource");

    assertThatThrownBy(() -> testee.execute(mcpClient, filter, parameters))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo("MCP_CLIENT_READ_RESOURCE_ERROR");
              assertThat(exception.getMessage())
                  .contains(
                      "Reading resource 'conflicted-resource' is not allowed by filter configuration");
            });
  }
}
