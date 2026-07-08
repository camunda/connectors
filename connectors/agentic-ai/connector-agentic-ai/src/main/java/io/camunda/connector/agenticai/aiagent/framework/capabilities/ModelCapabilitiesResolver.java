/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import java.util.Optional;

/**
 * Resolves a runtime {@link ModelCapabilities} from the capability matrix using the four-step
 * chain:
 *
 * <ol>
 *   <li>Connector config override (if present)
 *   <li>Exact id or alias match within the api family
 *   <li>Glob pattern match (longest match wins)
 *   <li>Conservative defaults (text-only, all flags false, null limits)
 * </ol>
 */
public interface ModelCapabilitiesResolver {

  ModelCapabilities resolve(String apiFamily, String modelId, Optional<ModelCapabilities> override);
}
