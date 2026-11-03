/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.vector.store;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@TemplateSubType(label = "Elasticsearch", id = ElasticsearchVectorStore.ELASTICSEARCH_STORE)
public record ElasticsearchVectorStore(@Valid @NotNull Configuration elasticsearch)
    implements EmbeddingsVectorStore {

  @TemplateProperty(ignore = true)
  public static final String ELASTICSEARCH_STORE = "elasticsearchStore";

  public record Configuration(
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Base URL",
              description = "Elasticsearch base URL, i.e. http(s)://host:port")
          String baseUrl,
      // Declared before the inline username/password fallback fields it gates below.
      @FEEL
          @Valid
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Elasticsearch credential",
              type = TemplateProperty.PropertyType.Configuration,
              optional = true,
              description =
                  "Choose a reusable Elasticsearch credential, or configure a one-time username and password below.")
          ElasticsearchCredential elasticsearchCredential,
      @TemplateProperty(
              group = "embeddingsStore",
              label = "Username",
              description = "Elasticsearch username",
              constraints = @PropertyConstraints(notEmpty = true),
              condition =
                  @PropertyCondition(
                      property = "vectorStore.elasticsearch.elasticsearchCredential",
                      isEmpty = NullableBoolean.TRUE))
          String userName,
      @TemplateProperty(
              group = "embeddingsStore",
              label = "Password",
              description = "Elasticsearch password",
              constraints = @PropertyConstraints(notEmpty = true),
              condition =
                  @PropertyCondition(
                      property = "vectorStore.elasticsearch.elasticsearchCredential",
                      isEmpty = NullableBoolean.TRUE))
          String password,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Index name",
              description = "Elasticsearch index")
          String indexName) {

    /** The bound credential's username takes precedence; the inline userName is the fallback. */
    @Override
    public String userName() {
      return elasticsearchCredential != null ? elasticsearchCredential.userName() : userName;
    }

    /** The bound credential's password takes precedence; the inline password is the fallback. */
    @Override
    public String password() {
      return elasticsearchCredential != null ? elasticsearchCredential.password() : password;
    }

    @AssertTrue(message = "No username provided by the credential or the element template")
    @JsonIgnore
    public boolean isUserNamePresent() {
      return userName() != null && !userName().isBlank();
    }

    @AssertTrue(message = "No password provided by the credential or the element template")
    @JsonIgnore
    public boolean isPasswordPresent() {
      return password() != null && !password().isBlank();
    }

    @Override
    public String toString() {
      return "Configuration{baseUrl='%s', userName='%s', password='[REDACTED]', indexName='%s'}"
          .formatted(baseUrl, userName, indexName);
    }
  }
}
