/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.filters;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.List;

@RecordBuilder
public record FilterOptions(AllowDenyList toolFilters, AllowDenyList resourceFilters) {
  public FilterOptions {
    toolFilters = toolFilters == null ? new AllowDenyList(List.of(), List.of()) : toolFilters;
    resourceFilters =
        resourceFilters == null ? new AllowDenyList(List.of(), List.of()) : resourceFilters;
  }
}
