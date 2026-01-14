package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.rpc;

import dev.langchain4j.mcp.client.McpBlobResourceContents;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpTextResourceContents;
import io.camunda.connector.agenticai.mcp.client.model.result.ResourceData;
import io.camunda.connector.api.error.ConnectorException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadResourceRequestTest {

  @Mock private McpClient mcpClient;

  @InjectMocks private ReadResourceRequest testee;

  @Test
  void returnsProperlyMappedResources_whenReadResourceYieldsDifferentContent() {
    final Map<String, Object> requestParams = Map.of("resourceUri", "contents-123");
    final var response =
        new McpReadResourceResult(
            List.of(
                new McpTextResourceContents("uri", "text content", "text/plain"),
                new McpBlobResourceContents(
                    "uri", "YmluYXJ5IGNvbnRlbnQ=", "application/octet-stream")));

    when(mcpClient.readResource("contents-123")).thenReturn(response);

    final var result = testee.execute(mcpClient, requestParams);

    assertThat(result.contents())
        .asInstanceOf(LIST)
        .hasSize(2)
        .usingRecursiveComparison()
            .isEqualTo(List.of(
            new ResourceData.TextResourceData("uri", "text/plain", "text content"),
            new ResourceData.BlobResourceData(
                "uri",
                "application/octet-stream",
                "binary content".getBytes(StandardCharsets.UTF_8))));
  }

  @Test
  void throwsConnectorException_whenClientErrorOccurs() {
    final Map<String, Object> requestParams = Map.of("resourceUri", "non-existing-resource");

    when(mcpClient.readResource("non-existing-resource"))
        .thenThrow(new RuntimeException("Resource not found"));

    assertThatThrownBy(() -> testee.execute(mcpClient, requestParams))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            exception ->
                assertThat(exception)
                    .returns("MCP_CLIENT_READ_RESOURCE_FAILED", ConnectorException::getErrorCode)
                    .returns("Failed to read resource from MCP server: Resource not found", ConnectorException::getMessage));
  }

  @Test
  void throwsException_whenResourceUriIsNotPresent() {
    assertThatThrownBy(() -> testee.execute(mcpClient, Map.of()))
            .isInstanceOfSatisfying(
                    ConnectorException.class,
                    exception ->
                            assertThat(exception)
                                    .returns("MCP_CLIENT_INVALID_PARAMS", ConnectorException::getErrorCode)
                                    .returns("Resource URI must be provided", ConnectorException::getMessage));
  }

  @Test
  void throwsConnectorException_whenResourceUriIsNotAString() {
    final Map<String, Object> requestParams = Map.of("resourceUri", 12345);

    assertThatThrownBy(() -> testee.execute(mcpClient, requestParams))
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
    final Map<String, Object> requestParams = Map.of("resourceUri", resourceUri);

    assertThatThrownBy(() -> testee.execute(mcpClient, requestParams))
            .isInstanceOfSatisfying(
                    ConnectorException.class,
                    exception ->
                            assertThat(exception)
                                    .returns("MCP_CLIENT_INVALID_PARAMS", ConnectorException::getErrorCode)
                                    .returns("Resource URI must not be blank or empty", ConnectorException::getMessage));
  }
}
