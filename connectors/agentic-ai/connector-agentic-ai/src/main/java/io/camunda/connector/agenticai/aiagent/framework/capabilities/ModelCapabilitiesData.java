/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

/**
 * Projects a fully-merged capability matrix tree (materialised via Jackson {@code treeToValue})
 * onto a concrete, provider-specific {@link ModelCapabilities}. Each provider ships its own sparse
 * DTO implementing this interface (e.g. {@code AnthropicModelCapabilitiesData}); the resolver's
 * deep-merge cascade stays provider-agnostic and only the final materialisation is parameterised by
 * the DTO class.
 */
public interface ModelCapabilitiesData<T extends ModelCapabilities> {
  T toModelCapabilities();
}
