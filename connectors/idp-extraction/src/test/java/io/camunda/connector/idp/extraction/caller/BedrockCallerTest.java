/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.idp.extraction.model.ConverseData;
import io.camunda.connector.idp.extraction.model.ExtractionRequest;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.providers.AwsProvider;
import io.camunda.connector.idp.extraction.util.ExtractionTestUtils;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;

@ExtendWith(MockitoExtension.class)
class BedrockCallerTest {

  BedrockRuntimeClient bedrockRuntimeClient = mock(BedrockRuntimeClient.class);
  ConverseResponse converseResponse = mock(ConverseResponse.class, Mockito.RETURNS_DEEP_STUBS);
  BedrockCaller bedrockCaller = new BedrockCaller();

  @Test
  void executeSuccessfulExtraction() {
    String expectedResponse =
        """
                {
                	"name": "John Smith",
                	"age": 32
                }
                """;

    when(bedrockRuntimeClient.converse(any(Consumer.class))).thenReturn(converseResponse);
    when(converseResponse.output().message().content().getFirst().text())
        .thenReturn(expectedResponse);

    AwsProvider baseRequest = new AwsProvider();
    ExtractionRequest extractionRequest =
        new ExtractionRequest(ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA, baseRequest);

    String bedrockResponse =
        bedrockCaller.call(
            extractionRequest.input(), "us-east-1", "extracted text", bedrockRuntimeClient);

    assertEquals(expectedResponse, bedrockResponse);
  }

  @Test
  void shouldAddCrossRegionPrefixForNovaModel() {
    // Given
    String originalModelId = "amazon.nova-lite-v1:0";
    String awsRegion = "us-east-1";
    String expectedPrefixedModelId = "us.amazon.nova-lite-v1:0";

    ExtractionRequestData requestData = createTestRequestData(originalModelId);

    setupMockResponse();

    // When
    bedrockCaller.call(requestData, awsRegion, "extracted text", bedrockRuntimeClient);

    // Then
    ArgumentCaptor<Consumer<ConverseRequest.Builder>> captor =
        ArgumentCaptor.forClass(Consumer.class);
    verify(bedrockRuntimeClient).converse(captor.capture());

    ConverseRequest.Builder builder = ConverseRequest.builder();
    captor.getValue().accept(builder);

    assertEquals(expectedPrefixedModelId, builder.build().modelId());
  }

  @Test
  void shouldAddCrossRegionPrefixForClaudeModel() {
    // Given
    String originalModelId = "anthropic.claude-3-5-sonnet-20241022-v2:0";
    String awsRegion = "eu-west-1";
    String expectedPrefixedModelId = "eu.anthropic.claude-3-5-sonnet-20241022-v2:0";

    ExtractionRequestData requestData = createTestRequestData(originalModelId);

    setupMockResponse();

    // When
    bedrockCaller.call(requestData, awsRegion, "extracted text", bedrockRuntimeClient);

    // Then
    ArgumentCaptor<Consumer<ConverseRequest.Builder>> captor =
        ArgumentCaptor.forClass(Consumer.class);
    verify(bedrockRuntimeClient).converse(captor.capture());

    ConverseRequest.Builder builder = ConverseRequest.builder();
    captor.getValue().accept(builder);

    assertEquals(expectedPrefixedModelId, builder.build().modelId());
  }

  @Test
  void shouldNotModifyRegularModels() {
    // Given
    String originalModelId = "anthropic.claude-3-sonnet-20240229-v1:0";
    String awsRegion = "us-east-1";

    ExtractionRequestData requestData = createTestRequestData(originalModelId);

    setupMockResponse();

    // When
    bedrockCaller.call(requestData, awsRegion, "extracted text", bedrockRuntimeClient);

    // Then
    ArgumentCaptor<Consumer<ConverseRequest.Builder>> captor =
        ArgumentCaptor.forClass(Consumer.class);
    verify(bedrockRuntimeClient).converse(captor.capture());

    ConverseRequest.Builder builder = ConverseRequest.builder();
    captor.getValue().accept(builder);

    assertEquals(originalModelId, builder.build().modelId());
  }

