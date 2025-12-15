/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.email.model.config;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "filterSpecification")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = FilterCriteria.SimpleConfiguration.class,
      name = FilterCriteria.SimpleConfiguration.TYPE),
  @JsonSubTypes.Type(
      value = FilterCriteria.AdvancedConfiguration.class,
      name = FilterCriteria.AdvancedConfiguration.TYPE),
})
@TemplateDiscriminatorProperty(
    label = "Filter Specification",
    description = "Use predefined filter conditions or provide your own OData filter string",
    group = "pollingConfig",
    name = "filterSpecification",
    defaultValue = FilterCriteria.SimpleConfiguration.TYPE)
public sealed interface FilterCriteria {
  String getFilterString();

  @TemplateSubType(id = FilterCriteria.SimpleConfiguration.TYPE, label = "Simple")
  record SimpleConfiguration(
      @TemplateProperty(
              label = "Only Unread",
              tooltip = "Only fetch unread emails",
              defaultValue = "true",
              defaultValueType = TemplateProperty.DefaultValueType.Boolean)
          boolean onlyUnread)
      implements FilterCriteria {
    @TemplateProperty(ignore = true)
    public static final String TYPE = "simple";

    @Override
    public String getFilterString() {
      List<String> filters = new ArrayList<>();
      if (onlyUnread) {
        filters.add("isRead eq false");
      }

      return String.join(" and  ", filters);
    }
  }

  @TemplateSubType(id = FilterCriteria.AdvancedConfiguration.TYPE, label = "Advanced")
  record AdvancedConfiguration(
      @NotBlank
          @FEEL
          @TemplateProperty(
              label = "OData Filter String",
              tooltip =
                  "A custom OData filter expression. <a href='https://learn.microsoft.com/en-us/graph/filter-query-parameter' target='_blank'>See OData filter documentation</a>")
          String filterString)
      implements FilterCriteria {
    @TemplateProperty(ignore = true)
    public static final String TYPE = "advanced";

    @Override
    public String getFilterString() {
      return filterString;
    }
  }
}
