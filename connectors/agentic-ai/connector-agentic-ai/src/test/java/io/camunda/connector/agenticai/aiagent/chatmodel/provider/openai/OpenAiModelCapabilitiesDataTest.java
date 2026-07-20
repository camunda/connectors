/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.request.chatmodel.OpenAiEffort;
import org.junit.jupiter.api.Test;

class OpenAiModelCapabilitiesDataTest {

  private final ObjectMapper mapper =
      new ObjectMapper()
          .findAndRegisterModules(); // snake_case handled by @JsonNaming on the record

  @Test
  void projectsReasoningEffortLevels() throws Exception {
    var yaml =
        """
        { "context_window": 128000, "max_output_tokens": 16384,
          "provider": { "reasoning": { "effort-levels": ["low","medium","high"] } } }
        """;
    var data = mapper.readValue(yaml, OpenAiModelCapabilitiesData.class);
    var caps = data.toModelCapabilities();
    assertThat(caps.supportsReasoning()).isTrue();
    assertThat(caps.reasoning().effortLevels())
        .containsExactly(OpenAiEffort.LOW, OpenAiEffort.MEDIUM, OpenAiEffort.HIGH);
  }

  @Test
  void noReasoningWhenProviderAbsent() throws Exception {
    var data =
        mapper.readValue("{ \"context_window\": 128000 }", OpenAiModelCapabilitiesData.class);
    assertThat(data.toModelCapabilities().supportsReasoning()).isFalse();
  }
}
