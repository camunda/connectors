/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeToolResultTest {

  @Test
  void aTextBecomesTheContent() {
    final var result = RuntimeToolResult.success("ok");

    assertThat(result).isInstanceOf(RuntimeToolResult.Success.class);
    assertThat(result.content()).containsExactly(textContent("ok"));
  }

  @Test
  void aBlankTextBecomesNoContent() {
    assertThat(RuntimeToolResult.success(" ").content()).isEmpty();
  }

  @Test
  void theGivenBlocksBecomeTheContentInOrder() {
    final var result = RuntimeToolResult.success(List.of(textContent("a"), textContent("b")));

    assertThat(result).isInstanceOf(RuntimeToolResult.Success.class);
    assertThat(result.content()).containsExactly(textContent("a"), textContent("b"));
  }

  @Test
  void anErrorResultCarriesItsMessageAsContent() {
    final var result = RuntimeToolResult.error("no");

    assertThat(result).isEqualTo(new RuntimeToolResult.Error("no"));
    assertThat(result.content()).containsExactly(textContent("no"));
  }

  @Test
  void aBlankErrorMessageGivesNoContent() {
    assertThat(RuntimeToolResult.error(" ").content()).isEmpty();
  }

  @Test
  void nullsAreRejected() {
    assertThatThrownBy(() -> RuntimeToolResult.success((String) null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> RuntimeToolResult.success((List<Content>) null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> RuntimeToolResult.error(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void theContentIsACopyThatCannotBeChanged() {
    final var blocks = new ArrayList<Content>(List.of(textContent("a")));
    final var result = RuntimeToolResult.success(blocks);

    blocks.add(textContent("b"));

    assertThat(result.content()).containsExactly(textContent("a"));
    assertThatThrownBy(() -> result.content().add(textContent("c")))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
