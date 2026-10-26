/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.DefaultAwsResponseMetadata;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeResponseMetadata;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningTextBlock;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

@ExtendWith(MockitoExtension.class)
class BedrockCallerTest {

  BedrockRuntimeClient bedrockRuntimeClient = mock(BedrockRuntimeClient.class);
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

    when(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
        .thenReturn(converseResponse(ContentBlock.fromText(expectedResponse)));

    AwsProvider baseRequest = new AwsProvider();
    ExtractionRequest extractionRequest =
        new ExtractionRequest(ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA, baseRequest);

    String bedrockResponse =
        bedrockCaller.call(
            extractionRequest.input(), "us-east-1", "extracted text", bedrockRuntimeClient);

    assertEquals(expectedResponse, bedrockResponse);
  }

  @Test
  void shouldReturnTextWhenFirstContentBlockIsReasoning() {
    String expectedResponse = "{\"name\": \"John Smith\"}";
    ContentBlock reasoningBlock =
        ContentBlock.fromReasoningContent(
            ReasoningContentBlock.fromReasoningText(
                ReasoningTextBlock.builder().text("Let me look at the document...").build()));

    when(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
        .thenReturn(converseResponse(reasoningBlock, ContentBlock.fromText(expectedResponse)));

    String bedrockResponse =
        bedrockCaller.call(
            createTestRequestData("global.anthropic.claude-haiku-5-5"),
            "us-east-1",
            "extracted text",
            bedrockRuntimeClient);

    assertEquals(expectedResponse, bedrockResponse);
  }

  @Test
  void shouldSendSystemPromptAndInferenceConfig() {
    ConverseRequest request = callAndCaptureRequest("anthropic.claude-3-haiku-20240307-v1:0");

    assertThat(request.system()).hasSize(1);
    assertThat(request.messages()).hasSize(1);
    assertThat(request.messages().getFirst().role()).isEqualTo(ConversationRole.USER);
    assertThat(request.inferenceConfig().temperature()).isEqualTo(0.5f);
    assertThat(request.inferenceConfig().topP()).isEqualTo(0.9f);
  }

  @Test
  void shouldMergeSystemPromptIntoUserMessageForTitan() {
    ConverseRequest request = callAndCaptureRequest("amazon.titan-text-express-v1");

    assertThat(request.system()).isEmpty();
    assertThat(request.messages()).hasSize(1);
    assertThat(request.messages().getFirst().content().getFirst().text())
        .contains("Respond in JSON format")
        .contains("extracted text");
  }

  @Test
  void shouldAddCrossRegionPrefixForNovaModel() {
    assertEquals(
        "us.amazon.nova-lite-v1:0",
        callAndCaptureRequest("amazon.nova-lite-v1:0", "us-east-1").modelId());
  }

  @Test
  void shouldAddCrossRegionPrefixForClaudeModel() {
    assertEquals(
        "eu.anthropic.claude-3-5-sonnet-20241022-v2:0",
        callAndCaptureRequest("anthropic.claude-3-5-sonnet-20241022-v2:0", "eu-west-1").modelId());
  }

  @Test
  void shouldNotModifyRegularModels() {
    String originalModelId = "anthropic.claude-3-sonnet-20240229-v1:0";
    assertEquals(originalModelId, callAndCaptureRequest(originalModelId, "us-east-1").modelId());
  }

  @Test
  void shouldNotModifyAlreadyPrefixedModels() {
    // Different region, but model already has prefix
    String prefixedModelId = "us.amazon.nova-lite-v1:0";
    assertEquals(prefixedModelId, callAndCaptureRequest(prefixedModelId, "eu-west-1").modelId());
  }

  @Test
  void shouldHandleUnsupportedRegion() {
    // Should return original model ID since region doesn't support cross-region inference
    String originalModelId = "amazon.nova-lite-v1:0";
    assertEquals(originalModelId, callAndCaptureRequest(originalModelId, "af-south-1").modelId());
  }

  @Test
  void shouldHandleApacRegion() {
    assertEquals(
        "apac.amazon.nova-lite-v1:0",
        callAndCaptureRequest("amazon.nova-lite-v1:0", "ap-southeast-2").modelId());
  }

  @Test
  void shouldHandleGovCloudRegion() {
    // This model is not in our cross-region list, so should remain unchanged
    String originalModelId = "anthropic.claude-3-haiku-20240307-v1:0";
    assertEquals(
        originalModelId, callAndCaptureRequest(originalModelId, "us-gov-west-1").modelId());
  }

  private ConverseRequest callAndCaptureRequest(String modelId) {
    return callAndCaptureRequest(modelId, "us-east-1");
  }

  private ConverseRequest callAndCaptureRequest(String modelId, String awsRegion) {
    when(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
        .thenReturn(converseResponse(ContentBlock.fromText("test response")));

    bedrockCaller.call(
        createTestRequestData(modelId), awsRegion, "extracted text", bedrockRuntimeClient);

    ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
    verify(bedrockRuntimeClient).converse(captor.capture());
    return captor.getValue();
  }

  private ConverseResponse converseResponse(ContentBlock... contentBlocks) {
    return (ConverseResponse)
        ConverseResponse.builder()
            .output(
                ConverseOutput.fromMessage(
                    Message.builder()
                        .role(ConversationRole.ASSISTANT)
                        .content(contentBlocks)
                        .build()))
            .stopReason(StopReason.END_TURN)
            .usage(TokenUsage.builder().inputTokens(10).outputTokens(5).totalTokens(15).build())
            .responseMetadata(
                BedrockRuntimeResponseMetadata.create(
                    DefaultAwsResponseMetadata.create(Map.of("AWS_REQUEST_ID", "test-request-id"))))
            .build();
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
}
