/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.model.request;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;

public record UploadData(
    @TemplateProperty(
            group = "operationDetails",
            label = "Document",
            feel = FeelMode.required,
            type = TemplateProperty.PropertyType.String,
            condition =
                @TemplateProperty.PropertyCondition(property = "resource.type", equals = "upload"),
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true),
            description =
                "Upload camunda document, <a href=\"https://docs.camunda.io/docs/apis-tools/camunda-api-rest/specifications/upload-document-alpha/\">see documentation</a>")
        Document document) {}
