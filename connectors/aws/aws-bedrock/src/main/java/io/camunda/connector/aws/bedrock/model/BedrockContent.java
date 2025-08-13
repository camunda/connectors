/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.connector.api.document.Document;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BedrockContent {
  private String text;

  private Document document;

  public BedrockContent(String text) {
    this.text = text;
  }

  public BedrockContent(Document document) {
    this.document = document;
  }

  public BedrockContent() {}

  public @Valid @NotBlank String getText() {
    return text;
  }

  public void setText(@Valid @NotBlank String text) {
    this.text = text;
  }

  public Document getDocument() {
    return document;
  }

  public void setDocument(Document document) {
    this.document = document;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BedrockContent that = (BedrockContent) o;
    return Objects.equals(text, that.text) && Objects.equals(document, that.document);
  }

  @Override
  public int hashCode() {
    return Objects.hash(text, document);
  }
}
