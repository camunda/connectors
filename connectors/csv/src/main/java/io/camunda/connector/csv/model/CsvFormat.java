/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.csv.model;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import java.util.List;

public record CsvFormat(
    @TemplateProperty(label = "Delimiter", tooltip = "CSV column delimiter", defaultValue = ",")
        String delimiter,
    @TemplateProperty(
            label = "Skip Header Record",
            tooltip = "Skips the first row to be not included in the final records.",
            defaultValue = "true",
            defaultValueType = TemplateProperty.DefaultValueType.Boolean)
        boolean skipHeaderRecord,
    @TemplateProperty(
            label = "Headers",
            tooltip = "Mapping of the columns if not included in the CSV itself in the first row.",
            feel = FeelMode.required)
        List<String> headers) {}
