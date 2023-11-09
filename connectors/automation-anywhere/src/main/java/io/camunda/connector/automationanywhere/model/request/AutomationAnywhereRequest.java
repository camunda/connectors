/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.automationanywhere.model.request;

import io.camunda.connector.automationanywhere.model.request.auth.Authentication;
import io.camunda.connector.automationanywhere.model.request.operation.OperationData;
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record AutomationAnywhereRequest(
    @Valid @FEEL @NotNull @TemplateProperty(group = "authentication", id = "authenticationType")
        Authentication authentication,
    @Valid @FEEL @NotNull @TemplateProperty(group = "configuration", id = "configuration")
        Configuration configuration,
    @Valid @FEEL @NotNull @TemplateProperty(group = "operation", id = "operationType")
        OperationData operation) {}
