/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations.model;

import io.camunda.connector.generator.java.annotation.TemplateLinkedResource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * The form's linked-resource properties are gated on the message-type discriminator rather than on
 * a separate Yes/No toggle, so "Form" behaves as one of the three mutually exclusive message types:
 * no {@code zeebe:linkedResource} block is written to the BPMN unless it is the selected type.
 */
@TemplateLinkedResource(
    linkName = "formDefinition",
    resourceType = "form",
    group = "message",
    resourceIdLabel = "Form ID",
    resourceIdDescription =
        "ID of the Camunda form to render as an adaptive card in the Teams message.",
    bindingTypeLabel = "Form binding",
    conditionProperty = "content.type",
    conditionEquals = MessageContent.FormContent.TYPE)
public record SendMessageRequest(
    @NotNull @Valid Recipient recipient, @NotNull @Valid MessageContent content) {}
