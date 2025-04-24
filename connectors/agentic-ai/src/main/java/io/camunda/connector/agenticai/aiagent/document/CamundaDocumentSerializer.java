/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.TextFileContent;
import io.camunda.client.api.response.DocumentMetadata;
import io.camunda.document.Document;
import io.camunda.document.reference.DocumentReference.CamundaDocumentReference;
import java.io.IOException;

public class CamundaDocumentSerializer extends JsonSerializer<Document> {

  private static final String TYPE_TEXT = "text";
  private static final String TYPE_BASE64 = "base64";

  private final CamundaDocumentToContentConverter contentConverter;

  public CamundaDocumentSerializer(CamundaDocumentToContentConverter contentConverter) {
    this.contentConverter = contentConverter;
  }

  @Override
  public void serialize(
      Document document, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {

    final var reference = document.reference();
    if (!(reference instanceof CamundaDocumentReference camundaReference)) {
      throw new IllegalArgumentException("Unsupported document reference type: " + reference);
    }

    final var contentBlock = contentConverter.convert(document);
    final var response =
        convertContentBlock(camundaReference, camundaReference.getMetadata(), contentBlock);

    jsonGenerator.writeObject(response);
  }

  private CamundaDocumentResponseModel convertContentBlock(
      CamundaDocumentReference reference, DocumentMetadata metadata, Content contentBlock) {

    return switch (contentBlock) {
      case TextContent tc ->
          new CamundaDocumentResponseModel(
              TYPE_TEXT, metadata.getContentType(), metadata.getFileName(), tc.text());

      case TextFileContent tf ->
          new CamundaDocumentResponseModel(
              TYPE_BASE64,
              metadata.getContentType(),
              metadata.getFileName(),
              tf.textFile().base64Data());

      case PdfFileContent tf ->
          new CamundaDocumentResponseModel(
              TYPE_BASE64,
              metadata.getContentType(),
              metadata.getFileName(),
              tf.pdfFile().base64Data());

      case ImageContent tf ->
          new CamundaDocumentResponseModel(
              TYPE_BASE64,
              metadata.getContentType(),
              metadata.getFileName(),
              tf.image().base64Data());

      default ->
          throw new IllegalArgumentException(
              "Unsupported content block type '%s' for document with reference '%s'"
                  .formatted(contentBlock.getClass().getSimpleName(), reference.toString()));
    };
  }

  /**
   * Adheres to the Claude schema for content blocks in user messages + filename. Revisit if this is
   * enough as we gain more hands-on experience with different models.
   */
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  public record CamundaDocumentResponseModel(
      String type, String mediaType, String filename, String data) {}
}
