/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.model.embedding.models.AzureOpenAiEmbeddingCredential;
import io.camunda.connector.model.embedding.models.AzureOpenAiEmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.BedrockEmbeddingCredential;
import io.camunda.connector.model.embedding.models.BedrockEmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.GoogleVertexAiEmbeddingCredential;
import io.camunda.connector.model.embedding.models.GoogleVertexAiEmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.GoogleVertexAiEmbeddingModelProvider.GoogleVertexAiAuthentication;
import io.camunda.connector.model.embedding.models.OpenAiEmbeddingCredential;
import io.camunda.connector.model.embedding.models.OpenAiEmbeddingModelProvider;
import io.camunda.connector.model.embedding.vector.store.AmazonManagedOpenSearchCredential;
import io.camunda.connector.model.embedding.vector.store.AmazonManagedOpenSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.AzureAiSearchCredential;
import io.camunda.connector.model.embedding.vector.store.AzureAiSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.AzureAuthentication;
import io.camunda.connector.model.embedding.vector.store.AzureCosmosDbNoSqlCredential;
import io.camunda.connector.model.embedding.vector.store.AzureCosmosDbNoSqlVectorStore;
import io.camunda.connector.model.embedding.vector.store.ElasticsearchCredential;
import io.camunda.connector.model.embedding.vector.store.ElasticsearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.OpenSearchCredential;
import io.camunda.connector.model.embedding.vector.store.OpenSearchVectorStore;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Covers the reusable-credential chooser on every embedding-model provider and vector store: the
 * bound credential's secret takes precedence over the inline field, the inline field is the
 * fallback when no credential is bound, and binding fails with a message naming both sources when
 * neither supplies the secret.
 */
class EmbeddingsCredentialChooserTest {

  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  @Nested
  class OpenAi {
    @Test
    void credentialWinsOverInline() {
      var cfg =
          new OpenAiEmbeddingModelProvider.Configuration(
              new OpenAiEmbeddingCredential("CRED_KEY"),
              "INLINE_KEY",
              "model",
              null,
              null,
              null,
              null,
              null,
              null);
      assertThat(cfg.apiKey()).isEqualTo("CRED_KEY");
    }

    @Test
    void inlineUsedWhenNoCredential() {
      var cfg =
          new OpenAiEmbeddingModelProvider.Configuration(
              null, "INLINE_KEY", "model", null, null, null, null, null, null);
      assertThat(cfg.apiKey()).isEqualTo("INLINE_KEY");
    }

    @Test
    void failsWhenNeitherPresent() {
      var cfg =
          new OpenAiEmbeddingModelProvider.Configuration(
              null, null, "model", null, null, null, null, null, null);
      assertThat(messages(cfg))
          .contains("No API key provided by the credential or the element template");
    }
  }

  @Nested
  class Bedrock {
    @Test
    void credentialWinsOverInline() {
      var cfg =
          new BedrockEmbeddingModelProvider.Configuration(
              new BedrockEmbeddingCredential("CRED_AK", "CRED_SK"),
              "INLINE_AK",
              "INLINE_SK",
              "us-east-1",
              null,
              null,
              null,
              null,
              null);
      assertThat(cfg.accessKey()).isEqualTo("CRED_AK");
      assertThat(cfg.secretKey()).isEqualTo("CRED_SK");
    }

    @Test
    void inlineUsedWhenNoCredential() {
      var cfg =
          new BedrockEmbeddingModelProvider.Configuration(
              null, "INLINE_AK", "INLINE_SK", "us-east-1", null, null, null, null, null);
      assertThat(cfg.accessKey()).isEqualTo("INLINE_AK");
      assertThat(cfg.secretKey()).isEqualTo("INLINE_SK");
    }

    @Test
    void failsWhenNeitherPresent() {
      var cfg =
          new BedrockEmbeddingModelProvider.Configuration(
              null, null, null, "us-east-1", null, null, null, null, null);
      assertThat(messages(cfg))
          .contains(
              "No access key provided by the credential or the element template",
              "No secret key provided by the credential or the element template");
    }
  }

  @Nested
  class AzureOpenAi {
    @Test
    void credentialWinsOverInline() {
      var cfg =
          new AzureOpenAiEmbeddingModelProvider.Configuration(
              "https://endpoint",
              new AzureOpenAiEmbeddingCredential(
                  new AzureOpenAiEmbeddingModelProvider.AzureAuthentication
                      .AzureApiKeyAuthentication("CRED_KEY")),
              new AzureOpenAiEmbeddingModelProvider.AzureAuthentication.AzureApiKeyAuthentication(
                  "INLINE_KEY"),
              "deployment",
              null,
              null,
              null);
      assertThat(cfg.authentication())
          .isInstanceOf(
              AzureOpenAiEmbeddingModelProvider.AzureAuthentication.AzureApiKeyAuthentication.class)
          .extracting(
              a ->
                  ((AzureOpenAiEmbeddingModelProvider.AzureAuthentication.AzureApiKeyAuthentication)
                          a)
                      .apiKey())
          .isEqualTo("CRED_KEY");
    }

