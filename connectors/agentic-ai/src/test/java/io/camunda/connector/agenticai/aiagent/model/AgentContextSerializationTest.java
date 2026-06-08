/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.util.TestObjectMapperSupplier;
import org.junit.jupiter.api.Test;

class AgentContextSerializationTest {

  private final ObjectMapper objectMapper = TestObjectMapperSupplier.getInstance();

  @Test
  void roundtrip_withAgentInstanceKey() throws Exception {
    var ctx = AgentContext.builder().agentInstanceKey("inst-42").build();
    var json = objectMapper.writeValueAsString(ctx);
    var restored = objectMapper.readValue(json, AgentContext.class);
    assertThat(restored.agentInstanceKey()).isEqualTo("inst-42");
  }

  @Test
  void agentInstanceKey_omittedFromJsonWhenNull() throws Exception {
    var ctx = AgentContext.builder().build();
    var json = objectMapper.writeValueAsString(ctx);
    assertThat(json).doesNotContain("agentInstanceKey");
  }

  @Test
  void deserialize_withoutAgentInstanceKey_producesNull() throws Exception {
    var json = "{\"state\":\"READY\",\"metrics\":{\"modelCalls\":0,\"toolCalls\":0}}";
    var ctx = objectMapper.readValue(json, AgentContext.class);
    assertThat(ctx.agentInstanceKey()).isNull();
  }
}
