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
import io.camunda.connector.idp.extraction.model.providers.DocumentAIProvider;
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

  public Map<String, String> extractKeyValuePairs(
      ExtractionRequestData input, DocumentAIProvider baseRequest) {
    try {
      // Get DocumentAI client and process the document
      try (DocumentProcessorServiceClient client =
          documentAiClientSupplier.getDocumentAiClient(baseRequest)) {

        String processorName =
            String.format(
                "projects/%s/locations/%s/processors/%s",
                baseRequest.getConfiguration().projectId(),
                baseRequest.getConfiguration().region(),
                baseRequest.getConfiguration().processorId());

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

        // Extract key-value pairs from form fields
        Map<String, String> keyValuePairs = extractFormFields(document);
        LOGGER.debug("Document AI extracted {} key-value pairs", keyValuePairs.size());

        return keyValuePairs;
      }
    } catch (IOException e) {
      LOGGER.error("Error while processing document with Document AI", e);
      throw new RuntimeException(e);
    }
  }

  private Map<String, String> extractFormFields(Document document) {
    Map<String, String> keyValuePairs = new HashMap<>();

    // Process form fields from Document AI response
    for (Document.Page page : document.getPagesList()) {
      for (Document.Page.FormField formField : page.getFormFieldsList()) {
        if (formField.hasFieldName() && formField.hasFieldValue()) {
          String name = getTextFromLayout(document, formField.getFieldName().getTextAnchor());
          String value = getTextFromLayout(document, formField.getFieldValue().getTextAnchor());

          if (name != null && !name.trim().isEmpty()) {
            keyValuePairs.put(name.trim(), value != null ? value.trim() : "");
          }
        }
      }
    }

    // If available, also get key-value pairs from entities
    for (Document.Entity entity : document.getEntitiesList()) {
      if (entity.getType().equals("key_value_pair") || entity.getType().equals("form_field")) {
        String key = null;
        String value = null;

        for (Document.Entity property : entity.getPropertiesList()) {
          if (property.getType().equals("key")) {
            key = property.getMentionText();
          } else if (property.getType().equals("value")) {
            value = property.getMentionText();
          }
        }

        if (key != null && !key.trim().isEmpty()) {
          keyValuePairs.put(key.trim(), value != null ? value.trim() : "");
        }
      }
    }

    return keyValuePairs;
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
        result.append(document.getText().substring(startIndex, endIndex));
      }
    }

    return result.toString();
  }
}
