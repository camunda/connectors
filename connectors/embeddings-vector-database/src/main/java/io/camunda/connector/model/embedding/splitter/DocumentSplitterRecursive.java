/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.splitter;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DefaultValueType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(label = "Recursive", id = DocumentSplitterRecursive.DOCUMENT_SPLITTER_RECURSIVE)
public record DocumentSplitterRecursive(
    @NotBlank
        @TemplateProperty(
            group = "document",
            id = "document.splitter.recursive.maxSegmentSizeInChars",
            label = "Max chars",
            description = "Max splitting segment size in chars",
            defaultValueType = DefaultValueType.Number,
            defaultValue = "500")
        Integer maxSegmentSizeInChars,
    @NotBlank
        @TemplateProperty(
            group = "document",
            id = "document.splitter.recursive.maxOverlapSizeInChars",
            label = "Max overlap window",
            description = "Max segment splitting overlap size in chars",
            defaultValueType = DefaultValueType.Number,
            defaultValue = "80")
        Integer maxOverlapSizeInChars)
    implements DocumentSplitter {
  @TemplateProperty(ignore = true)
  public static final String DOCUMENT_SPLITTER_RECURSIVE = "documentSplitterRecursive";
}
