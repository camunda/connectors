/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gemini.caller;

import static io.camunda.connector.gemini.TestUtil.readValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Candidate;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import io.camunda.connector.gemini.mapper.GenerativeModelMapper;
import io.camunda.connector.gemini.mapper.PromptsMapper;
import io.camunda.connector.gemini.model.GeminiRequest;
import io.camunda.connector.gemini.model.GeminiRequestData;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GeminiCallerTest {

  @Mock private GenerativeModelMapper generativeModelMapper;

  private GeminiCaller caller;

  @BeforeEach
  void setUp() {
    var ObjectMapper = ConnectorsObjectMapperSupplier.getCopy();
    var promptsMapper = new PromptsMapper(ObjectMapper);
    caller = new GeminiCaller(generativeModelMapper, promptsMapper);
  }

  @Test
  void generateContent() throws Exception {
    var generativeModel = mock(GenerativeModel.class);

    when(generativeModel.generateContent(any(Content.class)))
        .thenReturn(
            GenerateContentResponse.newBuilder()
                .addCandidates(
                    Candidate.newBuilder()
                        .setContent(
                            Content.newBuilder()
                                .addParts(Part.newBuilder().setText("just a text").build())
                                .build())
                        .build())
                .build());

    when(generativeModelMapper.map(any(GeminiRequestData.class), any(VertexAI.class)))
        .thenReturn(generativeModel);

    var geminiRequest =
        readValue("src/test/resources/only_required_fields_model.json", GeminiRequest.class);
    caller.generateContent(geminiRequest);

    verify(generativeModel, times(1)).generateContent(any(Content.class));
  }
}
