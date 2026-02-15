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
import io.camunda.connector.generator.dsl.Property;
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
          @FEEL
          boolean onlyUnread,
      @FEEL
          @TemplateProperty(
              label = "Subject Contains",
              tooltip = "Only fetch emails where subject contains this text (case-sensitive)",
              optional = true,
              feel = Property.FeelMode.optional)
          String subjectContains,
      @FEEL
          @TemplateProperty(
              label = "From Email Address",
              tooltip =
                  "Only fetch emails from this sender address (exact match, e.g. 'invoice@vendor.com')",
              optional = true,
              feel = Property.FeelMode.optional)
          String fromAddress)
      implements FilterCriteria {
    @TemplateProperty(ignore = true)
    public static final String TYPE = "simple";

    @Override
    public String getFilterString() {
      List<String> filters = new ArrayList<>();
      if (onlyUnread) {
        filters.add("isRead eq false");
      }
      if (subjectContains != null && !subjectContains.isBlank()) {
        filters.add("contains(subject, '" + escapeODataString(subjectContains) + "')");
      }
      if (fromAddress != null && !fromAddress.isBlank()) {
        filters.add("from/emailAddress/address eq '" + escapeODataString(fromAddress) + "'");
      }

      return String.join(" and ", filters);
    }

    /**
     * Escapes a string value for use in an OData filter expression.
     *
     * <p>According to the OData URL Conventions specification, single quotes within string literals
     * must be represented as two consecutive single quotes.
     *
     * @param value the string value to escape
     * @return the escaped string value
     * @see <a
     *     href="https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part2-url-conventions.html#sec_URLSyntax">OData
     *     v4.01 URL Conventions - Section 2.2 URL Syntax</a>
     */
    private static String escapeODataString(String value) {
      // Single quotes must be escaped as two consecutive single quotes
      return value.replace("'", "''");
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
