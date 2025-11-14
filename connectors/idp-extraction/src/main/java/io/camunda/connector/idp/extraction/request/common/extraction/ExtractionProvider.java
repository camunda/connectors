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
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotNull;

@NotNull
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type",
    defaultImpl = ExtractionProvider.ApachePdfBoxExtractorRequest.class)
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = ExtractionProvider.ApachePdfBoxExtractorRequest.class,
      name = "pdfBox"),
  @JsonSubTypes.Type(
      value = ExtractionProvider.MultimodalExtractorRequest.class,
      name = "multimodal"),
  @JsonSubTypes.Type(value = TextractExtractorRequest.class, name = "textract"),
  @JsonSubTypes.Type(
      value = DocumentIntelligenceExtractorRequest.class,
      name = "documentIntelligence"),
  @JsonSubTypes.Type(value = DocumentAiExtractorRequest.class, name = "documentAi")
})
@TemplateDiscriminatorProperty(
    label = "Text extraction providers",
    group = "extractor",
    name = "type")
public sealed interface ExtractionProvider
    permits ExtractionProvider.ApachePdfBoxExtractorRequest,
        ExtractionProvider.MultimodalExtractorRequest,
        DocumentIntelligenceExtractorRequest,
        DocumentAiExtractorRequest,
        TextractExtractorRequest {

  @TemplateSubType(id = "pdfBox", label = "Apache PdfBox text extractor")
  record ApachePdfBoxExtractorRequest() implements ExtractionProvider {}

  @TemplateSubType(id = "multimodal", label = "In-model text extractor (multimodal)")
  record MultimodalExtractorRequest() implements ExtractionProvider {}
}
