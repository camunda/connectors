/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Import(ValidationAutoConfiguration.class)
class McpClientStandaloneFiltersConfigurationValidationTest {

  @Autowired private Validator validator;

  @Test
  void validationSucceedsForEmptyConfiguration() {
    final var config = new McpClientStandaloneFiltersConfiguration(null, null, null);

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void validationSucceedsForValidToolsConfiguration() {
    final var toolsConfig =
        new McpClientToolsFilterConfiguration(List.of("tool1", "tool2"), List.of("tool3"));
    final var config = new McpClientStandaloneFiltersConfiguration(toolsConfig, null, null);

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void validationSucceedsForValidResourcesConfiguration() {
    final var resourcesConfig =
        new McpClientResourcesFilterConfiguration(
            List.of("resource1", "resource2"), List.of("resource3"));
    final var config = new McpClientStandaloneFiltersConfiguration(null, resourcesConfig, null);

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void validationSucceedsForValidPromptsConfiguration() {
    final var promptsConfig =
        new McpClientPromptsFilterConfiguration(List.of("prompt1", "prompt2"), List.of("prompt3"));
    final var config = new McpClientStandaloneFiltersConfiguration(null, null, promptsConfig);

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void validationSucceedsForFullyPopulatedConfiguration() {
    final var toolsConfig =
        new McpClientToolsFilterConfiguration(List.of("tool1"), List.of("tool2"));
    final var resourcesConfig =
        new McpClientResourcesFilterConfiguration(List.of("resource1"), List.of("resource2"));
    final var promptsConfig =
        new McpClientPromptsFilterConfiguration(List.of("prompt1"), List.of("prompt2"));
    final var config =
        new McpClientStandaloneFiltersConfiguration(toolsConfig, resourcesConfig, promptsConfig);

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void validationSucceeds_whenIncludedAndExcludedListsAreEmpty() {
    final var toolsConfig = new McpClientToolsFilterConfiguration(List.of(), List.of());
    final var resourcesConfig = new McpClientResourcesFilterConfiguration(List.of(), List.of());
    final var promptsConfig = new McpClientPromptsFilterConfiguration(List.of(), List.of());
    final var config =
        new McpClientStandaloneFiltersConfiguration(toolsConfig, resourcesConfig, promptsConfig);

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void validationSucceeds_whenOnlyIncludedListIsProvided() {
    final var toolsConfig = new McpClientToolsFilterConfiguration(List.of("tool1"), null);
    final var config = new McpClientStandaloneFiltersConfiguration(toolsConfig, null, null);

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void validationSucceeds_whenOnlyExcludedListIsProvided() {
    final var toolsConfig = new McpClientToolsFilterConfiguration(null, List.of("tool1"));
    final var config = new McpClientStandaloneFiltersConfiguration(toolsConfig, null, null);

    assertThat(validator.validate(config)).isEmpty();
  }

  @Nested
  class ToolsFilterValidation {

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "  "})
    void validationFails_whenIncludedToolNameIsBlank(String toolName) {
      final var toolsConfig = new McpClientToolsFilterConfiguration(List.of(toolName), null);
      final var config = new McpClientStandaloneFiltersConfiguration(toolsConfig, null, null);

      assertThat(validator.validate(config))
          .isNotEmpty()
          .extracting(ConstraintViolation::getMessage)
          .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "  "})
    void validationFails_whenExcludedToolNameIsBlank(String toolName) {
      final var toolsConfig = new McpClientToolsFilterConfiguration(null, List.of(toolName));
      final var config = new McpClientStandaloneFiltersConfiguration(toolsConfig, null, null);

      assertThat(validator.validate(config))
          .isNotEmpty()
          .extracting(ConstraintViolation::getMessage)
          .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
    }

    @Test
    void validationFails_whenIncludedListContainsBlankToolAmongValidOnes() {
      final var toolsConfig =
          new McpClientToolsFilterConfiguration(List.of("validTool", " "), null);
      final var config = new McpClientStandaloneFiltersConfiguration(toolsConfig, null, null);

      assertThat(validator.validate(config))
          .isNotEmpty()
          .extracting(ConstraintViolation::getMessage)
          .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
    }

    @Test
    void validationFails_whenExcludedListContainsBlankToolAmongValidOnes() {
      final var toolsConfig = new McpClientToolsFilterConfiguration(null, List.of("validTool", ""));
      final var config = new McpClientStandaloneFiltersConfiguration(toolsConfig, null, null);

      assertThat(validator.validate(config))
          .isNotEmpty()
          .extracting(ConstraintViolation::getMessage)
          .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
    }
  }

  @Nested
  class ResourcesFilterValidation {

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "  "})
    void validationFails_whenIncludedResourceNameIsBlank(String resourceName) {
      final var resourcesConfig =
          new McpClientResourcesFilterConfiguration(List.of(resourceName), null);
      final var config = new McpClientStandaloneFiltersConfiguration(null, resourcesConfig, null);

      assertThat(validator.validate(config))
          .isNotEmpty()
          .extracting(ConstraintViolation::getMessage)
          .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "  "})
    void validationFails_whenExcludedResourceNameIsBlank(String resourceName) {
      final var resourcesConfig =
          new McpClientResourcesFilterConfiguration(null, List.of(resourceName));
      final var config = new McpClientStandaloneFiltersConfiguration(null, resourcesConfig, null);

      assertThat(validator.validate(config))
          .isNotEmpty()
          .extracting(ConstraintViolation::getMessage)
          .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
    }

    @Test
    void validationFails_whenIncludedListContainsBlankResourceAmongValidOnes() {
      final var resourcesConfig =
          new McpClientResourcesFilterConfiguration(List.of("validResource", " "), null);
      final var config = new McpClientStandaloneFiltersConfiguration(null, resourcesConfig, null);

      assertThat(validator.validate(config))
          .isNotEmpty()
          .extracting(ConstraintViolation::getMessage)
          .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
    }

    @Test
    void validationFails_whenExcludedListContainsBlankResourceAmongValidOnes() {
      final var resourcesConfig =
          new McpClientResourcesFilterConfiguration(null, List.of("validResource", ""));
      final var config = new McpClientStandaloneFiltersConfiguration(null, resourcesConfig, null);

      assertThat(validator.validate(config))
          .isNotEmpty()
          .extracting(ConstraintViolation::getMessage)
          .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
    }
  }

  @Nested
  class PromptsFilterValidation {

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "  "})
    void validationFails_whenIncludedPromptNameIsBlank(String promptName) {
      final var promptsConfig = new McpClientPromptsFilterConfiguration(List.of(promptName), null);
      final var config = new McpClientStandaloneFiltersConfiguration(null, null, promptsConfig);

      assertThat(validator.validate(config))
          .isNotEmpty()
          .extracting(ConstraintViolation::getMessage)
          .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "  "})
    void validationFails_whenExcludedPromptNameIsBlank(String promptName) {
      final var promptsConfig = new McpClientPromptsFilterConfiguration(null, List.of(promptName));
      final var config = new McpClientStandaloneFiltersConfiguration(null, null, promptsConfig);

      assertThat(validator.validate(config))
          .isNotEmpty()
          .extracting(ConstraintViolation::getMessage)
          .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
    }

    @Test
    void validationFails_whenIncludedListContainsBlankPromptAmongValidOnes() {
      final var promptsConfig =
          new McpClientPromptsFilterConfiguration(List.of("validPrompt", " "), null);
      final var config = new McpClientStandaloneFiltersConfiguration(null, null, promptsConfig);

      assertThat(validator.validate(config))
          .isNotEmpty()
          .extracting(ConstraintViolation::getMessage)
          .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
    }

    @Test
    void validationFails_whenExcludedListContainsBlankPromptAmongValidOnes() {
      final var promptsConfig =
          new McpClientPromptsFilterConfiguration(null, List.of("validPrompt", ""));
      final var config = new McpClientStandaloneFiltersConfiguration(null, null, promptsConfig);

      assertThat(validator.validate(config))
          .isNotEmpty()
          .extracting(ConstraintViolation::getMessage)
          .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
    }
  }
}
