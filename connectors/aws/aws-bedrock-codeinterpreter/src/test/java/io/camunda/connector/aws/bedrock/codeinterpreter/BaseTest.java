/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.codeinterpreter;

import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;

public abstract class BaseTest {

  protected interface ActualValue {
    String ACCESS_KEY = "test-access-key";
    String SECRET_KEY = "test-secret-key";
    String REGION = "us-east-1";
    String CODE = "print('hello world')";
  }

  protected interface SecretsConstant {
    String ACCESS_KEY = "AWS_ACCESS_KEY";
    String SECRET_KEY = "AWS_SECRET_KEY";
  }

  protected static OutboundConnectorContextBuilder getContextBuilderWithSecrets() {
    return OutboundConnectorContextBuilder.create()
        .validation(new DefaultValidationProvider())
        .secret(SecretsConstant.ACCESS_KEY, ActualValue.ACCESS_KEY)
        .secret(SecretsConstant.SECRET_KEY, ActualValue.SECRET_KEY);
  }
}
