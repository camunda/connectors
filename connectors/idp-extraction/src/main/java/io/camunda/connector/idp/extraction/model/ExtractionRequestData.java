/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyBinding;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record ExtractionRequestData(
    @TemplateProperty(
            id = "document",
            label = "Document",
            group = "input",
            type = TemplateProperty.PropertyType.Hidden,
            description = "Specify the document",
            defaultValue = "= input.document",
            binding = @PropertyBinding(name = "document"),
            feel = FeelMode.disabled,
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
            feel = FeelMode.disabled,
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
            feel = FeelMode.disabled)
        List<TaxonomyItem> taxonomyItems,
    @TemplateProperty(
            id = "includedFields",
            label = "Included Fields",
            group = "input",
            type = TemplateProperty.PropertyType.Hidden,
            description = "List of fields that should be returned from the extraction",
            defaultValue = "= input.includedFields",
            binding = @PropertyBinding(name = "includedFields"),
            feel = FeelMode.disabled)
        List<String> includedFields,
    @TemplateProperty(
            id = "renameMappings",
            label = "Rename mappings",
            group = "input",
            type = TemplateProperty.PropertyType.Hidden,
            description = "List of keys that should be renamed and not be given the default name",
            defaultValue = "= input.renameMappings",
            binding = @PropertyBinding(name = "renameMappings"),
            feel = FeelMode.disabled)
        Map<String, String> renameMappings,
    @TemplateProperty(
            id = "delimiter",
            label = "delimiter",
            group = "input",
            type = TemplateProperty.PropertyType.Hidden,
            description = "The delimiter used for the variable name of the extracted field",
            defaultValue = "= input.delimiter",
            binding = @PropertyBinding(name = "delimiter"),
            feel = FeelMode.disabled)
        String delimiter,
    @TemplateProperty(
            id = "converseData",
            label = "AWS Bedrock Converse Parameters",
            group = "input",
            type = TemplateProperty.PropertyType.Hidden,
            description = "Specify the parameters for AWS Bedrock",
            defaultValue = "= input.converseData",
            binding = @PropertyBinding(name = "converseData"),
            feel = FeelMode.disabled)
        ConverseData converseData) {

  // Compact constructor that sets default value for extractionType if null
  public ExtractionRequestData {
    if (extractionType == null) {
      extractionType = ExtractionType.UNSTRUCTURED;
    }
  }
}
