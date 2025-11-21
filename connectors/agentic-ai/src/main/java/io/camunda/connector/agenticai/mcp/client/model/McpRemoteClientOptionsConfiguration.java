/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;

public record McpRemoteClientOptionsConfiguration(
    @TemplateProperty(
            group = "options",
            label = "MCP client caching",
            description = "If checked, the MCP client instance is cached on the connector runtime.",
            tooltip =
                "Only enable this option if you are sure that the configuration or authentication details of this client will not change between invocations. Caching the client instance can improve performance by reusing existing connections.",
            type = TemplateProperty.PropertyType.Boolean,
            optional = true)
        Boolean clientCache) {}
