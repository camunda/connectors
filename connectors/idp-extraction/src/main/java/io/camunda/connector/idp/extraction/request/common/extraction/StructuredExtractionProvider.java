/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.request.common.extraction;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Extraction providers that support structured (form) extraction, i.e. that can return key-value
 * pairs with confidence scores. Only these can be offered by the structured extraction template.
 */
@NotNull
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = DocumentAiExtractorRequest.class, name = "documentAi"),
  @JsonSubTypes.Type(value = TextractExtractorRequest.class, name = "textract")
})
@TemplateDiscriminatorProperty(
    label = "Text extraction providers",
    group = "extractor",
    name = "type")
public sealed interface StructuredExtractionProvider
    permits DocumentAiExtractorRequest, TextractExtractorRequest {}
