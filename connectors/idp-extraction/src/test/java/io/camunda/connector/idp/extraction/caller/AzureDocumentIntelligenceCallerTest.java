/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.ai.documentintelligence.DocumentIntelligenceClient;
import com.azure.ai.documentintelligence.DocumentIntelligenceClientBuilder;
import com.azure.ai.documentintelligence.models.AnalyzeDocumentOptions;
import com.azure.ai.documentintelligence.models.AnalyzeOperationDetails;
import com.azure.ai.documentintelligence.models.AnalyzeResult;
import com.azure.ai.documentintelligence.models.DocumentLine;
import com.azure.ai.documentintelligence.models.DocumentPage;
import com.azure.core.credential.KeyCredential;
import com.azure.core.util.polling.SyncPoller;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.providers.AzureProvider;
import io.camunda.connector.idp.extraction.model.providers.azure.DocumentIntelligenceConfiguration;
import io.camunda.connector.idp.extraction.util.ExtractionTestUtils;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AzureDocumentIntelligenceCallerTest {

  DocumentIntelligenceClient documentIntelligenceClient = mock(DocumentIntelligenceClient.class);
  SyncPoller<AnalyzeOperationDetails, AnalyzeResult> syncPoller = mock(SyncPoller.class);
  AnalyzeResult analyzeResult = mock(AnalyzeResult.class);
  AzureDocumentIntelligenceCaller azureCaller = new AzureDocumentIntelligenceCaller();

  MockedConstruction<DocumentIntelligenceClientBuilder> mockedClientBuilder = null;

  @BeforeEach
  void setUp() {
    // Mock the DocumentIntelligenceClientBuilder constructor
    mockedClientBuilder =
        mockConstruction(
            DocumentIntelligenceClientBuilder.class,
            (mock, context) -> {
              // Setup the builder chain to return our mocked client
              when(mock.endpoint(any(String.class))).thenReturn(mock);
              when(mock.credential(any(KeyCredential.class))).thenReturn(mock);
              when(mock.buildClient()).thenReturn(documentIntelligenceClient);
            });
  }

  @AfterEach
  void tearDown() {
    if (mockedClientBuilder != null) {
      mockedClientBuilder.close();
    }
  }

  @Test
  void call_SuccessfulTextExtraction() {
    // Given
    String expectedText = "Line 1\nLine 2\nLine 3";

    DocumentLine line1 = mock(DocumentLine.class);
    DocumentLine line2 = mock(DocumentLine.class);
    DocumentLine line3 = mock(DocumentLine.class);

    when(line1.getContent()).thenReturn("Line 1");
    when(line2.getContent()).thenReturn("Line 2");
    when(line3.getContent()).thenReturn("Line 3");

    DocumentPage page = mock(DocumentPage.class);
    when(page.getLines()).thenReturn(List.of(line1, line2, line3));

    when(analyzeResult.getPages()).thenReturn(List.of(page));
    when(syncPoller.getFinalResult()).thenReturn(analyzeResult);
    when(documentIntelligenceClient.beginAnalyzeDocument(
            eq("prebuilt-read"), any(AnalyzeDocumentOptions.class)))
        .thenReturn(syncPoller);

    AzureProvider azureProvider = createAzureProvider();
    ExtractionRequestData requestData = ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA;

    // When
    String result = azureCaller.call(requestData, azureProvider);

    // Then
    assertEquals(expectedText, result);
    verify(documentIntelligenceClient)
        .beginAnalyzeDocument(eq("prebuilt-read"), any(AnalyzeDocumentOptions.class));
    verify(syncPoller).getFinalResult();
  }

  @Test
  void call_EmptyDocument() {
    // Given
    when(analyzeResult.getPages()).thenReturn(List.of());
    when(syncPoller.getFinalResult()).thenReturn(analyzeResult);
    when(documentIntelligenceClient.beginAnalyzeDocument(
            eq("prebuilt-read"), any(AnalyzeDocumentOptions.class)))
        .thenReturn(syncPoller);

    AzureProvider azureProvider = createAzureProvider();
    ExtractionRequestData requestData = ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA;

    // When
    String result = azureCaller.call(requestData, azureProvider);

    // Then
    assertEquals("", result);
  }

  @Test
  void call_PageWithNoLines() {
    // Given
    DocumentPage page = mock(DocumentPage.class);
    when(page.getLines()).thenReturn(List.of());

    when(analyzeResult.getPages()).thenReturn(List.of(page));
    when(syncPoller.getFinalResult()).thenReturn(analyzeResult);
    when(documentIntelligenceClient.beginAnalyzeDocument(
            eq("prebuilt-read"), any(AnalyzeDocumentOptions.class)))
        .thenReturn(syncPoller);

    AzureProvider azureProvider = createAzureProvider();
    ExtractionRequestData requestData = ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA;

    // When
    String result = azureCaller.call(requestData, azureProvider);

    // Then
    assertEquals("", result);
  }

  @Test
  void call_PageWithNullLines() {
    // Given
    DocumentPage page = mock(DocumentPage.class);
    when(page.getLines()).thenReturn(null);

    when(analyzeResult.getPages()).thenReturn(List.of(page));
    when(syncPoller.getFinalResult()).thenReturn(analyzeResult);
    when(documentIntelligenceClient.beginAnalyzeDocument(
            eq("prebuilt-read"), any(AnalyzeDocumentOptions.class)))
        .thenReturn(syncPoller);

    AzureProvider azureProvider = createAzureProvider();
    ExtractionRequestData requestData = ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA;

    // When
    String result = azureCaller.call(requestData, azureProvider);

    // Then
    assertEquals("", result);
  }

  @Test
  void call_MultiplePagesWithText() {
    // Given
    DocumentLine line1 = mock(DocumentLine.class);
    DocumentLine line2 = mock(DocumentLine.class);
    DocumentLine line3 = mock(DocumentLine.class);
    DocumentLine line4 = mock(DocumentLine.class);

    when(line1.getContent()).thenReturn("Page 1 Line 1");
    when(line2.getContent()).thenReturn("Page 1 Line 2");
    when(line3.getContent()).thenReturn("Page 2 Line 1");
    when(line4.getContent()).thenReturn("Page 2 Line 2");

    DocumentPage page1 = mock(DocumentPage.class);
    DocumentPage page2 = mock(DocumentPage.class);
    when(page1.getLines()).thenReturn(List.of(line1, line2));
    when(page2.getLines()).thenReturn(List.of(line3, line4));

    when(analyzeResult.getPages()).thenReturn(List.of(page1, page2));
    when(syncPoller.getFinalResult()).thenReturn(analyzeResult);
    when(documentIntelligenceClient.beginAnalyzeDocument(
            eq("prebuilt-read"), any(AnalyzeDocumentOptions.class)))
        .thenReturn(syncPoller);

    AzureProvider azureProvider = createAzureProvider();
    ExtractionRequestData requestData = ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA;

    String expectedText = "Page 1 Line 1\nPage 1 Line 2\nPage 2 Line 1\nPage 2 Line 2";

    // When
    String result = azureCaller.call(requestData, azureProvider);

    // Then
    assertEquals(expectedText, result);
  }

  @Test
  void call_VerifyCorrectParametersPassedToClient() {
    // Given
    when(analyzeResult.getPages()).thenReturn(List.of());
    when(syncPoller.getFinalResult()).thenReturn(analyzeResult);
    when(documentIntelligenceClient.beginAnalyzeDocument(
            eq("prebuilt-read"), any(AnalyzeDocumentOptions.class)))
        .thenReturn(syncPoller);

    AzureProvider azureProvider = createAzureProvider();
    ExtractionRequestData requestData = ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA;

    // When
    azureCaller.call(requestData, azureProvider);

    // Then
    ArgumentCaptor<AnalyzeDocumentOptions> optionsCaptor =
        ArgumentCaptor.forClass(AnalyzeDocumentOptions.class);
    verify(documentIntelligenceClient)
        .beginAnalyzeDocument(eq("prebuilt-read"), optionsCaptor.capture());

  }

  @Test
  void call_ExceptionDuringAnalysis() {
    // Given
    String errorMessage = "Azure service unavailable";
    when(documentIntelligenceClient.beginAnalyzeDocument(
            eq("prebuilt-read"), any(AnalyzeDocumentOptions.class)))
        .thenThrow(new RuntimeException(errorMessage));

    AzureProvider azureProvider = createAzureProvider();
    ExtractionRequestData requestData = ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA;

    // When & Then
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> azureCaller.call(requestData, azureProvider));

    assertEquals("Failed to extract text from document: " + errorMessage, exception.getMessage());
    assertEquals(errorMessage, exception.getCause().getMessage());
  }

  @Test
  void call_ExceptionDuringPolling() {
    // Given
    String errorMessage = "Polling failed";
    when(documentIntelligenceClient.beginAnalyzeDocument(
            eq("prebuilt-read"), any(AnalyzeDocumentOptions.class)))
        .thenReturn(syncPoller);
    when(syncPoller.getFinalResult()).thenThrow(new RuntimeException(errorMessage));

    AzureProvider azureProvider = createAzureProvider();
    ExtractionRequestData requestData = ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA;

    // When & Then
    RuntimeException exception =
        assertThrows(RuntimeException.class, () -> azureCaller.call(requestData, azureProvider));

    assertEquals("Failed to extract text from document: " + errorMessage, exception.getMessage());
    assertEquals(errorMessage, exception.getCause().getMessage());
  }

  @Test
  void call_NullPages() {
    // Given
    when(analyzeResult.getPages()).thenReturn(null);
    when(syncPoller.getFinalResult()).thenReturn(analyzeResult);
    when(documentIntelligenceClient.beginAnalyzeDocument(
            eq("prebuilt-read"), any(AnalyzeDocumentOptions.class)))
        .thenReturn(syncPoller);

    AzureProvider azureProvider = createAzureProvider();
    ExtractionRequestData requestData = ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA;

    // When
    String result = azureCaller.call(requestData, azureProvider);

    // Then
    assertEquals("", result);
  }

  private AzureProvider createAzureProvider() {
    DocumentIntelligenceConfiguration config = new DocumentIntelligenceConfiguration();
    config.setEndpoint("https://test-endpoint.cognitiveservices.azure.com/");
    config.setApiKey("test-api-key");

    AzureProvider provider = new AzureProvider();
    provider.setDocumentIntelligenceConfiguration(config);

    return provider;
  }
}
