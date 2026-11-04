/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.model.embedding.models.AzureOpenAiEmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.GoogleVertexAiEmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.GoogleVertexAiEmbeddingModelProvider.GoogleVertexAiAuthentication;
import io.camunda.connector.model.embedding.vector.store.AzureAiSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.AzureAuthentication;
import io.camunda.connector.model.embedding.vector.store.AzureCosmosDbNoSqlVectorStore;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Binds real JSON-shaped variables, as the runtime does, to cover the authentication discriminators
 * that direct record construction cannot reach: a Modeler diagram keeps a leftover inline
 * discriminator when a credential is bound, and the inline value is the fallback otherwise.
 */
class EmbeddingsCredentialBindingTest {

  private static final Map<String, Object> OPERATION =
      Map.of(
          "operationType", "retrieveDocumentOperation",
          "query", "question",
          "documentLimit", 5);

  private static final Map<String, Object> OPEN_AI_MODEL =
      Map.of(
          "modelProvider",
          "openAiModelProvider",
          "openAi",
          Map.of("apiKey", "key", "modelName", "text-embedding-3-small"));

  private static final Map<String, Object> ELASTICSEARCH_STORE =
      Map.of(
          "storeType",
          "elasticsearchStore",
          "elasticsearch",
          Map.of("baseUrl", "https://es", "userName", "u", "password", "p", "indexName", "index"));

  private static final Map<String, Object> API_KEY_CREDENTIAL =
      Map.of("authentication", Map.of("type", "apiKey", "apiKey", "CRED_KEY"));

  // what the Modeler leaves behind when the user never touched the inline authentication
  private static final Map<String, Object> LEFTOVER_API_KEY = Map.of("type", "apiKey");

  private static final Map<String, Object> INLINE_API_KEY =
      Map.of("type", "apiKey", "apiKey", "INLINE_KEY");

  private static EmbeddingsVectorDBRequest bind(
      Map<String, Object> model, Map<String, Object> store) {
    var variables = new HashMap<String, Object>();
    variables.put("vectorDatabaseConnectorOperation", OPERATION);
    variables.put("embeddingModelProvider", model);
    variables.put("vectorStore", store);
    return OutboundConnectorContextBuilder.create()
        .variables(variables)
        .build()
        .bindVariables(EmbeddingsVectorDBRequest.class);
  }

  private static Map<String, Object> azureOpenAiModel(
      Map<String, Object> credential, Map<String, Object> authentication) {
    var config = new HashMap<String, Object>();
    config.put("endpoint", "https://endpoint");
    config.put("deploymentName", "deployment");
    putIfNotNull(config, "azureOpenAiCredential", credential);
    putIfNotNull(config, "authentication", authentication);
    return Map.of("modelProvider", "azureOpenAiModelProvider", "azureOpenAi", config);
  }

  private static Map<String, Object> vertexModel(
      Map<String, Object> credential, Map<String, Object> authentication) {
    var config = new HashMap<String, Object>();
    config.put("projectId", "project");
    config.put("region", "region");
    config.put("modelName", "model");
    config.put("dimensions", 768);
    putIfNotNull(config, "googleVertexAiCredential", credential);
    putIfNotNull(config, "authentication", authentication);
    return Map.of("modelProvider", "vertexAiModelProvider", "googleVertexAi", config);
  }

  private static Map<String, Object> aiSearchStore(
      Map<String, Object> credential, Map<String, Object> authentication) {
    var config = new HashMap<String, Object>();
    config.put("endpoint", "https://search");
    config.put("indexName", "index");
    putIfNotNull(config, "aiSearchCredential", credential);
    putIfNotNull(config, "authentication", authentication);
    return Map.of("storeType", "azureAiSearchStore", "aiSearch", config);
  }

  private static Map<String, Object> cosmosStore(
      Map<String, Object> credential, Map<String, Object> authentication) {
    var config = new HashMap<String, Object>();
    config.put("endpoint", "https://cosmos");
    config.put("databaseName", "db");
    config.put("containerName", "container");
    config.put("consistencyLevel", "EVENTUAL");
    config.put("distanceFunction", "COSINE");
    config.put("vectorIndexType", "FLAT");
    putIfNotNull(config, "azureCosmosDbNoSqlCredential", credential);
    putIfNotNull(config, "authentication", authentication);
    return Map.of("storeType", "azureCosmosDbNoSqlStore", "azureCosmosDbNoSql", config);
  }

