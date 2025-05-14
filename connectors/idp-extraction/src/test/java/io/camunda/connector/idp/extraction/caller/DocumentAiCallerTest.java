/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.cloud.documentai.v1.Document;
import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1.ProcessRequest;
import com.google.cloud.documentai.v1.ProcessResponse;
import io.camunda.client.api.response.DocumentMetadata;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.StructuredExtractionResponse;
import io.camunda.connector.idp.extraction.model.providers.GcpProvider;
import io.camunda.connector.idp.extraction.model.providers.gcp.DocumentAiRequestConfiguration;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthentication;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthenticationType;
import io.camunda.connector.idp.extraction.supplier.DocumentAiClientSupplier;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentAiCallerTest {

  @Test
  void extractKeyValuePairsWithConfidence_SuccessfulExtraction() throws Exception {
    // Arrange
    DocumentAiClientSupplier mockSupplier = mock(DocumentAiClientSupplier.class);
    DocumentProcessorServiceClient mockClient = mock(DocumentProcessorServiceClient.class);
    ProcessResponse mockResponse = mock(ProcessResponse.class);
    Document mockDocument = mock(Document.class);

    when(mockSupplier.getDocumentAiClient(any(GcpAuthentication.class))).thenReturn(mockClient);
    when(mockClient.processDocument((ProcessRequest) any())).thenReturn(mockResponse);
    when(mockResponse.getDocument()).thenReturn(mockDocument);

    DocumentAiCaller caller = new DocumentAiCaller(mockSupplier);

    // Mock the document pages with form fields
    Document.Page mockPage = mock(Document.Page.class);
    Document.Page.FormField mockFormField = mock(Document.Page.FormField.class);
    Document.TextAnchor mockNameAnchor = mock(Document.TextAnchor.class);
    Document.TextAnchor mockValueAnchor = mock(Document.TextAnchor.class);

    // Create mock Layout objects
    Document.Page.Layout mockNameLayout = mock(Document.Page.Layout.class);
    Document.Page.Layout mockValueLayout = mock(Document.Page.Layout.class);

    // Set up the form field with field name and value layouts
    when(mockFormField.getFieldName()).thenReturn(mockNameLayout);
    when(mockFormField.getFieldValue()).thenReturn(mockValueLayout);
    when(mockFormField.hasFieldName()).thenReturn(true);
    when(mockFormField.hasFieldValue()).thenReturn(true);
    when(mockFormField.getValueType()).thenReturn(null);

    // Set up text anchors for the layouts
    when(mockNameLayout.getTextAnchor()).thenReturn(mockNameAnchor);
    when(mockValueLayout.getTextAnchor()).thenReturn(mockValueAnchor);

    // Set up confidence values on the layouts
    when(mockNameLayout.getConfidence()).thenReturn(0.95f);
    when(mockValueLayout.getConfidence()).thenReturn(0.85f);

    // Set up document text and pages
    when(mockDocument.getText()).thenReturn("Invoice Total: $100.00");
    when(mockPage.getFormFieldsList()).thenReturn(List.of(mockFormField));
    when(mockDocument.getPagesList()).thenReturn(List.of(mockPage));

    // Set up text content for anchors instead of using text segments
    when(mockNameAnchor.getContent()).thenReturn("Invoice");
    when(mockValueAnchor.getContent()).thenReturn("Total:");

    // Mock entities for additional key-value pairs
    Document.Entity mockEntity = mock(Document.Entity.class);
    Document.Entity mockKeyProperty = mock(Document.Entity.class);
    Document.Entity mockValueProperty = mock(Document.Entity.class);
    Document.TextAnchor mockKeyAnchor = mock(Document.TextAnchor.class);
    Document.TextAnchor mockValAnchor = mock(Document.TextAnchor.class);

    when(mockKeyProperty.getType()).thenReturn("key");
    when(mockValueProperty.getType()).thenReturn("value");
    when(mockKeyProperty.getMentionText()).thenReturn("Date");
    when(mockValueProperty.getMentionText()).thenReturn("2023-05-15");

    // Set up text content for key and value anchors
    when(mockKeyAnchor.getContent()).thenReturn("Date");
    when(mockValAnchor.getContent()).thenReturn("2023-05-15");

    lenient().when(mockKeyProperty.getTextAnchor()).thenReturn(mockKeyAnchor);
    lenient().when(mockValueProperty.getTextAnchor()).thenReturn(mockValAnchor);

    when(mockKeyProperty.getConfidence()).thenReturn(0.98f);
    when(mockValueProperty.getConfidence()).thenReturn(0.92f);

    when(mockEntity.getType()).thenReturn("key_value_pair");

    when(mockEntity.getPropertiesList()).thenReturn(List.of(mockKeyProperty, mockValueProperty));
    when(mockDocument.getEntitiesList()).thenReturn(List.of(mockEntity));

    // Create and configure the GcpProvider
    GcpProvider baseRequest = new GcpProvider();
    DocumentAiRequestConfiguration configuration =
        new DocumentAiRequestConfiguration(
            "us", // region
            "test-project", // projectId
            "test-processor" // processorId
            );
    baseRequest.setConfiguration(configuration);

    // Set authentication
    GcpAuthentication authentication =
        new GcpAuthentication(
            GcpAuthenticationType.BEARER,
            "test-token", // bearerToken
            null, // oauthRefreshToken
            null, // oauthClientId
            null, // oauthClientSecret
            null // serviceAccountJson
            );
    baseRequest.setAuthentication(authentication);

    // Mock ExtractionRequestData and Document
    ExtractionRequestData requestData = mock(ExtractionRequestData.class);
    io.camunda.document.Document mockInputDocument = mock(io.camunda.document.Document.class);
    DocumentMetadata mockMetadata = mock(DocumentMetadata.class);
    InputStream mockInputStream = new ByteArrayInputStream("test document content".getBytes());

    when(requestData.document()).thenReturn(mockInputDocument);
    when(mockInputDocument.asInputStream()).thenReturn(mockInputStream);
    when(mockInputDocument.metadata()).thenReturn(mockMetadata);
    when(mockMetadata.getContentType()).thenReturn("application/pdf");

    // Act
    StructuredExtractionResponse response =
        caller.extractKeyValuePairsWithConfidence(requestData, baseRequest);

    // Assert
    Map<String, String> expectedKeyValuePairs = new HashMap<>();
    expectedKeyValuePairs.put("Invoice", "Total:");

    Map<String, Float> expectedConfidenceScores = new HashMap<>();
    expectedConfidenceScores.put("Invoice", 0.85f); // Min of 0.95 and 0.85

    assertEquals(expectedKeyValuePairs, response.extractedFields());
    assertEquals(expectedConfidenceScores, response.confidenceScore());
  }
}
