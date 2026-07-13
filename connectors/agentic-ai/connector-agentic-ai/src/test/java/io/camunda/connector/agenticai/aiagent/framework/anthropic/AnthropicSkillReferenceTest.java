/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.anthropic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.models.beta.messages.BetaSkillParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnthropicSkillReferenceTest {

  @Test
  void parsesSingleTokenAsAnthropicSkillWithLatestVersion() {
    final var reference = AnthropicSkillReference.parse("pptx");

    assertThat(reference.type()).isEqualTo(BetaSkillParams.Type.ANTHROPIC);
    assertThat(reference.skillId()).isEqualTo("pptx");
    assertThat(reference.version()).isEqualTo("latest");
  }

  @Test
  void parsesTwoTokensAsAnthropicSkillWithExplicitVersionWhenFirstTokenIsNotAType() {
    final var reference = AnthropicSkillReference.parse("pptx:special-version");

    assertThat(reference.type()).isEqualTo(BetaSkillParams.Type.ANTHROPIC);
    assertThat(reference.skillId()).isEqualTo("pptx");
    assertThat(reference.version()).isEqualTo("special-version");
  }

  @Test
  void parsesTwoTokensAsExplicitTypeWithLatestVersionWhenFirstTokenIsCustom() {
    final var reference = AnthropicSkillReference.parse("custom:my-skill");

    assertThat(reference.type()).isEqualTo(BetaSkillParams.Type.CUSTOM);
    assertThat(reference.skillId()).isEqualTo("my-skill");
    assertThat(reference.version()).isEqualTo("latest");
  }

  @Test
  void parsesTwoTokensAsExplicitTypeWithLatestVersionWhenFirstTokenIsAnthropic() {
    final var reference = AnthropicSkillReference.parse("anthropic:pptx");

    assertThat(reference.type()).isEqualTo(BetaSkillParams.Type.ANTHROPIC);
    assertThat(reference.skillId()).isEqualTo("pptx");
    assertThat(reference.version()).isEqualTo("latest");
  }

  @Test
  void twoTokenTypeDisambiguationIsCaseInsensitive() {
    final var reference = AnthropicSkillReference.parse("CUSTOM:my-skill");

    assertThat(reference.type()).isEqualTo(BetaSkillParams.Type.CUSTOM);
    assertThat(reference.skillId()).isEqualTo("my-skill");
    assertThat(reference.version()).isEqualTo("latest");
  }

  @Test
  void parsesThreeTokensAsExplicitTypeSkillAndVersion() {
    final var reference = AnthropicSkillReference.parse("custom:my-skill:v");

    assertThat(reference.type()).isEqualTo(BetaSkillParams.Type.CUSTOM);
    assertThat(reference.skillId()).isEqualTo("my-skill");
    assertThat(reference.version()).isEqualTo("v");
  }

  @Test
  void parsesThreeTokensWithExplicitAnthropicType() {
    final var reference = AnthropicSkillReference.parse("anthropic:pptx:v3");

    assertThat(reference.type()).isEqualTo(BetaSkillParams.Type.ANTHROPIC);
    assertThat(reference.skillId()).isEqualTo("pptx");
    assertThat(reference.version()).isEqualTo("v3");
  }

  @Test
  void trimsWhitespaceAroundTokens() {
    final var reference = AnthropicSkillReference.parse(" custom : my-skill : v ");

    assertThat(reference.type()).isEqualTo(BetaSkillParams.Type.CUSTOM);
    assertThat(reference.skillId()).isEqualTo("my-skill");
    assertThat(reference.version()).isEqualTo("v");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void failsLoudlyOnBlankRawString(String raw) {
    assertThatThrownBy(() -> AnthropicSkillReference.parse(raw))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
  }

  @ParameterizedTest
  @ValueSource(strings = {"pptx:", ":pptx", "custom::v", "pptx: :v"})
  void failsLoudlyOnBlankSegments(String raw) {
    assertThatThrownBy(() -> AnthropicSkillReference.parse(raw))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(raw);
  }

  @Test
  void failsLoudlyOnMoreThanThreeSegments() {
    assertThatThrownBy(() -> AnthropicSkillReference.parse("a:b:c:d"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a:b:c:d");
  }

  @Test
  void failsLoudlyOnUnknownExplicitTypeInThreeTokenForm() {
    assertThatThrownBy(() -> AnthropicSkillReference.parse("bogus:my-skill:v"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bogus")
        .hasMessageContaining("bogus:my-skill:v");
  }
}
