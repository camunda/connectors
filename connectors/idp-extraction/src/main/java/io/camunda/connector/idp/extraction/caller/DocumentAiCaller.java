/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import com.google.cloud.documentai.v1.Document;
import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1.ProcessRequest;
import com.google.cloud.documentai.v1.ProcessResponse;
import com.google.cloud.documentai.v1.RawDocument;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.StructuredExtractionResponse;
import io.camunda.connector.idp.extraction.model.providers.GcpProvider;
import io.camunda.connector.idp.extraction.model.providers.gcp.DocumentAiRequestConfiguration;
import io.camunda.connector.idp.extraction.supplier.DocumentAiClientSupplier;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentAiCaller {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentAiCaller.class);
  private final DocumentAiClientSupplier documentAiClientSupplier;

  public DocumentAiCaller() {
    this.documentAiClientSupplier = new DocumentAiClientSupplier();
  }

  public DocumentAiCaller(DocumentAiClientSupplier supplier) {
    this.documentAiClientSupplier = supplier;
  }

  public StructuredExtractionResponse extractKeyValuePairsWithConfidence(
      ExtractionRequestData input, GcpProvider baseRequest) {
    try {
      // Get DocumentAI client and process the document
      try (DocumentProcessorServiceClient client =
          documentAiClientSupplier.getDocumentAiClient(baseRequest.getAuthentication())) {
        DocumentAiRequestConfiguration requestConfiguration =
            (DocumentAiRequestConfiguration) baseRequest.getConfiguration();
        String processorName =
            String.format(
                "projects/%s/locations/%s/processors/%s",
                requestConfiguration.getProjectId(),
                requestConfiguration.getRegion(),
                requestConfiguration.getProcessorId());

        ProcessRequest request;

        // Use raw document input stream for processing
        InputStream documentStream = input.document().asInputStream();
        request =
            ProcessRequest.newBuilder()
                .setName(processorName)
                .setRawDocument(
                    RawDocument.newBuilder()
                        .setContent(com.google.protobuf.ByteString.readFrom(documentStream))
                        .setMimeType(
                            input.document().metadata().getContentType() != null
                                ? input.document().metadata().getContentType()
                                : "application/pdf")
                        .build())
                .build();

        // Process the document
        ProcessResponse response = client.processDocument(request);
        Document document = response.getDocument();

        // Extract key-value pairs with confidence scores
        StructuredExtractionResponse extractionResponse = extractFormFieldsWithConfidence(document);
        LOGGER.debug(
            "Document AI extracted {} key-value pairs",
            extractionResponse.extractedFields().size());

        return extractionResponse;
      }
    } catch (IOException e) {
      LOGGER.error("Error while processing document with Document AI", e);
      throw new RuntimeException(e);
    }
  }

  private StructuredExtractionResponse extractFormFieldsWithConfidence(Document document) {
    Map<String, String> keyValuePairs = new HashMap<>();
    Map<String, Float> confidenceScores = new HashMap<>();

    // Process form fields from Document AI response
    for (Document.Page page : document.getPagesList()) {
      for (Document.Page.FormField formField : page.getFormFieldsList()) {
        if (formField.hasFieldName() && formField.hasFieldValue()) {
          String name = getTextFromLayout(document, formField.getFieldName().getTextAnchor());
          String value = getTextFromLayout(document, formField.getFieldValue().getTextAnchor());

          if (name != null && !name.trim().isEmpty()) {
            // Get confidence scores from both name and value fields
            float nameConfidence = formField.getFieldName().getConfidence();
            float valueConfidence = formField.getFieldValue().getConfidence();

            // Use the lower of the two confidence scores (conservative approach)
            float combinedConfidence = Math.min(nameConfidence, valueConfidence);

            keyValuePairs.put(name.trim(), value != null ? value.trim() : "");
            confidenceScores.put(name.trim(), combinedConfidence);
          }
        }
      }
    }

    // If available, also get key-value pairs from entities
    for (Document.Entity entity : document.getEntitiesList()) {
      if (entity.getType().equals("key_value_pair") || entity.getType().equals("form_field")) {
        String key = null;
        String value = null;
        float keyConfidence = 0;
        float valueConfidence = 0;

        for (Document.Entity property : entity.getPropertiesList()) {
          if (property.getType().equals("key")) {
            key = property.getMentionText();
            keyConfidence = property.getConfidence();
          } else if (property.getType().equals("value")) {
            value = property.getMentionText();
            valueConfidence = property.getConfidence();
          }
        }

        if (key != null && !key.trim().isEmpty()) {
          // Use the lower of the two confidence scores
          float combinedConfidence = Math.min(keyConfidence, valueConfidence);

          keyValuePairs.put(key.trim(), value != null ? value.trim() : "");
          confidenceScores.put(key.trim(), combinedConfidence);
        }
      }
    }

    return new StructuredExtractionResponse(keyValuePairs, confidenceScores);
  }

  private String getTextFromLayout(Document document, Document.TextAnchor textAnchor) {
    if (textAnchor == null || textAnchor.getTextSegmentsList().isEmpty()) {
      return "";
    }

    StringBuilder result = new StringBuilder();
    for (Document.TextAnchor.TextSegment segment : textAnchor.getTextSegmentsList()) {
      int startIndex = (int) segment.getStartIndex();
      int endIndex = (int) segment.getEndIndex();

      if (startIndex >= 0 && endIndex > startIndex && endIndex <= document.getText().length()) {
        result.append(document.getText(), startIndex, endIndex);
      }
    }

    return result.toString();
  }
}
