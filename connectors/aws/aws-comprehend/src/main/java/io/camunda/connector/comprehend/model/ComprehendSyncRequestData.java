/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
@TemplateSubType(id = "sync", label = "Sync")
public record ComprehendSyncRequestData(
    @TemplateProperty(
            group = "input",
            label = "Text",
            description =
                "<a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_ClassifyDocument.html#comprehend-ClassifyDocument-request-Text\">Text</a> to be analyzed.")
        @NotNull
        String text,
    @TemplateProperty(
            group = "input",
            label = "Endpoint's ARN",
            description =
                "<a href=\"https://docs.aws.amazon.com/comprehend/latest/APIReference/API_ClassifyDocument.html#comprehend-ClassifyDocument-request-EndpointArn\">ARN of Endpoint.</a>")
        @NotNull
        String endpointArn)
    implements ComprehendRequestData {
  @Override
  public String toString() {
    return "ComprehendSyncRequestData{"
        + "text='"
        + text
        + '\''
        + ", endpointArn='"
        + endpointArn
        + '\''
        + '}';
  }
}