    @Test
    void inlineUsedWhenNoCredential() {
      var cfg =
          new AzureOpenAiEmbeddingModelProvider.Configuration(
              "https://endpoint",
              null,
              new AzureOpenAiEmbeddingModelProvider.AzureAuthentication.AzureApiKeyAuthentication(
                  "INLINE_KEY"),
              "deployment",
              null,
              null,
              null);
      assertThat(cfg.getInlineAuthenticationWhenNoCredentialBound()).isNotNull();
      assertThat(cfg.authentication()).isNotNull();
    }

    @Test
    void failsWhenNeitherPresent() {
      var cfg =
          new AzureOpenAiEmbeddingModelProvider.Configuration(
              "https://endpoint", null, null, "deployment", null, null, null);
      assertThat(messages(cfg))
          .contains("No authentication provided by the credential or the element template");
    }

    @Test
    void leftoverInlineDiscriminatorIgnoredWhenCredentialBound() {
      // Modeler emits an inline discriminator unconditionally even when a credential is bound; its
      // secret subfield is hidden/absent. The inline value must not be validated in that case.
      var cfg =
          new AzureOpenAiEmbeddingModelProvider.Configuration(
              "https://endpoint",
              new AzureOpenAiEmbeddingCredential(
                  new AzureOpenAiEmbeddingModelProvider.AzureAuthentication
                      .AzureApiKeyAuthentication("CRED_KEY")),
              new AzureOpenAiEmbeddingModelProvider.AzureAuthentication.AzureApiKeyAuthentication(
                  null),
              "deployment",
              null,
              null,
              null);
      assertThat(cfg.getInlineAuthenticationWhenNoCredentialBound()).isNull();
      assertThat(messages(cfg)).noneMatch(m -> m.equals("must not be blank"));
    }
  }

  @Nested
  class GoogleVertexAi {
    @Test
    void credentialWinsOverInline() {
      var cfg =
          new GoogleVertexAiEmbeddingModelProvider.Configuration(
              "project",
              "region",
              new GoogleVertexAiEmbeddingCredential(
                  new GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication(
                      "CRED_JSON")),
              new GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication(
                  "INLINE_JSON"),
              "model",
              1,
              null,
              null);
      assertThat(cfg.authentication())
          .asInstanceOf(
              org.assertj.core.api.InstanceOfAssertFactories.type(
                  GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication.class))
          .extracting(GoogleVertexAiAuthentication.ServiceAccountCredentialsAuthentication::jsonKey)
          .isEqualTo("CRED_JSON");
    }

    @Test
    void failsWhenNeitherPresent() {
      var cfg =
          new GoogleVertexAiEmbeddingModelProvider.Configuration(
              "project", "region", null, null, "model", 1, null, null);
      assertThat(messages(cfg))
          .contains("No authentication provided by the credential or the element template");
    }
  }

  @Nested
  class AmazonManagedOpenSearch {
    @Test
    void credentialWinsOverInline() {
      var cfg =
          new AmazonManagedOpenSearchVectorStore.Configuration(
              new AmazonManagedOpenSearchCredential("CRED_AK", "CRED_SK"),
              "INLINE_AK",
              "INLINE_SK",
              "https://server",
              "us-east-1",
              "index");
      assertThat(cfg.accessKey()).isEqualTo("CRED_AK");
      assertThat(cfg.secretKey()).isEqualTo("CRED_SK");
    }

    @Test
    void failsWhenNeitherPresent() {
      var cfg =
          new AmazonManagedOpenSearchVectorStore.Configuration(
              null, null, null, "https://server", "us-east-1", "index");
      assertThat(messages(cfg))
          .contains(
              "No access key provided by the credential or the element template",
              "No secret key provided by the credential or the element template");
    }
  }

  @Nested
  class OpenSearch {
    @Test
    void credentialWinsOverInline() {
      var cfg =
          new OpenSearchVectorStore.Configuration(
              "http://localhost:9200",
              new OpenSearchCredential("CRED_USER", "CRED_PW"),
              "INLINE_USER",
              "INLINE_PW",
              "index");
      assertThat(cfg.userName()).isEqualTo("CRED_USER");
      assertThat(cfg.password()).isEqualTo("CRED_PW");
    }

