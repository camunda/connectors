/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import java.util.List;

public record ToolsConfiguration(
    @TemplateProperty(
            group = "tools",
            label = "Ad-hoc sub-process ID",
            description = "ID of the sub-process that contains the tools the AI agent can use.",
            tooltip =
                "Add an ad-hoc sub-process ID to attach the AI agent to the tools. Ensure your process includes a tools "
                    + "feedback loop routing into the ad-hoc sub-process and back to the AI agent connector. "
                    + "<a href=\"https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent-task/\" target=\"_blank\">See documentation</a> "
                    + "for details.",
            optional = true)
        String containerElementId,
    @FEEL
        @TemplateProperty(
            group = "tools",
            label = "Tool call results",
            description = "Tool call results as returned by the sub-process.",
            tooltip =
                "This defines where to handle tool call results returned by the ad-hoc sub-process. Model this "
                    + "as part of your process and route it into the tools feedback loop. "
                    + "<a href=\"https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent-task/\" target=\"_blank\">See documentation</a> "
                    + "for details.",
            type = TemplateProperty.PropertyType.Text,
            feel = FeelMode.required,
            optional = true)
        List<ToolCallResult> toolCallResults) {}
