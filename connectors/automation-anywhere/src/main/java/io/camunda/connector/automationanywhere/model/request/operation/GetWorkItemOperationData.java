/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.model.request.operation;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(id = "listWorkItemsInQueue", label = "Get work item result from queue by ID")
public record GetWorkItemOperationData(
    @TemplateProperty(
            label = "Work queue ID",
            group = "input",
            id = "workQueueId",
            description = "The queue ID of the item")
        @NotNull
        Object queueId,
    @TemplateProperty(
            label = "Work item ID",
            group = "input",
            description = "The queue item identifier to be fetched from queue")
        @NotNull
        Integer workItemId)
    implements OperationData {}
