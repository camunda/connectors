/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gemini;

import static io.camunda.connector.gemini.TestUtil.readValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Candidate;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.gemini.caller.GeminiCaller;
import io.camunda.connector.gemini.mapper.GenerativeModelMapper;
import io.camunda.connector.gemini.mapper.PromptsMapper;
import io.camunda.connector.gemini.model.GeminiRequestData;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GeminiConnectorFunctionTest {

  @Mock private GenerativeModelMapper generativeModelMapper;
  private ObjectMapper objectMapper;
  private GeminiCaller caller;

  @BeforeEach
  void setUp() {
    objectMapper = ConnectorsObjectMapperSupplier.getCopy();
    PromptsMapper promptsMapper = new PromptsMapper(objectMapper);
    caller = new GeminiCaller(generativeModelMapper, promptsMapper);
  }

  @Test
  void execute() throws Exception {
    var generativeModel = mock(GenerativeModel.class);

    when(generativeModel.generateContent(any(Content.class))).thenReturn(getResponse());
    when(generativeModelMapper.map(any(GeminiRequestData.class), any(VertexAI.class)))
        .thenReturn(generativeModel);

    OutboundConnectorContext context =
        OutboundConnectorContextBuilder.create()
            .secret("MyToken", "MyRealToken")
            .variables(readValue("src/test/resources/fully_filled_model.json", JsonNode.class))
            .validation(new DefaultValidationProvider())
            .build();

    Object content = new GeminiConnectorFunction(caller, objectMapper).execute(context);
    assertThat(content).isInstanceOf(Map.class);
  }

  private GenerateContentResponse getResponse() {
    return GenerateContentResponse.newBuilder()
        .addCandidates(
            Candidate.newBuilder()
                .setContent(
                    Content.newBuilder()
                        .addParts(Part.newBuilder().setText("just a text").build())
                        .build())
                .build())
        .build();
  }
}
