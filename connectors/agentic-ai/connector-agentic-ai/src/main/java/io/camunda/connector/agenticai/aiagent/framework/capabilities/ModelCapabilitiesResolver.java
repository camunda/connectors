/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

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

  ModelCapabilities resolve(
      String apiFamily,
      String modelId,
      @Nullable String backend,
      Optional<ModelCapabilitiesOverride> override);
}
