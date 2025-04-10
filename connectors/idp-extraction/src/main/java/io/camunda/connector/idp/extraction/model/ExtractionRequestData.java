/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model;

import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyBinding;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.document.Document;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ExtractionRequestData(
    @TemplateProperty(
            id = "document",
            label = "Document",
            group = "input",
            type = TemplateProperty.PropertyType.Hidden,
            description = "Specify the document",
            defaultValue = "= input.document",
            binding = @PropertyBinding(name = "document"),
            feel = Property.FeelMode.disabled,
            constraints = @PropertyConstraints(notEmpty = true))
        @NotNull
        Document document,
    @TemplateProperty(
            id = "extractionType",
            label = "Extraction Type",
            group = "input",
            type = TemplateProperty.PropertyType.Hidden,
            defaultValue = "= input.extractionType",
            description = "Specify extraction type (structured or unstructured)",
            binding = @PropertyBinding(name = "extractionType"),
            feel = Property.FeelMode.disabled,
            constraints = @PropertyConstraints(notEmpty = true))
        @NotNull
        ExtractionType extractionType,
    @TemplateProperty(
            id = "taxonomyItems",
            label = "Taxonomy Items",
            group = "input",
            type = TemplateProperty.PropertyType.Hidden,
            description = "Array of taxonomy items",
            defaultValue = "= input.taxonomyItems",
            binding = @PropertyBinding(name = "taxonomyItems"),
            feel = Property.FeelMode.disabled)
        List<TaxonomyItem> taxonomyItems,
    @TemplateProperty(
            id = "excludedFields",
            label = "Excluded Fields",
            group = "input",
            type = TemplateProperty.PropertyType.Hidden,
            description = "List of fields that should not be returned from the extraction",
            defaultValue = "= input.excludedFields",
            binding = @PropertyBinding(name = "excludedFields"),
            feel = Property.FeelMode.disabled)
        List<String> excludedFields,
    @TemplateProperty(
            id = "delimiter",
            label = "delimiter",
            group = "input",
            type = TemplateProperty.PropertyType.Hidden,
            description = "The delimiter used for the variable name of the extracted field",
            defaultValue = "= input.delimiter",
            binding = @PropertyBinding(name = "delimiter"),
            feel = Property.FeelMode.disabled)
        String delimiter,
    @TemplateProperty(
            id = "converseData",
            label = "AWS Bedrock Converse Parameters",
            group = "input",
            type = TemplateProperty.PropertyType.Hidden,
            description = "Specify the parameters for AWS Bedrock",
            defaultValue = "= input.converseData",
            binding = @PropertyBinding(name = "converseData"),
            feel = Property.FeelMode.disabled)
        ConverseData converseData) {}
