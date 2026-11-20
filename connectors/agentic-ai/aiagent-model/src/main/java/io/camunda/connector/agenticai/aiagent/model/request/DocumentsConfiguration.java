/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * How the agent handles the documents of its conversation.
 *
 * @param references whether a new agent instance tracks its documents: the model gets a reference
 *     to each document and the tools to load and return them. A missing value is {@code false}.
 * @param userPromptDelivery how the model gets the documents of the user prompt. A missing value is
 *     {@link DocumentDelivery#CONTENT}.
 */
@UserPromptDeliveryAvailable
public record DocumentsConfiguration(
    @TemplateProperty(
            group = "documents",
            label = "Use document references",
            description = "The model loads documents on demand and can pass them to tools.",
            tooltip =
                "The model gets a short reference for each document instead of its content, and uses tools to load or return a document. "
                    + "This saves tokens for large or many documents. "
                    + "When cleared, the model gets the full content of every document, and tools cannot take documents as parameters.",
            type = TemplateProperty.PropertyType.Boolean,
            defaultValue = "true",
            defaultValueType = TemplateProperty.DefaultValueType.Boolean)
        boolean references,
    @TemplateProperty(
            group = "documents",
            label = "User prompt delivery",
            tooltip =
                "Reference (recommended): the model loads the content of a document of the user prompt only when it needs it. "
                    + "Content: the model gets the full content of every document with the prompt, which uses more tokens.",
            type = TemplateProperty.PropertyType.Dropdown,
            choices = {
              @TemplateProperty.DropdownPropertyChoice(value = "reference", label = "Reference"),
              @TemplateProperty.DropdownPropertyChoice(value = "content", label = "Content")
            },
            defaultValue = "reference",
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "data.documents.references",
                    equalsBoolean = TemplateProperty.EqualsBoolean.TRUE))
        DocumentDelivery userPromptDelivery) {

  /** Document references off, as for an element without document configuration. */
  public static final DocumentsConfiguration REFERENCES_OFF =
      new DocumentsConfiguration(false, DocumentDelivery.CONTENT);

  public DocumentsConfiguration(boolean references, @Nullable DocumentDelivery userPromptDelivery) {
    this.references = references;
    // the template sets no delivery while document references are off
    this.userPromptDelivery =
        Objects.requireNonNullElse(userPromptDelivery, DocumentDelivery.CONTENT);
  }
}
