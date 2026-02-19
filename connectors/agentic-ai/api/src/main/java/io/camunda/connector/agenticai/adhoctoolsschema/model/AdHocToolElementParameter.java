/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import java.util.Map;
import org.springframework.lang.Nullable;

@AgenticAiRecord
@JsonDeserialize(
    builder = AdHocToolElementParameter.AdHocToolElementParameterJacksonProxyBuilder.class)
public record AdHocToolElementParameter(
    String name,
    @Nullable String description,
    @Nullable String type,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, Object> schema,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) @Nullable Map<String, Object> options)
    implements AdHocToolElementParameterBuilder.With {

  public AdHocToolElementParameter(String name) {
    this(name, null, null, null, null);
  }

  public AdHocToolElementParameter(String name, String description) {
    this(name, description, null, null, null);
  }

  public AdHocToolElementParameter(String name, String description, String type) {
    this(name, description, type, null, null);
  }

  public AdHocToolElementParameter(
      String name, String description, String type, Map<String, Object> schema) {
    this(name, description, type, schema, null);
  }

  public static AdHocToolElementParameterBuilder builder() {
    return AdHocToolElementParameterBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class AdHocToolElementParameterJacksonProxyBuilder
      extends AdHocToolElementParameterBuilder {}
}
