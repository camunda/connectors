/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gemini.mapper;

import static com.google.cloud.vertexai.api.SafetySetting.HarmBlockThreshold.*;
import static io.camunda.connector.gemini.TestUtil.readValue;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.vertexai.api.*;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.gemini.model.GeminiRequest;
import io.camunda.connector.gemini.supplier.VertexAISupplier;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GenerativeModelMapperTest {

  private GenerativeModelMapper generativeModelMapper;

  @BeforeEach()
  void setUp() {
    var objectMapper = ConnectorsObjectMapperSupplier.getCopy();
    var functionDeclarationMapper = new FunctionDeclarationMapper(objectMapper);
    generativeModelMapper = new GenerativeModelMapper(functionDeclarationMapper);
  }

  @Test
  void mapWithAllVariables() throws Exception {
    var geminiRequest =
        readValue("src/test/resources/fully_filled_model.json", GeminiRequest.class);
    var requestData = geminiRequest.getInput();

    var generativeModel =
        generativeModelMapper.map(requestData, VertexAISupplier.getVertexAI(geminiRequest));

    assertThat(generativeModel.getModelName()).isEqualTo(requestData.model().getVersion());
    assertThat(generativeModel.getGenerationConfig()).isEqualTo(prepareGenConfForAllFields());
    assertThat(generativeModel.getSafetySettings())
        .containsExactlyInAnyOrderElementsOf(
            prepareSafetySettings(
                OFF, BLOCK_ONLY_HIGH, BLOCK_MEDIUM_AND_ABOVE, BLOCK_LOW_AND_ABOVE));
    assertThat(generativeModel.getTools())
        .containsExactlyInAnyOrderElementsOf(List.of(prepareFunctions(), prepareGrounding()));
  }

  @Test
  void mapOnlyWithRequiredVariables() throws Exception {
    var geminiRequest =
        readValue("src/test/resources/only_required_fields_model.json", GeminiRequest.class);
    var requestData = geminiRequest.getInput();

    var generativeModel =
        generativeModelMapper.map(requestData, VertexAISupplier.getVertexAI(geminiRequest));

    assertThat(generativeModel.getModelName()).isEqualTo(requestData.model().getVersion());
    assertThat(generativeModel.getGenerationConfig())
        .isEqualTo(prepareGenConfigForRequiredFields());
    assertThat(generativeModel.getSafetySettings())
        .containsExactlyInAnyOrderElementsOf(prepareSafetySettings(OFF, OFF, OFF, OFF));
    assertThat(generativeModel.getTools()).isEmpty();
  }

  private GenerationConfig prepareGenConfForAllFields() {
    return GenerationConfig.newBuilder()
        .setMaxOutputTokens(600)
        .setTemperature(2.0f)
        .setTopP(0.9f)
        .setTopK(2)
        .setSeed(1)
        .addAllStopSequences(List.of("text1", "text2"))
        .build();
  }

  private GenerationConfig prepareGenConfigForRequiredFields() {
    return GenerationConfig.newBuilder()
        .setMaxOutputTokens(600)
        .setTemperature(0)
        .setTopP(0)
        .setSeed(0)
        .build();
  }

  private List<SafetySetting> prepareSafetySettings(
      SafetySetting.HarmBlockThreshold hate,
      SafetySetting.HarmBlockThreshold dangerous,
      SafetySetting.HarmBlockThreshold sexually,
      SafetySetting.HarmBlockThreshold harassment) {
    return List.of(
        SafetySetting.newBuilder()
            .setCategory(HarmCategory.HARM_CATEGORY_HATE_SPEECH)
            .setThreshold(hate)
            .build(),
        SafetySetting.newBuilder()
            .setCategory(HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT)
            .setThreshold(dangerous)
            .build(),
        SafetySetting.newBuilder()
            .setCategory(HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT)
            .setThreshold(sexually)
            .build(),
        SafetySetting.newBuilder()
            .setCategory(HarmCategory.HARM_CATEGORY_HARASSMENT)
            .setThreshold(harassment)
            .build());
  }

  private Tool prepareFunctions() {
    List<FunctionDeclaration> functions =
        List.of(
            FunctionDeclaration.newBuilder()
                .setName("get_exchange_rate")
                .setDescription("Get the exchange rate for currencies between countries")
                .setParameters(
                    Schema.newBuilder()
                        .setType(Type.OBJECT)
                        .putProperties(
                            "currency_date",
                            Schema.newBuilder()
                                .setType(Type.STRING)
                                .setDescription(
                                    "A date that must always be in YYYY-MM-DD format or the value 'latest' if a time period is not specified")
                                .build())
                        .putProperties(
                            "currency_from",
                            Schema.newBuilder()
                                .setType(Type.STRING)
                                .setDescription("The currency to convert from in ISO 4217 format")
                                .build())
                        .putProperties(
                            "currency_to",
                            Schema.newBuilder()
                                .setType(Type.STRING)
                                .setDescription("The currency to convert to in ISO 4217 format")
                                .build())
                        .build())
                .build());
    return Tool.newBuilder().addAllFunctionDeclarations(functions).build();
  }

  private Tool prepareGrounding() {
    return Tool.newBuilder()
        .setRetrieval(
            Retrieval.newBuilder()
                .setVertexAiSearch(
                    VertexAISearch.newBuilder()
                        .setDatastore(
                            "projects/silicon-bolt-438910-q6/locations/global/collections/default_collection/dataStores/mma-rules_1730735591574")))
        .build();
  }
}
