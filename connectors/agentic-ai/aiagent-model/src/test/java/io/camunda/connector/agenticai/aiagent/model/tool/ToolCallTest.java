/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolCallTest {

  @Test
  void withResolvedArgumentsReplacesTheArgumentsAndKeepsEverythingElse() {
    final var call =
        new ToolCall(
            "call-1", "upload", Map.of("doc", "tag"), Map.of("k", "v"), ToolKind.RUNTIME, "no");

    final var resolved = call.withResolvedArguments(Map.of("doc", "reference"));

    assertThat(resolved)
        .isEqualTo(
            new ToolCall(
                "call-1",
                "upload",
                Map.of("doc", "reference"),
                Map.of("k", "v"),
                ToolKind.RUNTIME,
                "no"));
  }

  @Test
  void withResolvedArgumentsKeepsAnExplicitNullValue() {
    final var arguments = new HashMap<String, Object>();
    arguments.put("note", null);

    final var resolved =
        new ToolCall("call-1", "upload", Map.of()).withResolvedArguments(arguments);

    assertThat(resolved.arguments()).isEqualTo(Collections.singletonMap("note", null));
  }
}
