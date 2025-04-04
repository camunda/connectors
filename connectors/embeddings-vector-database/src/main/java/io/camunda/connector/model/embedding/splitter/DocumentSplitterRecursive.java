/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.splitter;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;

@TemplateSubType(label = "Recursive", id = DocumentSplitter.DOCUMENT_SPLITTER_RECURSIVE)
public record DocumentSplitterRecursive(
    @NotBlank
        @TemplateProperty(
            group = "document",
            id = "embeddingsStore.document.splitter.recursive.maxSegmentSizeInChars",
            label = "Max segment",
            description = "Max segment size in chars")
        String maxSegmentSizeInChars,
    @NotBlank
        @TemplateProperty(
            group = "document",
            id = "embeddingsStore.document.splitter.recursive.maxOverlapSizeInChars",
            label = "Max overlap window",
            description = "Max overlap size in chars")
        String maxOverlapSizeInChars)
    implements DocumentSplitter {}
