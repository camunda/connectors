/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.gemini.mapper;

import static io.camunda.connector.gemini.TestUtil.readValue;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.cloud.vertexai.api.FunctionDeclaration;
import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Type;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FunctionDeclarationMapperTest {

  private FunctionDeclarationMapper functionDeclarationMapper;

  @BeforeEach
  void setUp() {
    var objectMapper = ConnectorsObjectMapperSupplier.getCopy();
    functionDeclarationMapper = new FunctionDeclarationMapper(objectMapper);
  }

  @Test
  void mapWithCorrectInput() throws Exception {
    List<Object> input = readValue("src/test/resources/correct_function_call.json", List.class);

    List<FunctionDeclaration> expectedDeclarations =
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

    List<FunctionDeclaration> resultDeclarations = functionDeclarationMapper.map(input);

    assertThat(resultDeclarations).isEqualTo(expectedDeclarations);
  }

  @Test
  void mapWithIncorrectInput() throws Exception {
    List<Object> input = readValue("src/test/resources/incorrect_function_call.json", List.class);

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> functionDeclarationMapper.map(input));
    assertThat(ex.getMessage()).isEqualTo(FunctionDeclarationMapper.DESERIALIZATION_EX_MSG);
  }
}
