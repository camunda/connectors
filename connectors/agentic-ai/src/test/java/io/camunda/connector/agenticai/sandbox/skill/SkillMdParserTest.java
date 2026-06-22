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
  void parse_wellFormedFrontmatter_returnsNameDescriptionAndBody() {
    String skillMd =
        """
        ---
        name: pdf-tools
        description: Extract and merge PDF forms.
        ---
        This is the body.
        """;

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.name()).isEqualTo("pdf-tools");
    assertThat(result.description()).isEqualTo("Extract and merge PDF forms.");
    assertThat(result.body()).contains("This is the body.");
  }

  @Test
  void parse_bodyIsStripped_frontmatterNotIncludedInBody() {
    String skillMd =
        """
        ---
        name: my-skill
        description: A skill.
        ---
        Body content here.
        """;

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.body()).doesNotContain("---");
    assertThat(result.body()).doesNotContain("name:");
    assertThat(result.body()).doesNotContain("description:");
    assertThat(result.body()).contains("Body content here.");
  }

  @Test
  void parse_leadingBlankLineInBodyIsStripped() {
    String skillMd = "---\nname: s\ndescription: d\n---\n\nActual body.\n";

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.body()).startsWith("Actual body.");
  }

  @Test
  void parse_multilineBody_isPreservedCompletely() {
    String skillMd =
        """
        ---
        name: s
        description: d
        ---
        Line 1
        Line 2
        Line 3
        """;

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.body()).contains("Line 1");
    assertThat(result.body()).contains("Line 2");
    assertThat(result.body()).contains("Line 3");
  }

  // -------------------------------------------------------------------------
  // Unquoted colon in description
  // -------------------------------------------------------------------------

  @Test
  void parse_unquotedColonInDescription_doesNotFail() {
    String skillMd =
        """
        ---
        name: api-skill
        description: Calls the REST API: use for HTTP requests, REST, JSON payloads.
        ---
        Body.
        """;

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.description())
        .isEqualTo("Calls the REST API: use for HTTP requests, REST, JSON payloads.");
  }

  // -------------------------------------------------------------------------
  // Quoted values
  // -------------------------------------------------------------------------

  @Test
  void parse_doubleQuotedValue_quotesAreStripped() {
    String skillMd =
        """
        ---
        name: "my quoted skill"
        description: "A description in quotes."
        ---
        Body.
        """;

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.name()).isEqualTo("my quoted skill");
    assertThat(result.description()).isEqualTo("A description in quotes.");
  }

  @Test
  void parse_singleQuotedValue_quotesAreStripped() {
    String skillMd =
        """
        ---
        name: 'single-quoted'
        description: 'Single quoted description.'
        ---
        Body.
        """;

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.name()).isEqualTo("single-quoted");
    assertThat(result.description()).isEqualTo("Single quoted description.");
  }

  // -------------------------------------------------------------------------
  // Unknown/extra frontmatter keys are tolerated
  // -------------------------------------------------------------------------

  @Test
  void parse_unknownFrontmatterKey_isIgnoredWithoutFailure() {
    String skillMd =
        """
        ---
        name: tool-skill
        description: Useful tool skill.
        allowed-tools: bash, fs_read
        version: 1.2.3
        ---
        Body.
        """;

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.name()).isEqualTo("tool-skill");
    assertThat(result.description()).isEqualTo("Useful tool skill.");
  }

  // -------------------------------------------------------------------------
  // Missing frontmatter → InvalidSkillException
  // -------------------------------------------------------------------------

  @Test
  void parse_noFrontmatterBlock_throwsInvalidSkillException() {
    String skillMd = "Just plain markdown without any frontmatter.";

    assertThatThrownBy(() -> parser.parse(skillMd))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("---");
  }

  @Test
  void parse_emptyString_throwsInvalidSkillException() {
    assertThatThrownBy(() -> parser.parse("")).isInstanceOf(InvalidSkillException.class);
  }

  @Test
  void parse_nullString_throwsInvalidSkillException() {
    assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(InvalidSkillException.class);
  }

  @Test
  void parse_unclosedFrontmatter_throwsInvalidSkillException() {
    String skillMd =
        """
        ---
        name: s
        description: d
        """;

    assertThatThrownBy(() -> parser.parse(skillMd))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("---");
  }

  // -------------------------------------------------------------------------
  // Missing/blank description → InvalidSkillException
  // -------------------------------------------------------------------------

  @Test
  void parse_missingDescription_throwsInvalidSkillException() {
    String skillMd =
        """
        ---
        name: s
        ---
        Body.
        """;

    assertThatThrownBy(() -> parser.parse(skillMd))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("description");
  }

  @Test
  void parse_blankDescription_throwsInvalidSkillException() {
    String skillMd =
        """
        ---
        name: s
        description:
        ---
        Body.
        """;

    assertThatThrownBy(() -> parser.parse(skillMd))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("description");
  }

  // -------------------------------------------------------------------------
  // Missing name → recoverable (returns null name, no throw)
  // -------------------------------------------------------------------------

  @Test
  void parse_missingName_returnsNullNameWithoutThrowing() {
    String skillMd =
        """
        ---
        description: A skill without a name.
        ---
        Body.
        """;

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.name()).isNull();
    assertThat(result.description()).isEqualTo("A skill without a name.");
  }

  @Test
  void parse_blankName_returnsNullNameWithoutThrowing() {
    String skillMd =
        """
        ---
        name:
        description: Still valid.
        ---
        Body.
        """;

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.name()).isNull();
    assertThat(result.description()).isEqualTo("Still valid.");
  }

  // -------------------------------------------------------------------------
  // CRLF handling
  // -------------------------------------------------------------------------

  @Test
  void parse_crlfLineEndings_parsedCorrectly() {
    String skillMd =
        "---\r\nname: crlf-skill\r\ndescription: Uses CRLF.\r\n---\r\nBody with CRLF.\r\n";

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.name()).isEqualTo("crlf-skill");
    assertThat(result.description()).isEqualTo("Uses CRLF.");
    assertThat(result.body()).contains("Body with CRLF.");
  }

  @Test
  void parse_mixedLineEndings_parsedCorrectly() {
    String skillMd = "---\r\nname: mixed\ndescription: Mixed endings.\r\n---\nBody.\n";

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.name()).isEqualTo("mixed");
    assertThat(result.description()).isEqualTo("Mixed endings.");
  }

  // -------------------------------------------------------------------------
  // Body correctly stripped
  // -------------------------------------------------------------------------

  @Test
  void parse_emptyBody_returnsEmptyString() {
    String skillMd =
        """
        ---
        name: s
        description: d
        ---
        """;

    ParsedSkillMd result = parser.parse(skillMd);

    assertThat(result.body()).isEmpty();
  }

  @Test
  void parse_bodyPreservesInternalBlankLines() {
    String skillMd = "---\nname: s\ndescription: d\n---\n\nPara 1.\n\nPara 2.\n";

    ParsedSkillMd result = parser.parse(skillMd);

    // Leading blank line stripped, then two paragraphs with blank line between
    assertThat(result.body()).contains("Para 1.");
    assertThat(result.body()).contains("Para 2.");
    // The blank line between paragraphs should be preserved
    assertThat(result.body()).contains("\n\n");
  }
}
