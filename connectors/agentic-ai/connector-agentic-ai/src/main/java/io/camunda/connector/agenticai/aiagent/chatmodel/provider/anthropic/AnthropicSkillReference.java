/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.anthropic;

import com.anthropic.models.beta.messages.BetaSkillParams;

/**
 * A parsed reference to an Anthropic Agent Skill, configured by users as a {@code
 * type:skill:version} FEEL string (see {@link
 * io.camunda.connector.agenticai.aiagent.model.request.chatmodel.AnthropicChatModel.AnthropicConnection#skills()}).
 *
 * <p>Parsing splits the raw string on {@code ":"} and disambiguates by segment count:
 *
 * <ul>
 *   <li>1 segment ({@code "pptx"}): {@code (ANTHROPIC, "pptx", "latest")}
 *   <li>3 segments ({@code "custom:my-skill:v"}): the first segment is the explicit type ({@code
 *       anthropic} or {@code custom}, case-insensitive), the second the skill id, the third the
 *       version.
 *   <li>2 segments: if the first segment is exactly {@code "custom"} or {@code "anthropic"}
 *       (case-insensitive), it's treated as an explicit type and the second segment is the skill id
 *       with version defaulting to {@code "latest"} (e.g. {@code "custom:my-skill"} -&gt; {@code
 *       (CUSTOM, "my-skill", "latest")}); otherwise the first segment is the skill id and the
 *       second the version, both defaulting to type {@code ANTHROPIC} (e.g. {@code
 *       "pptx:special-version"} -&gt; {@code (ANTHROPIC, "pptx", "special-version")}).
 * </ul>
 *
 * Blank segments, more than 3 segments, or an unknown explicit type fail loud.
 */
public record AnthropicSkillReference(BetaSkillParams.Type type, String skillId, String version) {

  private static final String DEFAULT_VERSION = "latest";
  private static final String TYPE_ANTHROPIC = "anthropic";
  private static final String TYPE_CUSTOM = "custom";
  private static final int MAX_SEGMENTS = 3;

  public static AnthropicSkillReference parse(String raw) {
    if (raw.isBlank()) {
      throw new IllegalArgumentException(
          "Anthropic skill reference must not be blank, got: \"%s\"".formatted(raw));
    }

    // -1 limit keeps trailing empty segments (e.g. "pptx:") so blank-segment validation below
    // catches them rather than silently dropping them.
    final String[] segments = raw.split(":", -1);
    if (segments.length > MAX_SEGMENTS) {
      throw new IllegalArgumentException(
          "Anthropic skill reference must have at most %d \":\"-separated segments (type:skill:version), got: \"%s\""
              .formatted(MAX_SEGMENTS, raw));
    }

    for (int i = 0; i < segments.length; i++) {
      segments[i] = segments[i].trim();
      if (segments[i].isBlank()) {
        throw new IllegalArgumentException(
            "Anthropic skill reference must not contain blank \":\"-separated segments, got: \"%s\""
                .formatted(raw));
      }
    }

    return switch (segments.length) {
      case 1 ->
          new AnthropicSkillReference(BetaSkillParams.Type.ANTHROPIC, segments[0], DEFAULT_VERSION);
      case 2 -> parseTwoSegments(segments[0], segments[1], raw);
      case 3 -> new AnthropicSkillReference(parseType(segments[0], raw), segments[1], segments[2]);
      default ->
          throw new IllegalStateException(
              "Unreachable: segment count already validated to be 1-3, got: " + segments.length);
    };
  }

  private static AnthropicSkillReference parseTwoSegments(String first, String second, String raw) {
    if (TYPE_CUSTOM.equalsIgnoreCase(first) || TYPE_ANTHROPIC.equalsIgnoreCase(first)) {
      return new AnthropicSkillReference(parseType(first, raw), second, DEFAULT_VERSION);
    }
    return new AnthropicSkillReference(BetaSkillParams.Type.ANTHROPIC, first, second);
  }

  private static BetaSkillParams.Type parseType(String rawType, String raw) {
    if (TYPE_ANTHROPIC.equalsIgnoreCase(rawType)) {
      return BetaSkillParams.Type.ANTHROPIC;
    }
    if (TYPE_CUSTOM.equalsIgnoreCase(rawType)) {
      return BetaSkillParams.Type.CUSTOM;
    }
    throw new IllegalArgumentException(
        "Unknown Anthropic skill type \"%s\" in skill reference: \"%s\"".formatted(rawType, raw));
  }
}
