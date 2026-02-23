/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.model.request.operation;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "addWorkItemsToTheQueue", label = "Add work item to the queue")
public record AddWorkItemOperationData(
    @TemplateProperty(
            label = "Work queue ID",
            group = "input",
            description = "The queue ID of the item")
        @NotNull
        Object queueId,
    @TemplateProperty(
            label = "Work item json data",
            group = "input",
            feel = FeelMode.required,
            type = TemplateProperty.PropertyType.Text,
            description =
                "Work item json input data. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/automation-anywhere/\" target=\"_blank\">documentation</a>")
        @NotNull
        Object data)
    implements OperationData {}
