/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;

/**
 * Additional content offered for a Camunda recipient: no platform-specific card format applies, so
 * the choice is only a form.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = AdditionalContent.None.class, name = "none"),
  @JsonSubTypes.Type(value = AdditionalContent.Form.class, name = "form")
})
@TemplateDiscriminatorProperty(
    name = "type",
    group = "message",
    label = "Additional content",
    description = "Content sent alongside the message. A Camunda recipient supports a linked form.",
    defaultValue = "none")
public sealed interface CamundaExtra extends AdditionalContent
    permits AdditionalContent.None, AdditionalContent.Form {}
