/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.skill;

import java.util.List;

/**
 * An immutable representation of a parsed skill bundle.
 *
 * <p>{@code name} and {@code description} originate from the {@code SKILL.md} YAML frontmatter.
 * {@code skillMdBody} is the {@code SKILL.md} content with the frontmatter block stripped. {@code
 * files} contains every file extracted from the bundle zip, each with a root-relative path (i.e. a
 * single common top-level directory is stripped if present) and its raw byte content.
 *
 * @param name the skill name (never blank; resolved from frontmatter or the document file name)
 * @param description the skill description from frontmatter (never blank)
 * @param skillMdBody the instruction text from {@code SKILL.md} with frontmatter removed
 * @param files all files in the bundle, keyed by their root-relative path
 */
public record Skill(String name, String description, String skillMdBody, List<SkillFile> files) {

  /**
   * A single file extracted from a skill bundle.
   *
   * @param relativePath the root-relative path within the skill (e.g. {@code SKILL.md}, {@code
   *     scripts/run.sh}), always using forward slashes
   * @param content raw byte content of the file
   */
  public record SkillFile(String relativePath, byte[] content) {}

  /**
   * Returns the relative paths of all bundled files that are <em>not</em> {@code SKILL.md}.
   *
   * <p>This is the list surfaced to the model by {@code load_skill} in the {@code
   * <skill_resources>} section so it knows which files are available without having to read them
   * eagerly.
   *
   * @return an unmodifiable list of resource file paths, in bundle order
   */
  public List<String> resourceFileNames() {
    return files.stream()
        .map(SkillFile::relativePath)
        .filter(path -> !path.equals("SKILL.md"))
        .toList();
  }
}
