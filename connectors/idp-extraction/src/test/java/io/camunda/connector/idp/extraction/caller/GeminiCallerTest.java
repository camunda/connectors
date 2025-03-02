/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.caller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import io.camunda.connector.idp.extraction.model.ExtractionRequestData;
import io.camunda.connector.idp.extraction.model.GeminiBaseRequest;
import io.camunda.connector.idp.extraction.model.GeminiRequestConfiguration;
import io.camunda.connector.idp.extraction.util.ExtractionTestUtils;
import io.camunda.connector.idp.extraction.utils.GcsUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GeminiCallerTest {

  @Test
  void executeSuccessfulExtraction() throws Exception {
    // Expected response
    String expectedResponse =
        """
        {
          "name": "John Smith",
          "age": 32
        }
        """;

    class TestGeminiCaller extends GeminiCaller {
      private final GenerativeModel mockModel;

      public TestGeminiCaller(GenerativeModel mockModel) {
        this.mockModel = mockModel;
      }

      @Override
      public String generateContent(ExtractionRequestData input, GeminiBaseRequest baseRequest)
          throws Exception {
        GenerateContentResponse response =
            mockModel.generateContent(
                Content.newBuilder()
                    .addParts(Part.newBuilder().setText("just a text").build())
                    .build());

        return ResponseHandler.getText(response);
      }
    }

    // Mock the GCS upload utility
    String mockedFileUri = "gs://bucket-name/file-name";
    try (MockedStatic<GcsUtil> gcsUtilMockedStatic = mockStatic(GcsUtil.class)) {
      gcsUtilMockedStatic
          .when(() -> GcsUtil.uploadNewFileFromDocument(any(), anyString(), anyString(), any()))
          .thenReturn(mockedFileUri);

      GenerativeModel mockGenerativeModel = mock(GenerativeModel.class);

      GenerateContentResponse mockResponse = mock(GenerateContentResponse.class);
      when(mockGenerativeModel.generateContent(any(Content.class))).thenReturn(mockResponse);

      try (MockedStatic<ResponseHandler> responseHandlerMockedStatic =
          mockStatic(ResponseHandler.class)) {
        responseHandlerMockedStatic
            .when(() -> ResponseHandler.getText(any()))
            .thenReturn(expectedResponse);

        GeminiRequestConfiguration configuration =
            new GeminiRequestConfiguration("region", "project-id", "bucket-name", null, null);

        GeminiBaseRequest baseRequest = new GeminiBaseRequest();
        baseRequest.setConfiguration(configuration);

        TestGeminiCaller testCaller = new TestGeminiCaller(mockGenerativeModel);

        String result =
            testCaller.generateContent(
                ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA, baseRequest);

        assertEquals(expectedResponse, result);
      }
    }
  }
}
