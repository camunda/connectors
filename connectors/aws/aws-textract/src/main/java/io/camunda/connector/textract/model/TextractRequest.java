/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.model;

import io.camunda.connector.api.document.DocumentReturnChoice;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import io.camunda.connector.generator.java.annotation.DocumentReturnFormat;
import io.camunda.connector.generator.java.annotation.FieldVisibility;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

@DocumentReturnFormat(
    group = "input",
    supportedFormats = {DocumentReturnChoice.JSON, DocumentReturnChoice.DOCUMENT},
    defaultFormat = DocumentReturnChoice.JSON,
    // TEXT is left out: the payload is always a JSON analysis tree, so decoding the bytes into a
    // String would only yield the same JSON as a single unusable blob.
    encoding = FieldVisibility.HIDDEN,
    tooltip =
        "How the analysis result should be returned. JSON returns the result directly in the"
            + " process variables; Document reference uploads it to the document store and returns"
            + " the reference.",
    // Element template conditions have no OR, and "oneOf {SYNC, POLLING}" on input.executionType
    // cannot be used: on the uploaded-document path that property is inactive, so its value is
    // removed from the XML and the condition never matches. The output bucket is active exactly
    // when the execution type is ASYNC, which makes "not active" the negation we need. Revisit
    // this if outputConfigS3Bucket's own condition changes.
    condition =
        @TemplateProperty.PropertyCondition(
            property = "input.outputConfigS3Bucket",
            isActive = false))
public class TextractRequest extends AwsBaseRequest {
  @Valid @NotNull private TextractRequestData input;

  public TextractRequestData getInput() {
    return input;
  }

  public void setInput(TextractRequestData input) {
    this.input = input;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    TextractRequest that = (TextractRequest) o;

    return new EqualsBuilder().appendSuper(super.equals(o)).append(input, that.input).isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37).appendSuper(super.hashCode()).append(input).toHashCode();
  }

  @Override
  public String toString() {
    return "TextractRequest{" + "input=" + input + '}';
  }
}
