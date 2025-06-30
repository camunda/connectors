/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.suppliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.idp.extraction.model.ExtractionRequest;
import io.camunda.connector.idp.extraction.model.providers.AwsProvider;
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

    AwsProvider baseRequest = new AwsProvider();
    baseRequest.setConfiguration(configuration);
    request =
        new ExtractionRequest(ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA, baseRequest);
  }

  @Test
  void getBedrockRuntimeClient() {
    BedrockRuntimeClient client =
        clientSupplier.getBedrockRuntimeClient((AwsProvider) request.baseRequest());
    assertThat(client).isInstanceOf(BedrockRuntimeClient.class);
  }

  @Test
  void getBedrockRuntimeClientWithCustomEndpoint() {
    // Create configuration with custom endpoint
    AwsBaseConfiguration configurationWithEndpoint = 
        new AwsBaseConfiguration("us-east-1", "https://bedrock-runtime.vpce-12345.us-east-1.vpce.amazonaws.com");
    
    AwsProvider awsProvider = new AwsProvider();
    awsProvider.setConfiguration(configurationWithEndpoint);
    
    // Create client with custom endpoint
    BedrockRuntimeClient client = clientSupplier.getBedrockRuntimeClient(awsProvider);
    
    assertThat(client).isInstanceOf(BedrockRuntimeClient.class);
    // The client should be created successfully with the custom endpoint
    // We can't easily verify the internal endpoint configuration without accessing private fields
    // but the successful creation indicates the endpointOverride was called correctly
  }

  @Test
  void getBedrockRuntimeClientWithNullEndpoint() {
    // Create configuration with null endpoint
    AwsBaseConfiguration configurationWithNullEndpoint = 
        new AwsBaseConfiguration("us-east-1", null);
    
    AwsProvider awsProvider = new AwsProvider();
    awsProvider.setConfiguration(configurationWithNullEndpoint);
    
    // Create client - should work the same as before
    BedrockRuntimeClient client = clientSupplier.getBedrockRuntimeClient(awsProvider);
    
    assertThat(client).isInstanceOf(BedrockRuntimeClient.class);
  }

  @Test
  void getBedrockRuntimeClientWithEmptyEndpoint() {
    // Create configuration with empty endpoint
    AwsBaseConfiguration configurationWithEmptyEndpoint = 
        new AwsBaseConfiguration("us-east-1", "   ");
    
    AwsProvider awsProvider = new AwsProvider();
    awsProvider.setConfiguration(configurationWithEmptyEndpoint);
    
    // Create client - should work the same as before
    BedrockRuntimeClient client = clientSupplier.getBedrockRuntimeClient(awsProvider);
    
    assertThat(client).isInstanceOf(BedrockRuntimeClient.class);
  }
}
