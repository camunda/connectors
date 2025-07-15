/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.util;

import static io.camunda.connector.agenticai.util.PromptUtils.resolveParameterizedPrompt;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PromptUtilsTest {

  @Nested
  public class ResolveParameterizedPromptTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void supportsMissingTemplate(String template) {
      assertThat(resolveParameterizedPrompt(template, Map.of("name", "Johnny")))
          .isEqualTo(template);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void supportsMissingParameters(Map<String, Object> parameters) {
      assertThat(resolveParameterizedPrompt("Tell me a story", parameters))
          .isEqualTo("Tell me a story");
    }

    @Test
    void replacesPromptWithParameters() {
      assertThat(
              resolveParameterizedPrompt(
                  "Tell me a story about {{name}} and {{dummy}}",
                  Map.of("name", "Johnny", "dummy", new DummyClass("hello"), "unused", "foo")))
          .isEqualTo("Tell me a story about Johnny and DummyClass<<hello>>");
    }

    @Test
    void missingParameterResultsInValueNotBeingReplaced() {
      assertThat(resolveParameterizedPrompt("Tell me a story about {{name}}", Map.of()))
          .isEqualTo("Tell me a story about {{name}}");
    }

    @Test
    void nullParameterValueIsReplacedWithEmptyString() {
      final var parameters = new HashMap<String, Object>();
      parameters.put("name", null);

      assertThat(resolveParameterizedPrompt("Tell me a story about {{name}}", parameters))
          .isEqualTo("Tell me a story about ");
    }
  }

  private static class DummyClass {
    private final String name;

    public DummyClass(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return "DummyClass<<%s>>".formatted(name);
    }
  }
}
