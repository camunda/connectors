/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.awslambda.model;

import static io.camunda.connector.aws.model.impl.AwsAuthentication.AwsStaticCredentialsAuthentication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.aws.model.impl.AwsCredentialConfiguration;
import io.camunda.connector.awslambda.BaseTest;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AwsLambdaRequestTest extends BaseTest {

  private static final AwsCredentialConfiguration CREDENTIAL =
      new AwsCredentialConfiguration(
          new AwsStaticCredentialsAuthentication("cred-ak", "cred-sk"), "eu-west-1");

  private AwsLambdaRequest request;

  @BeforeEach
  public void beforeEach() {
    request = new AwsLambdaRequest();
    request.setAwsFunction(new FunctionRequestData());
  }

  @Test
  void usesCredentialAuthenticationAndRegionWhenBound() {
    request.setAwsCredential(CREDENTIAL);

    assertThat(request.getAuthentication()).isInstanceOf(AwsStaticCredentialsAuthentication.class);
    assertThat(((AwsStaticCredentialsAuthentication) request.getAuthentication()).accessKey())
        .isEqualTo("cred-ak");
    assertThat(request.getConfiguration().region()).isEqualTo("eu-west-1");
  }

  @Test
  void fallsBackToInlineWhenNoCredential() {
    request.setAuthentication(new AwsStaticCredentialsAuthentication("inline-ak", "inline-sk"));
    request.setConfiguration(new AwsBaseConfiguration("us-east-1", null));

    assertThat(((AwsStaticCredentialsAuthentication) request.getAuthentication()).accessKey())
        .isEqualTo("inline-ak");
    assertThat(request.getConfiguration().region()).isEqualTo("us-east-1");
  }

  @Test
  void credentialOnlySatisfiesAuthenticationValidation() {
    // No inline authentication set; validation is getter-based, so a bound credential satisfies it.
    request.setAwsCredential(CREDENTIAL);

    assertThat(request.isAuthenticationPresent()).isTrue();
  }

  @ParameterizedTest(name = "Should throw exception when validate null field # {index}")
  @MethodSource("failRequestCases")
  void validate_shouldThrowExceptionWhenLeastOneNotExistRequestField(String input) {
    // Given request , where one field is null
    var context =
        getContextBuilderWithSecrets()
            .variables(input)
            .validation(new DefaultValidationProvider())
            .build();
    // When request validate
    // Then we except exception with message
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.bindVariables(AwsLambdaRequest.class),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input:");
  }

  @ParameterizedTest(name = "Should replace secrets")
  @MethodSource("successSecretsRequestCases")
  void replaceSecrets_shouldReplaceSecrets(String input) {
    // When replace secrets
    var context =
        getContextBuilderWithSecrets()
            .variables(input)
            .validation(new DefaultValidationProvider())
            .build();
    request = context.bindVariables(AwsLambdaRequest.class);
    AwsStaticCredentialsAuthentication sca =
        (AwsStaticCredentialsAuthentication) request.getAuthentication();
    // Then
    assertThat(sca.secretKey()).isEqualTo(ACTUAL_SECRET_KEY);
    assertThat(sca.accessKey()).isEqualTo(ACTUAL_ACCESS_KEY);
    assertThat(request.getConfiguration().region()).isEqualTo(ACTUAL_FUNCTION_REGION);
    assertThat(request.getAwsFunction().getFunctionName()).isEqualTo(ACTUAL_FUNCTION_NAME);
  }
}
