/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws;

import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReplaceSecretsTest {



    @Test
    public void replaceSecrets_shouldReplaceAuthenticationSecrets() {
        // Given
        String input = """
                {
                   "authentication": {
                     "accessKey": "secrets.ACCESS_KEY",
                     "secretKey": "secrets.SECRET_KEY"
                   }
                 }""";
        OutboundConnectorContext context = getContextWithSecrets();
        AwsBaseRequest request = GsonComponentSupplier.gsonInstance().fromJson(input, AwsBaseRequest.class);
        // When
        context.replaceSecrets(request);
        // Then
        assertThat(request.getAuthentication().getAccessKey()).isEqualTo(TestData.Authentication.ActualValue.ACCESS_KEY);
        assertThat(request.getAuthentication().getSecretKey()).isEqualTo(TestData.Authentication.ActualValue.SECRET_KEY);
    }

    @Test
    public void replaceSecrets_shouldReplaceConfigurationSecrets() {
        // Given
        String input = """
                      {
                      "configuration": {"region": "secrets.REGION_KEY"}
                      }
                 """;
        OutboundConnectorContext context = getContextWithSecrets();
        AwsBaseRequest request = GsonComponentSupplier.gsonInstance().fromJson(input, AwsBaseRequest.class);
        // When
        context.replaceSecrets(request);
        // Then
        assertThat(request.getConfiguration().getRegion()).isEqualTo(TestData.Configuration.ActualValue.REGION);
    }


    public OutboundConnectorContext getContextWithSecrets(){
        return OutboundConnectorContextBuilder.create()
                .secret(TestData.Authentication.Secrets.ACCESS_KEY, TestData.Authentication.ActualValue.ACCESS_KEY)
                .secret(TestData.Authentication.Secrets.SECRET_KEY, TestData.Authentication.ActualValue.SECRET_KEY)
                .secret(TestData.Configuration.Secrets.REGION, TestData.Configuration.ActualValue.REGION).build();
    }
}
