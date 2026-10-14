/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.agenticai.aiagent.tool.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RuntimeToolResultTest {

  @Test
  void anErrorResultCarriesNoContextUpdate() {
    final var result =
        RuntimeToolResult.error("no").withContextUpdate(agentContext -> agentContext);

    assertThat(result.error()).isTrue();
    assertThat(result.content()).isEqualTo("no");
    assertThat(result.contextUpdates()).isEmpty();
  }

  @Test
  void anErrorResultCarriesNoProperty() {
    final var result = RuntimeToolResult.error("no").withProperty("key", "value");

    assertThat(result.properties()).isEmpty();
  }
}
