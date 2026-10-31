/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.model.embedding.models;

import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;

/** Reusable, secrets-only credential for the AWS Bedrock embedding model provider. */
@Configuration(
    id = "io.camunda.connectors:embeddings-bedrock-credential:1",
    version = 1,
    name = "AWS Bedrock embedding credential")
public record BedrockEmbeddingCredential(
    @NotBlank
        @TemplateProperty(
            secret = true,
            group = "authentication",
            label = "Access key",
            description =
                "Provide an IAM access key tailored to a user, equipped with the necessary permissions")
        String accessKey,
    @NotBlank
        @TemplateProperty(
            secret = true,
            group = "authentication",
            label = "Secret key",
            description = "Provide a secret key associated with the access key")
        String secretKey) {

  @Override
  public String toString() {
    return "BedrockEmbeddingCredential{accessKey=[REDACTED], secretKey=[REDACTED]}";
  }
}
