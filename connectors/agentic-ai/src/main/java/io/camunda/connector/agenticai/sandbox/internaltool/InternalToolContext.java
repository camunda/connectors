/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.internaltool;

import io.camunda.connector.agenticai.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.sandbox.skill.Skill;
import io.camunda.connector.agenticai.sandbox.skill.SkillResolver;
import io.camunda.connector.api.document.Document;
import java.util.List;
import java.util.Optional;

/**
 * Per-invocation context carried through the internal-tool execution path. Holds data that is
 * resolved once per agent invocation (outside the internal sub-loop) and handed to every {@link
 * InternalToolHandler} on each call.
 *
 * <p>Skills are carried as their raw {@link Document} bundles plus a {@link SkillResolver}, NOT as
 * pre-resolved {@link Skill}s: {@code load_skill} materializes the requested bundle lazily via
 * {@link #resolveSkill(String)} so that invocations which never load a skill pay no unzip cost.
 * (The Tier-1 catalog is still rendered per invocation by the system-prompt contributor.)
 *
 * @param skillDocs the raw skill bundle documents available to the agent; may be empty, never null
 * @param skillResolver resolver used to materialize a bundle on demand; {@code null} only when
 *     there are no skill documents (see {@link #empty()})
 * @param documentRegistry the per-execution document registry used by {@code
 *     sandbox_import_document} to resolve inbound documents by their stable handle id; never null
 *     (use {@link DocumentRegistry#empty()} when no registry is available)
 */
public record InternalToolContext(
    List<Document> skillDocs, SkillResolver skillResolver, DocumentRegistry documentRegistry) {

  /** Returns an empty context with no skills and an empty document registry. */
  public static InternalToolContext empty() {
    return new InternalToolContext(List.of(), null, DocumentRegistry.empty());
  }

  /** Resolves the named skill's full bundle on demand, or empty if it is unknown. */
  public Optional<Skill> resolveSkill(String name) {
    return skillResolver == null ? Optional.empty() : skillResolver.resolveByName(skillDocs, name);
  }

  /** Returns the names of all configured skills (resolves metadata; used for error listings). */
  public List<String> skillNames() {
    return skillResolver == null
        ? List.of()
        : skillResolver.resolveMetadata(skillDocs).stream()
            .map(SkillResolver.SkillMetadata::name)
            .toList();
  }
}
