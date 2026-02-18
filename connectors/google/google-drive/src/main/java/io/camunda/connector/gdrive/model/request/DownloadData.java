/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.model.request;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;

public record DownloadData(
    @TemplateProperty(
            group = "operationDetails",
            label = "File ID",
            feel = FeelMode.optional,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "resource.type",
                    equals = "download"),
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String fileId) {}
