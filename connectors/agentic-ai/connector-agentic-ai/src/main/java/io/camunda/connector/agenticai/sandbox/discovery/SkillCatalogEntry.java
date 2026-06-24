/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.discovery;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.common.AgenticAiRecord;

/**
 * A single skill entry in the sandbox skill catalog returned by the CREATE operation.
 *
 * <p>Each entry describes a reusable skill script that can be loaded into the sandbox via the
 * {@code load_skill} tool.
 */
@AgenticAiRecord
@JsonDeserialize(builder = SkillCatalogEntry.SkillCatalogEntryJacksonProxyBuilder.class)
public record SkillCatalogEntry(String name, String description, String location)
    implements SkillCatalogEntryBuilder.With {

  public static SkillCatalogEntryBuilder builder() {
    return SkillCatalogEntryBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class SkillCatalogEntryJacksonProxyBuilder extends SkillCatalogEntryBuilder {}
}
