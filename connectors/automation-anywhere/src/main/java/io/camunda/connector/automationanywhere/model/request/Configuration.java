/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.model.request;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;

/**
 * @param controlRoomUrl not {@code @NotBlank}: a bound credential may supply it instead, so
 *     requiredness is asserted on the effective value in {@link
 *     AutomationAnywhereRequest#isControlRoomUrlPresent()}. Hidden once a credential is bound.
 */
public record Configuration(
    @TemplateProperty(
            group = "configuration",
            label = "Control room URL",
            condition =
                @PropertyCondition(
                    property = "authenticationConfiguration",
                    isEmpty = NullableBoolean.TRUE),
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String controlRoomUrl,
    @TemplateProperty(
            group = "timeout",
            defaultValueType = TemplateProperty.DefaultValueType.Number,
            defaultValue = "20",
            optional = true,
            tooltip =
                "Sets the timeout in seconds to establish a connection or 0 for an infinite timeout")
        Integer connectionTimeoutInSeconds) {

  public Configuration {
    if (controlRoomUrl != null && controlRoomUrl.isBlank()) {
      controlRoomUrl = null;
    }
  }
}
