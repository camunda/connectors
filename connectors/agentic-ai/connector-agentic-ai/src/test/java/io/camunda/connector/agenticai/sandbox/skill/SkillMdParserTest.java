/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.sandbox.skill.SkillMdParser.ParsedSkillMd;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkillMdParserTest {

  private SkillMdParser parser;

  @BeforeEach
  void setUp() {
    parser = new SkillMdParser();
  }

  // -------------------------------------------------------------------------
  // Happy path
  // -------------------------------------------------------------------------

  @Test
  void parse_minimalValidFrontmatter_returnsNameAndDescription() {
    String skillMd = "---\nname: my-skill\ndescription: Does something useful.\n---\n";

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.name()).isEqualTo("my-skill");
    assertThat(result.description()).isEqualTo("Does something useful.");
    assertThat(result.body()).isEmpty();
  }

  @Test
  void parse_frontmatterWithBody_returnsBody() {
    String skillMd =
        "---\nname: my-skill\ndescription: Does something useful.\n---\n\nThis is the body.\n";

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.name()).isEqualTo("my-skill");
    assertThat(result.description()).isEqualTo("Does something useful.");
    assertThat(result.body()).contains("This is the body.");
  }

  @Test
  void parse_descriptionWithBlockScalar_parsedCorrectly() {
    String skillMd =
        "---\nname: my-skill\ndescription: |\n  Line one.\n  Line two.\n---\nBody here.\n";

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.description()).contains("Line one.");
    assertThat(result.description()).contains("Line two.");
  }

  @Test
  void parse_missingNameInFrontmatter_returnsNullName() {
    String skillMd = "---\ndescription: A skill without a name.\n---\n";

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.name()).isNull();
    assertThat(result.description()).isEqualTo("A skill without a name.");
  }

  @Test
  void parse_unknownFrontmatterKeys_silentlyIgnored() {
    String skillMd =
        "---\nname: my-skill\ndescription: Desc.\nallowed-tools: bash\nversion: 1.0\n---\n";

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.name()).isEqualTo("my-skill");
    assertThat(result.description()).isEqualTo("Desc.");
  }

  @Test
  void parse_crlfLineEndings_normalizedAndParsed() {
    String skillMd = "---\r\nname: my-skill\r\ndescription: Desc.\r\n---\r\nBody.\r\n";

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.name()).isEqualTo("my-skill");
    assertThat(result.description()).isEqualTo("Desc.");
    assertThat(result.body()).contains("Body.");
  }

  @Test
  void parse_leadingBlankLineBeforeFrontmatter_accepted() {
    String skillMd = "\n---\nname: my-skill\ndescription: Desc.\n---\n";

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.name()).isEqualTo("my-skill");
  }

  // -------------------------------------------------------------------------
  // Error cases
  // -------------------------------------------------------------------------

  @Test
  void parse_nullInput_throwsInvalidSkillException() {
    assertThatThrownBy(() -> parser.parse(null))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("empty");
  }

  @Test
  void parse_emptyString_throwsInvalidSkillException() {
    assertThatThrownBy(() -> parser.parse(""))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("empty");
  }

  @Test
  void parse_noFrontmatter_throwsInvalidSkillException() {
    assertThatThrownBy(() -> parser.parse("Just some text without frontmatter."))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("---");
  }

  @Test
  void parse_unclosedFrontmatter_throwsInvalidSkillException() {
    assertThatThrownBy(() -> parser.parse("---\nname: my-skill\ndescription: Desc.\n"))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("closing");
  }

  @Test
  void parse_missingDescription_throwsInvalidSkillException() {
    assertThatThrownBy(() -> parser.parse("---\nname: my-skill\n---\n"))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("description");
  }

  @Test
  void parse_blankDescription_throwsInvalidSkillException() {
    assertThatThrownBy(() -> parser.parse("---\nname: my-skill\ndescription:   \n---\n"))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("description");
  }
}
