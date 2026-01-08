package io.camunda.connector.agenticai.mcp.client.model.result;

import java.util.List;

public record McpClientListResourceTemplatesResult(List<ResourceTemplate> resourceTemplates) implements McpClientResult {}
