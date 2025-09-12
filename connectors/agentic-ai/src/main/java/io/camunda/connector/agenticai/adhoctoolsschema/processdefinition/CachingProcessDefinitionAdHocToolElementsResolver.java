/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.processdefinition;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class CachingProcessDefinitionAdHocToolElementsResolver
    implements ProcessDefinitionAdHocToolElementsResolver {

  private final LoadingCache<AdHocToolsIdentifier, List<AdHocToolElement>> cache;

  public CachingProcessDefinitionAdHocToolElementsResolver(
      ProcessDefinitionAdHocToolElementsResolver delegate, CacheConfiguration cacheConfiguration) {
    this.cache = buildCache(delegate, cacheConfiguration);
  }

  @Override
  public List<AdHocToolElement> resolveToolElements(
      Long processDefinitionKey, String adHocSubProcessId) {
    return cache.get(new AdHocToolsIdentifier(processDefinitionKey, adHocSubProcessId));
  }

  private LoadingCache<AdHocToolsIdentifier, List<AdHocToolElement>> buildCache(
      ProcessDefinitionAdHocToolElementsResolver delegate, CacheConfiguration config) {
    // configured via camunda.connector.agenticai.tools.process-definition.cache.*
    // see AgenticAiConnectorsConfigurationProperties for default values
    final var builder = Caffeine.newBuilder();
    Optional.ofNullable(config.maximumSize()).ifPresent(builder::maximumSize);
    Optional.ofNullable(config.expireAfterWrite()).ifPresent(builder::expireAfterWrite);

    return builder.build(
        id -> delegate.resolveToolElements(id.processDefinitionKey(), id.adHocSubProcessId()));
  }

  private record AdHocToolsIdentifier(Long processDefinitionKey, String adHocSubProcessId) {
    private AdHocToolsIdentifier {
      if (processDefinitionKey == null || processDefinitionKey <= 0) {
        throw new IllegalArgumentException("Process definition key must not be null or negative");
      }

      if (adHocSubProcessId == null || adHocSubProcessId.isBlank()) {
        throw new IllegalArgumentException("adHocSubProcessId cannot be null or empty");
      }
    }
  }

  public record CacheConfiguration(Long maximumSize, Duration expireAfterWrite) {}
}
