/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.azure.email.model.config;

import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import java.time.OffsetDateTime;
import java.util.Arrays;

@TemplateDiscriminatorProperty(
    label = "Field Selection",
    group = "pollingConfig",
    name = "data.fieldSelection",
    defaultValue = FieldSelection.SimpleConfiguration.TYPE)
public sealed interface FieldSelection {
  String[] getFieldsArray();

  record SimpleConfiguration(boolean onlyUnread, OffsetDateTime earliestReceived)
      implements FieldSelection {
    @TemplateProperty(ignore = true)
    public static final String TYPE = "simple";

    @Override
    public String[] getFieldsArray() {
      return null;
    }
  }

  record AdvancedConfiguration(String fields) implements FieldSelection {
    @TemplateProperty(ignore = true)
    public static final String TYPE = "advanced";

    @Override
    public String[] getFieldsArray() {
      return Arrays.stream(fields.split(",")).toArray(String[]::new);
    }
  }
}
