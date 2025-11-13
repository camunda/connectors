/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.client.extraction;

import com.google.cloud.documentai.v1.Document;
import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1.DocumentProcessorServiceSettings;
import com.google.cloud.documentai.v1.NormalizedVertex;
import com.google.cloud.documentai.v1.ProcessRequest;
import com.google.cloud.documentai.v1.ProcessResponse;
import com.google.cloud.documentai.v1.RawDocument;
import com.google.protobuf.ByteString;
import io.camunda.connector.idp.extraction.client.extraction.base.MlExtractor;
import io.camunda.connector.idp.extraction.client.extraction.base.TextExtractor;
import io.camunda.connector.idp.extraction.model.Polygon;
import io.camunda.connector.idp.extraction.model.PolygonPoint;
import io.camunda.connector.idp.extraction.model.StructuredExtractionResponse;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthentication;
import io.camunda.connector.idp.extraction.utils.GcsUtil;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GcpDocumentAiExtractionClient implements TextExtractor, MlExtractor, AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(GcpDocumentAiExtractionClient.class);
  private final DocumentProcessorServiceClient client;
  private final String projectId;
  private final String region;
  private final String processorId;

  public GcpDocumentAiExtractionClient(
      GcpAuthentication authentication, String projectId, String region, String processorId) {
    this.projectId = projectId;
    this.region = region;
    this.processorId = processorId;
    try {
      DocumentProcessorServiceSettings settings =
          DocumentProcessorServiceSettings.newBuilder()
              .setCredentialsProvider(() -> GcsUtil.getCredentials(authentication))
              .build();
      client = DocumentProcessorServiceClient.create(settings);
    } catch (IOException e) {
      LOGGER.error("Error while initializing DocumentProcessorServiceClient", e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    if (client != null) {
      try {
        client.close();
        LOGGER.debug("DocumentProcessorServiceClient closed successfully");
      } catch (Exception e) {
        LOGGER.warn("Error while closing DocumentProcessorServiceClient", e);
      }
    }
  }

  @Override
  public String extract(io.camunda.connector.api.document.Document document) {
    try {
      String processorName =
          String.format("projects/%s/locations/%s/processors/%s", projectId, region, processorId);

      // Use raw document input stream for processing with try-with-resources
      ProcessRequest request;
      try (InputStream documentStream = document.asInputStream()) {
        request =
            ProcessRequest.newBuilder()
                .setName(processorName)
                .setRawDocument(
                    RawDocument.newBuilder()
                        .setContent(ByteString.readFrom(documentStream))
                        .setMimeType(
                            document.metadata().getContentType() != null
                                ? document.metadata().getContentType()
                                : "application/pdf")
                        .build())
                .build();
      }

      // Process the document
      ProcessResponse response = client.processDocument(request);
      Document gcpDocument = response.getDocument();

      // Extract plain text from the document (similar to AWS Textract's text detection)
      String extractedText = gcpDocument.getText();
      LOGGER.debug("Document AI extracted {} characters of text", extractedText.length());

      return extractedText;

    } catch (IOException e) {
      LOGGER.error("Error while extracting text from document with Document AI", e);
      throw new RuntimeException(e);
    }
  }

  public StructuredExtractionResponse runDocumentAnalysis(
      io.camunda.connector.api.document.Document document) {
    try {
      // Get DocumentAI client and process the document

      String processorName =
          String.format("projects/%s/locations/%s/processors/%s", projectId, region, processorId);

      // Use raw document input stream for processing with try-with-resources
      ProcessRequest request;
      try (InputStream documentStream = document.asInputStream()) {
        request =
            ProcessRequest.newBuilder()
                .setName(processorName)
                .setRawDocument(
                    RawDocument.newBuilder()
                        .setContent(ByteString.readFrom(documentStream))
                        .setMimeType(
                            document.metadata().getContentType() != null
                                ? document.metadata().getContentType()
                                : "application/pdf")
                        .build())
                .build();
      }

      // Process the document
      ProcessResponse response = client.processDocument(request);
      Document gcpDocument = response.getDocument();

      // Extract key-value pairs with confidence scores
      StructuredExtractionResponse extractionResponse =
          extractFormFieldsWithConfidence(gcpDocument);
      LOGGER.debug(
          "Document AI extracted {} key-value pairs", extractionResponse.extractedFields().size());

      return extractionResponse;

    } catch (IOException e) {
      LOGGER.error("Error while processing document with Document AI", e);
      throw new RuntimeException(e);
    }
  }

  private StructuredExtractionResponse extractFormFieldsWithConfidence(Document document) {
    Map<String, Object> keyValuePairs = new HashMap<>();
    Map<String, Object> confidenceScores = new HashMap<>();
    Map<String, Polygon> geometry = new HashMap<>();
    Map<String, Integer> keyOccurrences = new HashMap<>();
    var nextTableIndex = 1;

    // Process form fields from Document AI response
    for (Document.Page page : document.getPagesList()) {
      // get form fields
      for (Document.Page.FormField formField : page.getFormFieldsList()) {
        if (formField.hasFieldName() && formField.hasFieldValue()) {
          String originalKey =
              getTextFromLayout(document, formField.getFieldName().getTextAnchor());
          String key = originalKey;
          String value = getValueFromFormField(document, formField);

          if (!key.isEmpty()) {
            // Handle duplicate keys by adding a suffix
            if (keyValuePairs.containsKey(key)) {
              int count = keyOccurrences.getOrDefault(originalKey, 1) + 1;
              keyOccurrences.put(originalKey, count);
              key = originalKey + " " + count;
            } else {
              keyOccurrences.put(originalKey, 1);
            }
            // Get confidence scores from both name and value fields
            float nameConfidence = formField.getFieldName().getConfidence();
            float valueConfidence = formField.getFieldValue().getConfidence();

            // Use the lower of the two confidence scores (conservative approach)
            float combinedConfidence = Math.min(nameConfidence, valueConfidence);

            keyValuePairs.put(key, value);
            confidenceScores.put(key, combinedConfidence);

            // Extract polygon information
            List<NormalizedVertex> keyPolygon =
                formField.getFieldName().getBoundingPoly().getNormalizedVerticesList();
            List<NormalizedVertex> valuePolygon =
                formField.getFieldValue().getBoundingPoly().getNormalizedVerticesList();
            Polygon polygon =
                new Polygon(page.getPageNumber(), getBoundingPolygon(keyPolygon, valuePolygon));
            geometry.put(key, polygon);
          }
        }
      }

      // get tables
      for (Document.Page.Table table : page.getTablesList()) {
        List<List<String>> data = new ArrayList<>();
        List<List<Float>> tableConfidence = new ArrayList<>();

        List<Document.Page.Table.TableRow> rows = new ArrayList<>();
        rows.addAll(table.getHeaderRowsList());
        rows.addAll(table.getBodyRowsList());

        for (Document.Page.Table.TableRow row : rows) {
          List<String> rowData = new ArrayList<>();
          List<Float> rowConfidence = new ArrayList<>();
          processTableRowWithConfidence(document, row, rowData, rowConfidence);
          if (!rowData.isEmpty()) {
            data.add(rowData);
            tableConfidence.add(rowConfidence);
          }
        }

        String tableKey = "table " + nextTableIndex++;
        keyValuePairs.put(tableKey, data);
        confidenceScores.put(tableKey, tableConfidence);

        List<PolygonPoint> tablePolygon =
            table.getLayout().getBoundingPoly().getNormalizedVerticesList().stream()
                .map(vertex -> new PolygonPoint(vertex.getX(), vertex.getY()))
                .toList();
        geometry.put(tableKey, new Polygon(page.getPageNumber(), tablePolygon));
      }
    }

    return new StructuredExtractionResponse(keyValuePairs, confidenceScores, geometry);
  }

  private String getValueFromFormField(Document document, Document.Page.FormField formField) {
    String valueType = formField.getValueType();
    if (valueType != null && valueType.equals("unfilled_checkbox")) {
      return "false";
    } else if (valueType != null && valueType.equals("filled_checkbox")) {
      return "true";
    } else {
      return getTextFromLayout(document, formField.getFieldValue().getTextAnchor());
    }
  }

  private String getTextFromLayout(Document document, Document.TextAnchor textAnchor) {
    if (textAnchor == null) {
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

    return result.toString().trim();
  }

  private void processTableRowWithConfidence(
      Document document,
      Document.Page.Table.TableRow row,
      List<String> rowData,
      List<Float> rowConfidence) {
    for (Document.Page.Table.TableCell cell : row.getCellsList()) {
      String cellText = "";
      if (cell.hasLayout()) {
        cellText = getTextFromLayout(document, cell.getLayout().getTextAnchor());
      }
      rowData.add(cellText);
      rowConfidence.add(cell.getLayout().getConfidence());
    }
  }

  private List<PolygonPoint> getBoundingPolygon(
      List<NormalizedVertex> polygon1, List<NormalizedVertex> polygon2) {
    float minX = Float.MAX_VALUE;
    float minY = Float.MAX_VALUE;
    float maxX = Float.MIN_VALUE;
    float maxY = Float.MIN_VALUE;

    // Process all points from first polygon
    for (NormalizedVertex point : polygon1) {
      minX = Math.min(minX, point.getX());
      minY = Math.min(minY, point.getY());
      maxX = Math.max(maxX, point.getX());
      maxY = Math.max(maxY, point.getY());
    }

    // Process all points from second polygon
    for (NormalizedVertex point : polygon2) {
      minX = Math.min(minX, point.getX());
      minY = Math.min(minY, point.getY());
      maxX = Math.max(maxX, point.getX());
      maxY = Math.max(maxY, point.getY());
    }

    // Create the 4 corners of the bounding rectangle
    return List.of(
        new PolygonPoint(minX, minY),
        new PolygonPoint(maxX, minY),
        new PolygonPoint(maxX, maxY),
        new PolygonPoint(minX, maxY));
  }
}
