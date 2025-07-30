/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.vector.store;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.DropdownItem;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(
    label = "Azure Cosmos DB NoSQL",
    id = AzureCosmosDbNoSqlVectorStore.STORE_AZURE_COSMOS_DB_NO_SQL)
public record AzureCosmosDbNoSqlVectorStore(
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "azureCosmosDbNoSql.endpoint",
            label = "Endpoint",
            description =
                "Specify Azure Cosmos DB endpoint. Details in the <a href=\"https://learn.microsoft.com/en-us/azure/cosmos-db/\" target=\"_blank\">documentation</a>.",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String endpoint,
    @Valid @NotNull AzureAuthentication authentication,
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "azureCosmosDbNoSql.databaseName",
            label = "Database name",
            description = "Specify the name of the Azure Cosmos DB NoSQL database.",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String databaseName,
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "azureCosmosDbNoSql.containerName",
            label = "Container name",
            description = "Specify the name of the Azure Cosmos DB NoSQL container.",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        String containerName,
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "azureCosmosDbNoSql.consistencyLevel",
            label = "Consistency level",
            description = "Specify the consistency level for the Azure Cosmos DB NoSQL store.",
            feel = Property.FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "EVENTUAL",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        ConsistencyLevel consistencyLevel,
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "azureCosmosDbNoSql.distanceFunction",
            label = "Distance function",
            description =
                "The metric used to compute distance/similarity. Details in the <a href=\"https://learn.microsoft.com/en-us/azure/cosmos-db/nosql/vector-search\" target=\"_blank\">documentation</a>.",
            feel = Property.FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "COSINE",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        DistanceFunction distanceFunction,
    @NotBlank
        @TemplateProperty(
            group = "embeddingsStore",
            id = "azureCosmosDbNoSql.vectorIndexType",
            label = "Vector index type",
            description =
                "The type of vector index type to use. Details in the <a href=\"https://learn.microsoft.com/en-us/azure/cosmos-db/nosql/vector-search\" target=\"_blank\">documentation</a>.",
            feel = Property.FeelMode.required,
            type = TemplateProperty.PropertyType.Dropdown,
            defaultValue = "FLAT",
            constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
        IndexType vectorIndexType)
    implements EmbeddingsVectorStore {

  @TemplateProperty(ignore = true)
  public static final String STORE_AZURE_COSMOS_DB_NO_SQL = "STORE_AZURE_COSMOS_DB_NO_SQL";

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(
        value = AzureAuthentication.AzureApiKeyAuthentication.class,
        name = "apiKey"),
    @JsonSubTypes.Type(
        value = AzureAuthentication.AzureClientCredentialsAuthentication.class,
        name = "clientCredentials")
  })
  @TemplateDiscriminatorProperty(
      label = "Authentication",
      group = "embeddingsStore",
      name = "type",
      defaultValue = "apiKey",
      description = "Specify the Azure OpenAI authentication strategy.")
  public sealed interface AzureAuthentication {
    @TemplateSubType(id = "apiKey", label = "API key")
    record AzureApiKeyAuthentication(
        @NotBlank
            @TemplateProperty(
                group = "embeddingsStore",
                label = "API key",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            String apiKey)
        implements AzureAuthentication {

      @Override
      public @NotNull String toString() {
        return "AzureApiKeyAuthentication{apiKey=[REDACTED]}";
      }
    }

    @TemplateSubType(id = "clientCredentials", label = "Client credentials")
    record AzureClientCredentialsAuthentication(
        @NotBlank
            @TemplateProperty(
                group = "embeddingsStore",
                label = "Client ID",
                description = "ID of a Microsoft Entra application",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            String clientId,
        @NotBlank
            @TemplateProperty(
                group = "embeddingsStore",
                label = "Client secret",
                description = "Secret of a Microsoft Entra application",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
            String clientSecret,
        @NotBlank
            @TemplateProperty(
                group = "embeddingsStore",
                label = "Tenant ID",
                description =
                    "ID of a Microsoft Entra tenant. Details in the <a href=\"https://learn.microsoft.com/en-us/entra/fundamentals/how-to-find-tenant\" target=\"_blank\">documentation</a>.",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional)
            String tenantId,
        @TemplateProperty(
                group = "embeddingsStore",
                label = "Authority host",
                description =
                    "Authority host URL for the Microsoft Entra application. Defaults to <code>https://login.microsoftonline.com</code>. This can also contain an OAuth 2.0 token endpoint.",
                type = TemplateProperty.PropertyType.String,
                feel = Property.FeelMode.optional,
                optional = true)
            String authorityHost)
        implements AzureAuthentication {

      @Override
      public String toString() {
        return "AzureClientCredentialsAuthentication{clientId=%s, clientSecret=[REDACTED]}, tenantId=%s, authorityHost=%s}"
            .formatted(clientId, tenantId, authorityHost);
      }
    }
  }

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
