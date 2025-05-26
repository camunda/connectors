/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import com.google.cloud.documentai.v1.Document;
import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1.NormalizedVertex;
import com.google.cloud.documentai.v1.ProcessRequest;
import com.google.cloud.documentai.v1.ProcessResponse;
import com.google.cloud.documentai.v1.RawDocument;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.Polygon;
import io.camunda.connector.idp.extraction.model.PolygonPoint;
import io.camunda.connector.idp.extraction.model.StructuredExtractionResponse;
import io.camunda.connector.idp.extraction.model.providers.GcpProvider;
import io.camunda.connector.idp.extraction.model.providers.gcp.DocumentAiRequestConfiguration;
import io.camunda.connector.idp.extraction.supplier.DocumentAiClientSupplier;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    Map<String, Object> keyValuePairs = new HashMap<>();
    Map<String, Float> confidenceScores = new HashMap<>();
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

        List<Document.Page.Table.TableRow> rows = new ArrayList<>();
        rows.addAll(table.getHeaderRowsList());
        rows.addAll(table.getBodyRowsList());

        for (Document.Page.Table.TableRow row : rows) {
          processTableRow(document, row, data);
        }

        String tableKey = "table " + nextTableIndex++;
        keyValuePairs.put(tableKey, data);

        // Calculate table confidence as average of all cell confidences
        float tableConfidence = 0.0f;
        int cellCount = 0;

        for (Document.Page.Table.TableRow row : rows) {
          for (Document.Page.Table.TableCell cell : row.getCellsList()) {
            if (cell.hasLayout()) {
              tableConfidence += cell.getLayout().getConfidence();
              cellCount++;
            }
          }
        }

        if (cellCount > 0) {
          tableConfidence /= cellCount;
        }

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

  private void processTableRow(
      Document document, Document.Page.Table.TableRow row, List<List<String>> data) {
    List<String> rowData = new ArrayList<>();

    for (Document.Page.Table.TableCell cell : row.getCellsList()) {
      String cellText = "";
      if (cell.hasLayout()) {
        cellText = getTextFromLayout(document, cell.getLayout().getTextAnchor());
      }
      rowData.add(cellText);
    }

    if (!rowData.isEmpty()) {
      data.add(rowData);
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
