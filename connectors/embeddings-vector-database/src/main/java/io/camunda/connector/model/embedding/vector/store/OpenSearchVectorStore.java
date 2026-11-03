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

@TemplateSubType(label = "OpenSearch", id = OpenSearchVectorStore.OPEN_SEARCH_STORE)
public record OpenSearchVectorStore(@Valid @NotNull Configuration openSearch)
    implements EmbeddingsVectorStore {

  @TemplateProperty(ignore = true)
  public static final String OPEN_SEARCH_STORE = "openSearchStore";

  public record Configuration(
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Base URL",
              description = "OpenSearch base URL, i.e. http(s)://host:port")
          String baseUrl,
      // Declared before the inline username/password fallback fields it gates below.
      @FEEL
          @Valid
          @TemplateProperty(
              group = "embeddingsStore",
              label = "OpenSearch credential",
              type = TemplateProperty.PropertyType.Configuration,
              optional = true,
              description =
                  "Choose a reusable OpenSearch credential, or configure a one-time username and password below.")
          OpenSearchCredential openSearchCredential,
      @TemplateProperty(
              group = "embeddingsStore",
              label = "Username",
              description = "OpenSearch username",
              constraints = @PropertyConstraints(notEmpty = true),
              condition =
                  @PropertyCondition(
                      property = "vectorStore.openSearch.openSearchCredential",
                      isEmpty = NullableBoolean.TRUE))
          String userName,
      @TemplateProperty(
              group = "embeddingsStore",
              label = "Password",
              description = "OpenSearch password",
              constraints = @PropertyConstraints(notEmpty = true),
              condition =
                  @PropertyCondition(
                      property = "vectorStore.openSearch.openSearchCredential",
                      isEmpty = NullableBoolean.TRUE))
          String password,
      @NotBlank
          @TemplateProperty(
              group = "embeddingsStore",
              label = "Index name",
              description = "OpenSearch index")
          String indexName) {

    /** The bound credential's username takes precedence; the inline userName is the fallback. */
    @Override
    public String userName() {
      return openSearchCredential != null ? openSearchCredential.userName() : userName;
    }

    /** The bound credential's password takes precedence; the inline password is the fallback. */
    @Override
    public String password() {
      return openSearchCredential != null ? openSearchCredential.password() : password;
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
