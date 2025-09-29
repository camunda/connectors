/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.model.embedding.models.EmbeddingModelProvider;
import io.camunda.connector.model.embedding.models.OpenAiEmbeddingModelProvider;
import io.camunda.connector.model.embedding.splitter.RecursiveDocumentSplitter;
import io.camunda.connector.model.embedding.vector.store.AzureAiSearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.AzureAuthentication;
import io.camunda.connector.model.embedding.vector.store.ElasticsearchVectorStore;
import io.camunda.connector.model.embedding.vector.store.EmbeddingsVectorStore;
import io.camunda.connector.model.operation.EmbedDocumentOperation;
import io.camunda.connector.model.operation.EmbedDocumentSource;
import io.camunda.connector.model.operation.VectorDatabaseConnectorOperation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Tests for validating {@link EmbeddingsVectorDBRequest}. Note that only a small subset of possible
 * valid and invalid configurations are tested here.
 */
@ExtendWith(SpringExtension.class)
@Import(ValidationAutoConfiguration.class)
class EmbeddingsVectorDBRequestValidationTest {

  @Autowired private Validator validator;

  @Test
  void validationShouldSucceedWhenAllFieldsArePresent() {
    var request =
        new EmbeddingsVectorDBRequest(
            sampleOperation(), sampleModelProvider(), sampleVectorStore());
    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void validationShouldFailWhenOperationIsNull() {
    var request = new EmbeddingsVectorDBRequest(null, sampleModelProvider(), sampleVectorStore());
    var violations = validator.validate(request);
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next())
        .satisfies(
            violation -> {
              assertThat(violation.getMessage()).isEqualTo("must not be null");
              assertThat(violation.getPropertyPath().toString())
                  .isEqualTo("vectorDatabaseConnectorOperation");
            });
  }

  @Test
  void validationShouldFailWhenModelProviderIsNull() {
    var request = new EmbeddingsVectorDBRequest(sampleOperation(), null, sampleVectorStore());
    var violations = validator.validate(request);
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next())
        .satisfies(
            violation -> {
              assertThat(violation.getMessage()).isEqualTo("must not be null");
              assertThat(violation.getPropertyPath().toString())
                  .isEqualTo("embeddingModelProvider");
            });
  }

  @Test
  void validationShouldFailWhenVectorStoreIsNull() {
    var request = new EmbeddingsVectorDBRequest(sampleOperation(), sampleModelProvider(), null);
    var violations = validator.validate(request);
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next())
        .satisfies(
            violation -> {
              assertThat(violation.getMessage()).isEqualTo("must not be null");
              assertThat(violation.getPropertyPath().toString()).isEqualTo("vectorStore");
            });
  }

  @Test
  void validationShouldFailWhenOperationIsInvalid() {
    var request =
        new EmbeddingsVectorDBRequest(
            new EmbedDocumentOperation(
                EmbedDocumentSource.PlainText,
                "document content",
                List.of(),
                new RecursiveDocumentSplitter(-1, 0)),
            sampleModelProvider(),
            sampleVectorStore());
    var violations = validator.validate(request);
    assertThat(violations).hasSize(2);
    assertThat(violations)
        .anySatisfy(
            violation -> {
              assertThat(violation.getMessage()).isEqualTo("must be greater than or equal to 1");
              assertThat(violation.getPropertyPath().toString()).contains("maxSegmentSizeInChars");
            });
    assertThat(violations)
        .anySatisfy(
            violation -> {
              assertThat(violation.getMessage()).isEqualTo("must be greater than or equal to 1");
              assertThat(violation.getPropertyPath().toString()).contains("maxOverlapSizeInChars");
            });
  }

  @Test
  void validationShouldFailWhenModelProviderIsInvalid() {
    var request =
        new EmbeddingsVectorDBRequest(
            sampleOperation(),
            new OpenAiEmbeddingModelProvider(
                new OpenAiEmbeddingModelProvider.Configuration(
                    "", null, null, null, null, null, emptyMap(), null)),
            sampleVectorStore());
    var violations = validator.validate(request);
    assertThat(violations).hasSize(2);
    assertThat(violations)
        .anySatisfy(
            violation -> {
              assertThat(violation.getMessage()).isEqualTo("must not be blank");
              assertThat(violation.getPropertyPath().toString()).contains("apiKey");
            });
    assertThat(violations)
        .anySatisfy(
            violation -> {
              assertThat(violation.getMessage()).isEqualTo("must not be blank");
              assertThat(violation.getPropertyPath().toString()).contains("modelName");
            });
  }

  @Test
  void validationShouldFailWhenVectorStoreIsInvalid() {
    var request =
        new EmbeddingsVectorDBRequest(
            sampleOperation(),
            sampleModelProvider(),
            new AzureAiSearchVectorStore(
                new AzureAiSearchVectorStore.Configuration(
                    null, new AzureAuthentication.AzureApiKeyAuthentication(null), " ")));
    var violations = validator.validate(request);
    assertThat(violations).hasSize(3);
    assertThat(violations)
        .anySatisfy(
            violation -> {
              assertThat(violation.getMessage()).isEqualTo("must not be blank");
              assertThat(violation.getPropertyPath().toString()).contains("endpoint");
            });
    assertThat(violations)
        .anySatisfy(
            violation -> {
              assertThat(violation.getMessage()).isEqualTo("must not be blank");
              assertThat(violation.getPropertyPath().toString()).contains("apiKey");
            });
    assertThat(violations)
        .anySatisfy(
            violation -> {
              assertThat(violation.getMessage()).isEqualTo("must not be blank");
              assertThat(violation.getPropertyPath().toString()).contains("indexName");
            });
  }

  private VectorDatabaseConnectorOperation sampleOperation() {
    return new EmbedDocumentOperation(
        EmbedDocumentSource.PlainText,
        "document content",
        List.of(),
        new RecursiveDocumentSplitter(100, 10));
  }

  private EmbeddingModelProvider sampleModelProvider() {
    return new OpenAiEmbeddingModelProvider(
        new OpenAiEmbeddingModelProvider.Configuration(
            "API_KEY", "MODEL_NAME", null, null, null, null, emptyMap(), null));
  }

  private EmbeddingsVectorStore sampleVectorStore() {
    return new ElasticsearchVectorStore(
        new ElasticsearchVectorStore.Configuration(
            "http://localhost:9200", "username", "password", "index-name"));
  }
}
