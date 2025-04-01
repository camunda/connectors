/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agents.schema;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Duration;

public class CachingAdHocToolsSchemaResolver implements AdHocToolsSchemaResolver {

  private final LoadingCache<AdHocToolsIdentifier, AdHocToolsSchema> cache;

  public CachingAdHocToolsSchemaResolver(AdHocToolsSchemaResolver delegate) {
    this.cache =
        Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build(id -> delegate.resolveSchema(id.processDefinitionKey(), id.adHocSubprocessId()));
  }

  @Override
  public AdHocToolsSchema resolveSchema(Long processDefinitionKey, String adHocSubprocessId) {
    return cache.get(new AdHocToolsIdentifier(processDefinitionKey, adHocSubprocessId));
  }

  private record AdHocToolsIdentifier(Long processDefinitionKey, String adHocSubprocessId) {}
}