  @Test
  void shouldNotModifyAlreadyPrefixedModels() {
    // Given
    String prefixedModelId = "us.amazon.nova-lite-v1:0";
    String awsRegion = "eu-west-1"; // Different region, but model already has prefix

    ExtractionRequestData requestData = createTestRequestData(prefixedModelId);

    setupMockResponse();

    // When
    bedrockCaller.call(requestData, awsRegion, "extracted text", bedrockRuntimeClient);

    // Then
    ArgumentCaptor<Consumer<ConverseRequest.Builder>> captor =
        ArgumentCaptor.forClass(Consumer.class);
    verify(bedrockRuntimeClient).converse(captor.capture());

    ConverseRequest.Builder builder = ConverseRequest.builder();
    captor.getValue().accept(builder);

    assertEquals(prefixedModelId, builder.build().modelId());
  }

  @Test
  void shouldHandleUnsupportedRegion() {
    // Given
    String originalModelId = "amazon.nova-lite-v1:0";
    String unsupportedRegion = "af-south-1"; // Region not in our mapping

    ExtractionRequestData requestData = createTestRequestData(originalModelId);

    setupMockResponse();

    // When
    bedrockCaller.call(requestData, unsupportedRegion, "extracted text", bedrockRuntimeClient);

    // Then
    ArgumentCaptor<Consumer<ConverseRequest.Builder>> captor =
        ArgumentCaptor.forClass(Consumer.class);
    verify(bedrockRuntimeClient).converse(captor.capture());

    ConverseRequest.Builder builder = ConverseRequest.builder();
    captor.getValue().accept(builder);

    // Should return original model ID since region doesn't support cross-region inference
    assertEquals(originalModelId, builder.build().modelId());
  }

  @Test
  void shouldHandleApacRegion() {
    // Given
    String originalModelId = "amazon.nova-lite-v1:0";
    String awsRegion = "ap-southeast-2";
    String expectedPrefixedModelId = "apac.amazon.nova-lite-v1:0";

    ExtractionRequestData requestData = createTestRequestData(originalModelId);

    setupMockResponse();

    // When
    bedrockCaller.call(requestData, awsRegion, "extracted text", bedrockRuntimeClient);

    // Then
    ArgumentCaptor<Consumer<ConverseRequest.Builder>> captor =
        ArgumentCaptor.forClass(Consumer.class);
    verify(bedrockRuntimeClient).converse(captor.capture());

    ConverseRequest.Builder builder = ConverseRequest.builder();
    captor.getValue().accept(builder);

    assertEquals(expectedPrefixedModelId, builder.build().modelId());
  }

  @Test
  void shouldHandleGovCloudRegion() {
    // Given
    String originalModelId =
        "anthropic.claude-3-haiku-20240307-v1:0"; // This would be cross-region if it was in the
    // list
    String awsRegion = "us-gov-west-1";

    ExtractionRequestData requestData = createTestRequestData(originalModelId);

    setupMockResponse();

    // When
    bedrockCaller.call(requestData, awsRegion, "extracted text", bedrockRuntimeClient);

    // Then
    ArgumentCaptor<Consumer<ConverseRequest.Builder>> captor =
        ArgumentCaptor.forClass(Consumer.class);
    verify(bedrockRuntimeClient).converse(captor.capture());

    ConverseRequest.Builder builder = ConverseRequest.builder();
    captor.getValue().accept(builder);

    // This model is not in our cross-region list, so should remain unchanged
    assertEquals(originalModelId, builder.build().modelId());
  }

  private ExtractionRequestData createTestRequestData(String modelId) {
    ConverseData converseData = new ConverseData(modelId, 512, 0.5f, 0.9f);
    return new ExtractionRequestData(
        ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.document(),
        ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.extractionType(),
        ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.taxonomyItems(),
        ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.includedFields(),
        ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.renameMappings(),
        ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA.delimiter(),
        converseData);
  }

  private void setupMockResponse() {
    when(bedrockRuntimeClient.converse(any(Consumer.class))).thenReturn(converseResponse);
    when(converseResponse.output().message().content().getFirst().text())
        .thenReturn("test response");
  }
}
