/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.vector.store;

import io.camunda.connector.generator.java.annotation.DropdownItem;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(
    label = "Azure Cosmos DB NoSQL",
    id = AzureCosmosDbNoSqlVectorStore.AZURE_COSMOS_DB_NO_SQL_STORE)
public record AzureCosmosDbNoSqlVectorStore(@Valid @NotNull Configuration azureCosmosDbNoSql)
    implements EmbeddingsVectorStore {

  @TemplateProperty(ignore = true)
  public static final String AZURE_COSMOS_DB_NO_SQL_STORE = "azureCosmosDbNoSqlStore";

  public record Configuration(
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Endpoint",
              description =
                  "Specify Azure Cosmos DB endpoint. Details in the <a href=\"https://learn.microsoft.com/en-us/azure/cosmos-db/\" target=\"_blank\">documentation</a>.",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String endpoint,
      @Valid @NotNull AzureAuthentication authentication,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Database name",
              description = "Specify the name of the Azure Cosmos DB NoSQL database.",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String databaseName,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Container name",
              description = "Specify the name of the Azure Cosmos DB NoSQL container.",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          String containerName,
      @NotNull
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Consistency level",
              description = "Specify the consistency level for the Azure Cosmos DB NoSQL store.",
              feel = FeelMode.required,
              type = TemplateProperty.PropertyType.Dropdown,
              defaultValue = "EVENTUAL")
          ConsistencyLevel consistencyLevel,
      @NotNull
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Distance function",
              description =
                  "The metric used to compute distance/similarity. Details in the <a href=\"https://learn.microsoft.com/en-us/azure/cosmos-db/nosql/vector-search\" target=\"_blank\">documentation</a>.",
              feel = FeelMode.required,
              type = TemplateProperty.PropertyType.Dropdown,
              defaultValue = "COSINE")
          DistanceFunction distanceFunction,
      @NotNull
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Vector index type",
              description =
                  "The type of vector index type to use. Details in the <a href=\"https://learn.microsoft.com/en-us/azure/cosmos-db/nosql/vector-search\" target=\"_blank\">documentation</a>.",
              feel = FeelMode.required,
              type = TemplateProperty.PropertyType.Dropdown,
              defaultValue = "FLAT")
          IndexType vectorIndexType) {}

  public enum ConsistencyLevel {
    @DropdownItem(label = "Strong")
    STRONG,
    @DropdownItem(label = "Bounded Staleness")
    BOUNDED_STALENESS,
    @DropdownItem(label = "Session")
    SESSION,
    @DropdownItem(label = "Consistent Prefix")
    CONSISTENT_PREFIX,
    @DropdownItem(label = "Eventual")
    EVENTUAL
  }

  public enum DistanceFunction {
    @DropdownItem(label = "Euclidean")
    EUCLIDEAN,
    @DropdownItem(label = "Cosine")
    COSINE,
    @DropdownItem(label = "Dot Product")
    DOT_PRODUCT
  }

  public enum IndexType {
    @DropdownItem(label = "Flat")
    FLAT,
    @DropdownItem(label = "Quantized Flat")
    QUANTIZED_FLAT,
    @DropdownItem(label = "DiskANN")
    DISK_ANN
  }
}
