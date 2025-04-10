/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.resolver;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Duration;
import java.util.Optional;

public class CachingAdHocToolsSchemaResolver implements AdHocToolsSchemaResolver {

  private final LoadingCache<AdHocToolsIdentifier, AdHocToolsSchema> cache;

  public CachingAdHocToolsSchemaResolver(
      AdHocToolsSchemaResolver delegate, CacheConfiguration cacheConfiguration) {
    this.cache = buildCache(delegate, cacheConfiguration);
  }

  @Override
  public AdHocToolsSchema resolveSchema(Long processDefinitionKey, String adHocSubprocessId) {
    return cache.get(new AdHocToolsIdentifier(processDefinitionKey, adHocSubprocessId));
  }

  private static LoadingCache<AdHocToolsIdentifier, AdHocToolsSchema> buildCache(
      AdHocToolsSchemaResolver delegate, CacheConfiguration config) {
    final var builder = Caffeine.newBuilder();
    Optional.ofNullable(config.maxSize()).ifPresent(builder::maximumSize);
    Optional.ofNullable(config.expireAfterWrite()).ifPresent(builder::expireAfterWrite);

    return builder.build(
        id -> delegate.resolveSchema(id.processDefinitionKey(), id.adHocSubprocessId()));
  }

  private record AdHocToolsIdentifier(Long processDefinitionKey, String adHocSubprocessId) {}

  public record CacheConfiguration(Integer maxSize, Duration expireAfterWrite) {}
}
