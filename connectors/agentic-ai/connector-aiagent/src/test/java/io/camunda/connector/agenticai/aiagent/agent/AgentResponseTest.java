/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.document.ReturnedDocument;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentResponseTest {

  private static final ReturnedDocument RETURNED =
      new ReturnedDocument(Map.of("camunda.document.type", "camunda"), "Report");

  @Test
  void aResponseWithoutResponseDocumentsHasNoneAndTheFieldIsLeftOutOfItsJson() throws Exception {
    final var response = AgentResponse.builder().context(AgentContext.empty()).build();

    assertThat(response.responseDocuments()).isEmpty();
    assertThat(json(response).has("responseDocuments")).isFalse();
  }

  @Test
  void aResponseReadWithoutResponseDocumentsHasNone() throws Exception {
    final var response =
        TestObjectMapperSupplier.INSTANCE.readValue(
            TestObjectMapperSupplier.INSTANCE.writeValueAsString(
                AgentResponse.builder().context(AgentContext.empty()).responseText("hi").build()),
            AgentResponse.class);

    assertThat(response.responseDocuments()).isEmpty();
  }

  @Test
  void aResponseKeepsItsResponseDocumentsInTheJson() throws Exception {
    final var response =
        AgentResponse.builder()
            .context(AgentContext.empty())
            .responseDocuments(Map.of("report", RETURNED))
            .build();

    assertThat(response.responseDocuments()).containsExactly(Map.entry("report", RETURNED));
    assertThat(json(response).at("/responseDocuments/report/description").asText())
        .isEqualTo("Report");
  }

  @Test
  void aSubProcessResponseWithoutResponseDocumentsHasNoneAndTheFieldIsLeftOutOfItsJson()
      throws Exception {
    final var response = AgentSubProcessResponse.builder().responseText("hi").build();

    assertThat(response.responseDocuments()).isEmpty();
    assertThat(json(response).has("responseDocuments")).isFalse();
  }

  private static JsonNode json(Object value) {
    return TestObjectMapperSupplier.INSTANCE.valueToTree(value);
  }
}
