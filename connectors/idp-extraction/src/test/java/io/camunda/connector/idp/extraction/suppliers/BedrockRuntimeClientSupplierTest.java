/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.suppliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.aws.model.impl.AwsBaseRequest;
import io.camunda.connector.idp.extraction.model.ExtractionRequest;
import io.camunda.connector.idp.extraction.model.ProviderConfiguration;
import io.camunda.connector.idp.extraction.supplier.BedrockRuntimeClientSupplier;
import io.camunda.connector.idp.extraction.util.ExtractionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

class BedrockRuntimeClientSupplierTest {

  private ExtractionRequest request;
  private BedrockRuntimeClientSupplier clientSupplier;

  @BeforeEach
  public void setUp() {
    clientSupplier = new BedrockRuntimeClientSupplier();
    AwsBaseConfiguration configuration = new AwsBaseConfiguration("region", "");

    AwsBaseRequest baseRequest = new AwsBaseRequest();
    baseRequest.setConfiguration(configuration);
    ProviderConfiguration providerConfiguration = new ProviderConfiguration(baseRequest, null);
    request =
        new ExtractionRequest(
            ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA, null, providerConfiguration);
  }

  @Test
  void getBedrockRuntimeClient() {
    BedrockRuntimeClient client =
        clientSupplier.getBedrockRuntimeClient(request.providerConfiguration().awsRequest());
    assertThat(client).isInstanceOf(BedrockRuntimeClient.class);
  }
}
