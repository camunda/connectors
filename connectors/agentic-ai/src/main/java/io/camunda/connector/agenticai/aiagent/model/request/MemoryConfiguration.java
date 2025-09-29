/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

public record MemoryConfiguration(
    @Valid @NestedProperties(group = "memory") MemoryStorageConfiguration storage,
    // TODO support more advanced eviction policies (token window)
    @TemplateProperty(
            group = "memory",
            label = "Context window size",
            description =
                "Maximum number of recent conversation messages which are passed to the model.",
            tooltip =
                "Use this to limit the number of messages which are sent to the model. The agent will only send "
                    + "the most recent messages up to the configured limit to the LLM. Older messages will be kept "
                    + "in the conversation store, but not sent to the model. "
                    + "<a href=\"https://docs.camunda.io/docs/8.8/components/connectors/out-of-the-box-connectors/agentic-ai-aiagent-task/\" target=\"_blank\">See documentation</a> "
                    + "for details.",
            type = TemplateProperty.PropertyType.Number,
            defaultValue = "20",
            defaultValueType = TemplateProperty.DefaultValueType.Number)
        @Min(3)
        Integer contextWindowSize) {}
