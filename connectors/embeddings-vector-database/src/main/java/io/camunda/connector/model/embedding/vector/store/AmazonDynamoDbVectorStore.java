/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.vector.store;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@TemplateSubType(label = "Amazon DynamoDB", id = AmazonDynamoDbVectorStore.AMAZON_DYNAMODB_STORE)
public record AmazonDynamoDbVectorStore(@Valid @NotNull Configuration amazonDynamoDb)
    implements EmbeddingsVectorStore {

  @TemplateProperty(ignore = true)
  public static final String AMAZON_DYNAMODB_STORE = "amazonDynamoDbStore";

  @TemplateProperty(ignore = true)
  private static final String NAME_PATTERN = "^[a-zA-Z0-9_.-]{3,255}$";

  @TemplateProperty(ignore = true)
  private static final String NAME_MESSAGE =
      "must be 3 to 255 letters, digits, underscores, hyphens or dots";

  public record Configuration(
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Access key",
              secret = true,
              tooltip =
                  "IAM access key of a user allowed to call dynamodb:DescribeTable, BatchWriteItem"
                      + " and SearchVectors, plus CreateTable if the connector should create the"
                      + " table")
          String accessKey,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Secret key",
              secret = true,
              tooltip = "Secret key associated with the access key")
          String secretKey,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Region",
              tooltip = "AWS region of the table",
              placeholder = "us-east-1")
          String region,
      @NotBlank
          @Pattern(regexp = NAME_PATTERN, message = NAME_MESSAGE)
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Table name",
              tooltip =
                  "Table that stores the embeddings. If it does not exist, it is created on the"
                      + " first embed in on-demand capacity mode, together with the vector index."
                      + " An existing table must have a string partition key and no sort key.",
              placeholder = "embeddings")
          String tableName,
      @NotBlank
          @Pattern(regexp = NAME_PATTERN, message = NAME_MESSAGE)
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Vector index name",
              description =
                  "Note: DynamoDB indexes new embeddings asynchronously, so they may not be"
                      + " returned by a retrieve that runs immediately after the embed.",
              tooltip =
                  "Vector index used for similarity search. It must project the '_page_content'"
                      + " attribute, which holds the chunk text. When the table is created by the"
                      + " connector, the index uses cosine distance and the embedding model's"
                      + " dimensions.",
              placeholder = "embeddings-index")
          String indexName) {

    @Override
    public String toString() {
      return "Configuration(accessKey=[REDACTED], secretKey=[REDACTED], region='%s', tableName='%s', indexName='%s')"
          .formatted(region, tableName, indexName);
    }
  }
}
