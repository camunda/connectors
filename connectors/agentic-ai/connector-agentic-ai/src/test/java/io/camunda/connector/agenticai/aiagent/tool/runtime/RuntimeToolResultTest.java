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

  @Test
  void anErrorResultCarriesNoDocumentContent() {
    final var result = RuntimeToolResult.error("no").withDocumentContent("doc-1");

    assertThat(result.documentIdsWithContent()).isEmpty();
  }

  @Test
  void aSuccessfulResultKeepsTheDocumentsOfContentInTheOrderTheyWereNamedWithoutDuplicates() {
    final var result =
        RuntimeToolResult.success("ok")
            .withDocumentContent("doc-2")
            .withDocumentContent("doc-1")
            .withDocumentContent("doc-2");

    assertThat(result.documentIdsWithContent()).containsExactly("doc-2", "doc-1");
  }

  @Test
  void otherWithersKeepTheDocumentsOfContent() {
    final var result =
        RuntimeToolResult.success("ok")
            .withDocumentContent("doc-1")
            .withProperty("key", "value")
            .withContextUpdate(agentContext -> agentContext);

    assertThat(result.documentIdsWithContent()).containsExactly("doc-1");
  }
}
