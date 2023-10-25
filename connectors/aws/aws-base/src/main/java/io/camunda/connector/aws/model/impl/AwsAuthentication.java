/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.model.impl;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.aws.model.impl.AwsAuthentication.AwsDefaultCredentialsChainAuthentication;
import io.camunda.connector.aws.model.impl.AwsAuthentication.AwsStaticCredentialsAuthentication;
import jakarta.validation.constraints.NotBlank;

// Note on `defaultImpl`. This is left for backwards compatibility
// with existing BPMN diagrams.
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "type",
    defaultImpl = AwsStaticCredentialsAuthentication.class)
@JsonSubTypes({
  @JsonSubTypes.Type(value = AwsStaticCredentialsAuthentication.class, name = "credentials"),
  @JsonSubTypes.Type(
      value = AwsDefaultCredentialsChainAuthentication.class,
      name = "defaultCredentialsChain"),
})
public sealed interface AwsAuthentication
    permits AwsDefaultCredentialsChainAuthentication, AwsStaticCredentialsAuthentication {
  record AwsDefaultCredentialsChainAuthentication() implements AwsAuthentication {}

  record AwsStaticCredentialsAuthentication(@NotBlank String accessKey, @NotBlank String secretKey)
      implements AwsAuthentication {}
}
