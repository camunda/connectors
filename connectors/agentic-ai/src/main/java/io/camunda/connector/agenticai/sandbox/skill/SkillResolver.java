/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.skill;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a list of {@link Document}s into a list of {@link Skill}s.
 *
 * <p>Each document is expected to contain the raw bytes of a skill bundle zip. The resolver is
 * stateless and safe to use as a singleton.
 *
 * <h2>Error handling</h2>
 *
 * <ul>
 *   <li>If a single document fails to parse ({@link InvalidSkillException}), the resolver <em>logs
 *       a warning and skips that document</em>. Other documents in the batch are unaffected and the
 *       successfully-parsed skills are returned in input order.
 *   <li>If two resolved skills share the same name, the <em>first occurrence is kept</em> and
 *       subsequent duplicates are skipped with a warning. This is a warn-and-skip strategy rather
 *       than a hard failure, because skills come from user configuration and a duplicate is more
 *       likely a misconfiguration than a security issue.
 * </ul>
 *
 * <p>A {@code null} or empty input list returns an empty list.
 */
public class SkillResolver {

  private static final Logger LOG = LoggerFactory.getLogger(SkillResolver.class);

  private final SkillBundleReader bundleReader;

  public SkillResolver() {
    this(new SkillBundleReader());
  }

  public SkillResolver(SkillBundleReader bundleReader) {
    this.bundleReader = bundleReader;
  }

  /**
   * Resolves skill documents into {@link Skill} instances.
   *
   * @param skillDocuments the documents to resolve; may be {@code null} or empty
   * @return resolved skills in input order, skipping any that fail to parse or are duplicates
   */
  public List<Skill> resolve(List<Document> skillDocuments) {
    if (skillDocuments == null || skillDocuments.isEmpty()) {
      return List.of();
    }

    List<Skill> result = new ArrayList<>(skillDocuments.size());
    Set<String> seenNames = new LinkedHashSet<>();

    for (Document document : skillDocuments) {
      String fallbackName = deriveFallbackName(document);
      Skill skill;
      try {
        byte[] bytes = document.asByteArray();
        skill = bundleReader.read(bytes, fallbackName);
      } catch (InvalidSkillException e) {
        LOG.warn(
            "Skipping skill document '{}': {}",
            fallbackName != null ? fallbackName : "<unknown>",
            e.getMessage());
        continue;
      } catch (Exception e) {
        LOG.warn(
            "Skipping skill document '{}' due to unexpected error: {}",
            fallbackName != null ? fallbackName : "<unknown>",
            e.getMessage(),
            e);
        continue;
      }

      if (!seenNames.add(skill.name())) {
        LOG.warn(
            "Skipping duplicate skill name '{}' (already resolved from an earlier document)",
            skill.name());
        continue;
      }

      result.add(skill);
    }

    return List.copyOf(result);
  }

  /**
   * Resolves skill documents into lightweight {@link SkillMetadata} (name + description only),
   * without retaining the bundle file bytes. Used at agent initialization to build the {@code
   * load_skill} name enum and to render the Tier-1 catalog, where the full bundle is not needed.
   *
   * <p>This currently delegates to {@link #resolve(List)} and projects the result, so it pays the
   * same unzip cost; the file bytes are simply not retained. It can later be optimized to parse
   * only {@code SKILL.md}.
   *
   * @param skillDocuments the documents to resolve; may be {@code null} or empty
   * @return metadata in input order, skipping any that fail to parse or are duplicates
   */
  public List<SkillMetadata> resolveMetadata(List<Document> skillDocuments) {
    return resolve(skillDocuments).stream()
        .map(skill -> new SkillMetadata(skill.name(), skill.description()))
        .toList();
  }

  /** Lightweight skill metadata: name and description, without the bundle file bytes. */
  public record SkillMetadata(String name, String description) {}

  /**
   * Resolves a single skill (full bundle, including file bytes) by name, on demand. Used by {@code
   * load_skill} so that bundles are only materialized for the skill the model actually requests,
   * rather than eagerly resolving every configured bundle on each invocation.
   *
   * <p>Delegates to {@link #resolve(List)} and filters, so duplicate-name precedence ("first wins")
   * and parse-failure skipping match the catalog/enum view.
   *
   * @param skillDocuments the configured skill documents; may be {@code null} or empty
   * @param name the requested skill name
   * @return the resolved skill, or empty if no document resolves to that name
   */
  public Optional<Skill> resolveByName(List<Document> skillDocuments, String name) {
    if (skillDocuments == null || skillDocuments.isEmpty() || name == null) {
      return Optional.empty();
    }
    return resolve(skillDocuments).stream().filter(skill -> name.equals(skill.name())).findFirst();
  }

  /**
   * Derives a best-effort fallback name from a document's metadata or reference.
   *
   * <p>Tries {@link DocumentMetadata#getFileName()} first (stripping the {@code .zip} extension if
   * present), then falls back to the string form of {@link Document#reference()}.
   */
  private static String deriveFallbackName(Document document) {
    try {
      DocumentMetadata metadata = document.metadata();
      if (metadata != null) {
        String fileName = metadata.getFileName();
        if (fileName != null && !fileName.isBlank()) {
          if (fileName.toLowerCase().endsWith(".zip")) {
            return fileName.substring(0, fileName.length() - 4);
          }
          return fileName;
        }
      }
    } catch (Exception e) {
      LOG.debug("Could not read document metadata for fallback name: {}", e.getMessage());
    }

    try {
      var ref = document.reference();
      if (ref != null) {
        return ref.toString();
      }
    } catch (Exception e) {
      LOG.debug("Could not read document reference for fallback name: {}", e.getMessage());
    }

    return null;
  }
}
