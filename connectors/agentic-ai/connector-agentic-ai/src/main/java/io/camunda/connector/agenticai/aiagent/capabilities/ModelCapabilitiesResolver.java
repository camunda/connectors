/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.capabilities;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a runtime {@link ModelCapabilities} from the capability matrix using the following
 * chain:
 *
 * <ol>
 *   <li>Connector config override (if present)
 *   <li>Exact id or alias match within the api family, backend-agnostic tier
 *   <li>Glob pattern match (longest match wins), backend-agnostic tier
 *   <li>Exact id or alias / glob pattern match within the {@code backend}-specific tier (if a
 *       backend is given), LAYERED on top of the backend-agnostic match
 *   <li>Family defaults (an unmatched model under a known api family still gets that family's
 *       {@code defaults} block)
 *   <li>Conservative defaults (text-only, all flags false, null limits) — only when the api family
 *       itself is unknown
 * </ol>
 *
 * <p>{@code backend} is an optional secondary dimension distinguishing how the same model id is
 * served (e.g. direct provider API vs. Azure Foundry vs. Bedrock), which can shift capabilities
 * such as context window. A backend-specific entry layers on top of the backend-agnostic entry
 * matching the same model id: fields the backend-specific entry doesn't touch keep the
 * backend-agnostic (or family-default) value, fields it does touch win.
 */
public interface ModelCapabilitiesResolver {

  <T extends ModelCapabilities> T resolve(
      String apiFamily,
      String modelId,
      @Nullable String backend,
      Optional<ModelCapabilitiesOverride> override,
      Class<? extends ModelCapabilitiesData<T>> dataClass);

  /**
   * Reports whether {@code modelId} matched a matrix entry (exact id/alias, or glob pattern, in
   * either the backend-agnostic or the {@code backend}-specific tier) as opposed to falling through
   * to the api family's {@code defaults} block or the fully conservative defaults. Callers use this
   * to distinguish "declared but not reasoning-capable" from "unknown/custom model, assume it knows
   * what it's doing" when validating provider-specific parameters (e.g. Anthropic thinking/effort)
   * against the resolved capabilities.
   */
  boolean matches(String apiFamily, String modelId, @Nullable String backend);
}
