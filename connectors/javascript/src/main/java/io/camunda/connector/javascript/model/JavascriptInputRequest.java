/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.javascript.model;

import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import java.util.List;

public record JavascriptInputRequest(
    @TemplateProperty(group = "javascript") Object script,
    @TemplateProperty(group = "javascript", feel = FeelMode.required) List<Object> parameters) {}
