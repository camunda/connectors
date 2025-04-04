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

@TemplateSubType(label = "By sentence", id = DocumentSplitter.DOCUMENT_SPLITTER_BY_SENTENCE)
public record DocumentSplitterBySentence(
    @NotBlank
        @TemplateProperty(
            group = "document",
            id = "embeddingsStore.document.splitter.bySentence.maxSegmentSizeInChars",
            label = "Max segment",
            description = "Max segment size in chars")
        String maxSegmentSizeInChars,
    @NotBlank
        @TemplateProperty(
            group = "document",
            id = "embeddingsStore.document.splitter.bySentence.maxOverlapSizeInChars",
            label = "Max overlap window",
            description = "Max overlap size in chars")
        String maxOverlapSizeInChars)
    implements DocumentSplitter {}