    @Test
    void inlineUsedWhenNoCredential() {
      var cfg =
          new OpenSearchVectorStore.Configuration(
              "http://localhost:9200", null, "INLINE_USER", "INLINE_PW", "index");
      assertThat(cfg.userName()).isEqualTo("INLINE_USER");
      assertThat(cfg.password()).isEqualTo("INLINE_PW");
    }

    @Test
    void failsWhenNeitherPresent() {
      var cfg =
          new OpenSearchVectorStore.Configuration(
              "http://localhost:9200", null, null, null, "index");
      assertThat(messages(cfg))
          .contains(
              "No username provided by the credential or the element template",
              "No password provided by the credential or the element template");
    }
  }

  @Nested
  class Elasticsearch {
    @Test
    void credentialWinsOverInline() {
      var cfg =
          new ElasticsearchVectorStore.Configuration(
              "http://localhost:9200",
              new ElasticsearchCredential("CRED_USER", "CRED_PW"),
              "INLINE_USER",
              "INLINE_PW",
              "index");
      assertThat(cfg.userName()).isEqualTo("CRED_USER");
      assertThat(cfg.password()).isEqualTo("CRED_PW");
    }

    @Test
    void failsWhenNeitherPresent() {
      var cfg =
          new ElasticsearchVectorStore.Configuration(
              "http://localhost:9200", null, null, null, "index");
      assertThat(messages(cfg))
          .contains(
              "No username provided by the credential or the element template",
              "No password provided by the credential or the element template");
    }
  }

  @Nested
  class AzureAiSearch {
    @Test
    void credentialWinsOverInline() {
      var cfg =
          new AzureAiSearchVectorStore.Configuration(
              "https://endpoint",
              new AzureAiSearchCredential(
                  new AzureAuthentication.AzureApiKeyAuthentication("CRED_KEY")),
              new AzureAuthentication.AzureApiKeyAuthentication("INLINE_KEY"),
              "index");
      assertThat(cfg.authentication())
          .asInstanceOf(
              org.assertj.core.api.InstanceOfAssertFactories.type(
                  AzureAuthentication.AzureApiKeyAuthentication.class))
          .extracting(AzureAuthentication.AzureApiKeyAuthentication::apiKey)
          .isEqualTo("CRED_KEY");
    }

    @Test
    void failsWhenNeitherPresent() {
      var cfg = new AzureAiSearchVectorStore.Configuration("https://endpoint", null, null, "index");
      assertThat(messages(cfg))
          .contains("No authentication provided by the credential or the element template");
    }
  }

  @Nested
  class AzureCosmosDb {
    @Test
    void credentialWinsOverInline() {
      var cfg =
          new AzureCosmosDbNoSqlVectorStore.Configuration(
              "https://endpoint",
              new AzureCosmosDbNoSqlCredential(
                  new AzureAuthentication.AzureApiKeyAuthentication("CRED_KEY")),
              new AzureAuthentication.AzureApiKeyAuthentication("INLINE_KEY"),
              "db",
              "container",
              AzureCosmosDbNoSqlVectorStore.ConsistencyLevel.EVENTUAL,
              AzureCosmosDbNoSqlVectorStore.DistanceFunction.COSINE,
              AzureCosmosDbNoSqlVectorStore.IndexType.FLAT);
      assertThat(cfg.authentication())
          .asInstanceOf(
              org.assertj.core.api.InstanceOfAssertFactories.type(
                  AzureAuthentication.AzureApiKeyAuthentication.class))
          .extracting(AzureAuthentication.AzureApiKeyAuthentication::apiKey)
          .isEqualTo("CRED_KEY");
    }

    @Test
    void failsWhenNeitherPresent() {
      var cfg =
          new AzureCosmosDbNoSqlVectorStore.Configuration(
              "https://endpoint",
              null,
              null,
              "db",
              "container",
              AzureCosmosDbNoSqlVectorStore.ConsistencyLevel.EVENTUAL,
              AzureCosmosDbNoSqlVectorStore.DistanceFunction.COSINE,
              AzureCosmosDbNoSqlVectorStore.IndexType.FLAT);
      assertThat(messages(cfg))
          .contains("No authentication provided by the credential or the element template");
    }
  }

  private static <T> Set<String> messages(T bean) {
    return VALIDATOR.validate(bean).stream()
        .map(ConstraintViolation::getMessage)
        .collect(java.util.stream.Collectors.toSet());
  }
}
