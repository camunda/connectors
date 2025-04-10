/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model.providers;

import io.camunda.connector.generator.java.annotation.TemplateProperty;

public record DocumentAiRequestConfiguration(
    @TemplateProperty(
            group = "configuration",
            id = "documentAiRegion",
            label = "Region",
            description = "Can be 'eu' or 'us'")
        String region,
    @TemplateProperty(group = "configuration", id = "projectId", label = "Project ID")
        String projectId,
    @TemplateProperty(
            group = "configuration",
            id = "processorId",
            label = "Processor ID",
            description = "The id of the processor used to parse the document")
        String processorId) {}
