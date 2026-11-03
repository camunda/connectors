/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.vector.store;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

/** Reusable, secrets-only credential for the Amazon DynamoDB vector store. */
@Configuration(
    id = "io.camunda.connectors:embeddings-amazon-dynamodb-credential:1",
    version = 1,
    name = "Amazon DynamoDB credential")
public record AmazonDynamoDbCredential(
    @NotBlank
        @TemplateProperty(
            secret = true,
            group = "authentication",
            label = "Access key",
            tooltip = ACCESS_KEY_TOOLTIP)
        String accessKey,
    @NotBlank
        @TemplateProperty(
            secret = true,
            group = "authentication",
            label = "Secret key",
            tooltip = "Secret key associated with the access key")
        String secretKey) {

  @TemplateProperty(ignore = true)
  static final String ACCESS_KEY_TOOLTIP =
      "IAM access key of a user allowed to call dynamodb:DescribeTable, BatchWriteItem and SearchVectors,"
          + " plus CreateTable if the connector should create the table";

  @Override
  public String toString() {
    return "AmazonDynamoDbCredential{accessKey=[REDACTED], secretKey=[REDACTED]}";
  }
}
