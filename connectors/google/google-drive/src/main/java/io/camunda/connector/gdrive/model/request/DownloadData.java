/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.model.request;

import io.camunda.connector.generator.java.annotation.DocumentReturnFormat;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;

@DocumentReturnFormat(
    group = "operationDetails",
    tooltip =
        "How the downloaded payload should be returned. Document reference uploads the payload to"
            + " the document store; as text decodes it as a String; as JSON parses it into a"
            + " structure you can access via dot notation.",
    condition =
        @TemplateProperty.PropertyCondition(property = "resource.type", equals = "download"))
public record DownloadData(
    @TemplateProperty(
            group = "operationDetails",
            label = "File ID",
            feel = FeelMode.optional,
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String fileId) {}
