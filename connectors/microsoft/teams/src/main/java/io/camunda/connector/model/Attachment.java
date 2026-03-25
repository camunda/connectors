/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

public record Attachment(
    @NotBlank
        @TemplateProperty(label = "Attachment ID", description = "Unique ID for the attachment")
        String id,
    @NotBlank
        @TemplateProperty(
            label = "Content type",
            description =
                "The media type of the content attachment"
                    + " (e.g. 'application/vnd.microsoft.card.adaptive')")
        String contentType,
    @TemplateProperty(
            label = "Content",
            description = "The content of the attachment (e.g. an Adaptive Card JSON payload)")
        String content) {}
