/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.util.Arrays;

/**
 * Parser for {@code SKILL.md} frontmatter.
 *
 * <p>Frontmatter is delimited by a leading {@code ---} line and a closing {@code ---} line. The
 * block between the delimiters is parsed as YAML by Jackson's {@link YAMLMapper} (which wraps
 * SnakeYAML), so it correctly handles block scalars ({@code description: |}), folded scalars,
 * quoted values containing colons, and multi-line values — none of which a naive line/colon split
 * handles.
 *
 * <p><b>Leniency contract:</b>
 *
 * <ul>
 *   <li>If {@code description} is missing or blank → {@link InvalidSkillException} is thrown.
 *   <li>If {@code name} is missing or blank → no exception; {@link ParsedSkillMd#name()} returns
 *       {@code null}. Callers may fall back to a derived name (e.g. from the document file name).
 *   <li>Unknown frontmatter keys (e.g. {@code allowed-tools}) are silently ignored.
 *   <li>Both LF and CRLF line endings are handled.
 * </ul>
 */
public class SkillMdParser {

  private static final String FRONTMATTER_DELIMITER = "---";

  /** Thread-safe and reusable; shared across concurrent invocations. */
  private static final YAMLMapper YAML = new YAMLMapper();

  /**
   * Parses a {@code SKILL.md} string and returns its structured representation.
   *
   * @param skillMd the full text of SKILL.md
   * @return parsed result; {@link ParsedSkillMd#name()} may be {@code null} when the frontmatter
   *     {@code name} key is absent or blank
   * @throws InvalidSkillException if there is no frontmatter block, if the frontmatter is not valid
   *     YAML, or if {@code description} is missing/blank
   */
  public ParsedSkillMd parse(String skillMd) {
    if (skillMd == null || skillMd.isEmpty()) {
      throw new InvalidSkillException("SKILL.md is empty");
    }

    // Normalize line endings to LF
    String normalized = skillMd.replace("\r\n", "\n").replace("\r", "\n");

    String[] lines = normalized.split("\n", -1);

    // The very first non-blank line must be the opening ---
    int openIdx = -1;
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (line.isBlank()) {
        continue;
      }
      if (FRONTMATTER_DELIMITER.equals(line)) {
        openIdx = i;
      }
      break;
    }

    if (openIdx < 0) {
      throw new InvalidSkillException(
          "SKILL.md has no YAML frontmatter block (expected opening '---' line)");
    }

    // Find closing ---
    int closeIdx = -1;
    for (int i = openIdx + 1; i < lines.length; i++) {
      if (FRONTMATTER_DELIMITER.equals(lines[i])) {
        closeIdx = i;
        break;
      }
    }

    if (closeIdx < 0) {
      throw new InvalidSkillException(
          "SKILL.md frontmatter block is not closed (missing closing '---' line)");
    }

    // Parse the frontmatter block as YAML
    String frontmatterYaml = String.join("\n", Arrays.asList(lines).subList(openIdx + 1, closeIdx));
    JsonNode frontmatter;
    try {
      frontmatter = YAML.readTree(frontmatterYaml);
    } catch (Exception e) {
      throw new InvalidSkillException("SKILL.md frontmatter is not valid YAML: " + e.getMessage());
    }
    if (frontmatter == null || !frontmatter.isObject()) {
      throw new InvalidSkillException(
          "SKILL.md frontmatter must be a YAML mapping of key/value pairs");
    }

    // Extract known keys
    String name = textOrNull(frontmatter, "name");
    if (name != null && name.isBlank()) {
      name = null;
    }

    String description = textOrNull(frontmatter, "description");
    if (description == null || description.isBlank()) {
      throw new InvalidSkillException(
          "SKILL.md frontmatter is missing a required 'description' field (or it is blank)");
    }
    description = description.trim();

    // Build the body: everything after the closing ---
    StringBuilder bodyBuilder = new StringBuilder();
    for (int i = closeIdx + 1; i < lines.length; i++) {
      bodyBuilder.append(lines[i]);
      if (i < lines.length - 1) {
        bodyBuilder.append('\n');
      }
    }
    // Trim a single leading blank line (common formatting convention)
    String body = bodyBuilder.toString();
    if (body.startsWith("\n")) {
      body = body.substring(1);
    }

    return new ParsedSkillMd(name, description, body);
  }

  private static String textOrNull(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  /** Structured result of parsing a {@code SKILL.md} file. */
  public record ParsedSkillMd(String name, String description, String body) {}
}
