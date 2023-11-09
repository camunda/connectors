/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.model.request;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

public record Configuration(
    @TemplateProperty(group = "configuration", label = "Control room URL") @NotBlank
        String controlRoomUrl,
    @TemplateProperty(
            group = "timeout",
            defaultValue = "20",
            optional = true,
            description =
                "Sets the timeout in seconds to establish a connection or 0 for an infinite timeout")
        Integer connectionTimeoutInSeconds) {}