  private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  private static String apiKey(Object authentication) {
    return switch (authentication) {
      case AzureAuthentication.AzureApiKeyAuthentication a -> a.apiKey();
      case AzureOpenAiEmbeddingModelProvider.AzureAuthentication.AzureApiKeyAuthentication a ->
          a.apiKey();
      default -> throw new AssertionError("not an API key authentication: " + authentication);
    };
  }

  @Test
  void azureOpenAiCredentialWinsOverLeftoverInlineDiscriminator() {
    var request = bind(azureOpenAiModel(API_KEY_CREDENTIAL, LEFTOVER_API_KEY), ELASTICSEARCH_STORE);

    var config =
        ((AzureOpenAiEmbeddingModelProvider) request.embeddingModelProvider()).azureOpenAi();
    assertThat(apiKey(config.authentication())).isEqualTo("CRED_KEY");
  }

  @Test
  void azureOpenAiInlineUsedWhenNoCredential() {
    var request = bind(azureOpenAiModel(null, INLINE_API_KEY), ELASTICSEARCH_STORE);

    var config =
        ((AzureOpenAiEmbeddingModelProvider) request.embeddingModelProvider()).azureOpenAi();
    assertThat(apiKey(config.authentication())).isEqualTo("INLINE_KEY");
  }

  @Test
  void vertexAiCredentialWinsOverLeftoverInlineDiscriminator() {
    var credential =
        Map.<String, Object>of(
            "authentication", Map.of("type", "serviceAccountCredentials", "jsonKey", "CRED_JSON"));
    var leftover = Map.<String, Object>of("type", "serviceAccountCredentials");

    var request = bind(vertexModel(credential, leftover), ELASTICSEARCH_STORE);

    var config =
        ((GoogleVertexAiEmbeddingModelProvider) request.embeddingModelProvider()).googleVertexAi();
    assertThat(config.authentication())
        .isInstanceOfSatisfying(
            GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication.class,
            a -> assertThat(a.jsonKey()).isEqualTo("CRED_JSON"));
  }

  @Test
  void vertexAiInlineUsedWhenNoCredential() {
    var inline =
        Map.<String, Object>of("type", "serviceAccountCredentials", "jsonKey", "INLINE_JSON");

    var request = bind(vertexModel(null, inline), ELASTICSEARCH_STORE);

    var config =
        ((GoogleVertexAiEmbeddingModelProvider) request.embeddingModelProvider()).googleVertexAi();
    assertThat(config.authentication())
        .isInstanceOfSatisfying(
            GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication.class,
            a -> assertThat(a.jsonKey()).isEqualTo("INLINE_JSON"));
  }

  @Test
  void azureAiSearchCredentialWinsOverLeftoverInlineDiscriminator() {
    var request = bind(OPEN_AI_MODEL, aiSearchStore(API_KEY_CREDENTIAL, LEFTOVER_API_KEY));

    var config = ((AzureAiSearchVectorStore) request.vectorStore()).aiSearch();
    assertThat(apiKey(config.authentication())).isEqualTo("CRED_KEY");
  }

  @Test
  void azureAiSearchInlineUsedWhenNoCredential() {
    var request = bind(OPEN_AI_MODEL, aiSearchStore(null, INLINE_API_KEY));

    var config = ((AzureAiSearchVectorStore) request.vectorStore()).aiSearch();
    assertThat(apiKey(config.authentication())).isEqualTo("INLINE_KEY");
  }

  @Test
  void cosmosDbCredentialWinsOverLeftoverInlineDiscriminator() {
    var request = bind(OPEN_AI_MODEL, cosmosStore(API_KEY_CREDENTIAL, LEFTOVER_API_KEY));

    var config = ((AzureCosmosDbNoSqlVectorStore) request.vectorStore()).azureCosmosDbNoSql();
    assertThat(apiKey(config.authentication())).isEqualTo("CRED_KEY");
  }

  @Test
  void cosmosDbInlineUsedWhenNoCredential() {
    var request = bind(OPEN_AI_MODEL, cosmosStore(null, INLINE_API_KEY));

    var config = ((AzureCosmosDbNoSqlVectorStore) request.vectorStore()).azureCosmosDbNoSql();
    assertThat(apiKey(config.authentication())).isEqualTo("INLINE_KEY");
  }
}
