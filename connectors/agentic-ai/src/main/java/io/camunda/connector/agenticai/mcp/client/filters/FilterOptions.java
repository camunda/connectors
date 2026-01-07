/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.filters;

import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.soabase.recordbuilder.core.RecordBuilder;

@AgenticAiRecord
public record FilterOptions(
    @RecordBuilder.Initializer(source = AllowDenyList.class, value = "allowingEverything")
        AllowDenyList toolFilters,
    @RecordBuilder.Initializer(source = AllowDenyList.class, value = "allowingEverything")
        AllowDenyList resourceFilters) {}
