/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import jakarta.validation.constraints.NotNull;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type",
    defaultImpl = ProviderConfig.AwsConfiguration.class)
@JsonSubTypes({
  @JsonSubTypes.Type(value = ProviderConfig.AwsConfiguration.class, name = "aws"),
  @JsonSubTypes.Type(value = ProviderConfig.GeminiConfiguration.class, name = "gemini")
})
@NotNull
public sealed interface ProviderConfig
    permits ProviderConfig.AwsConfiguration, ProviderConfig.GeminiConfiguration {

  final class AwsConfiguration extends AwsBaseRequest implements ProviderConfig {}

  final class GeminiConfiguration extends GeminiBaseRequest implements ProviderConfig {}
}
