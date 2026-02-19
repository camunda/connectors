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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

@AgenticAiRecord
@JsonDeserialize(builder = AdHocToolElement.AdHocToolElementJacksonProxyBuilder.class)
public record AdHocToolElement(
    String elementId,
    @Nullable String elementName,
    @Nullable String documentation,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> properties,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<AdHocToolElementParameter> parameters)
    implements AdHocToolElementBuilder.With {

  /**
   * Returns the documentation if it is non-null, non-blank, and not only whitespace. Otherwise, it
   * falls back to returning the elementName.
   *
   * @return the documentation if available and valid; otherwise, the elementName.
   */
  public String documentationWithNameFallback() {
    return Optional.ofNullable(documentation)
        .map(String::trim)
        .filter(StringUtils::isNotBlank)
        .orElse(elementName);
  }

  public static AdHocToolElementBuilder builder() {
    return AdHocToolElementBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class AdHocToolElementJacksonProxyBuilder extends AdHocToolElementBuilder {}
}
