/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.suppliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.textract.model.TextractRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.textract.TextractAsyncClient;
import software.amazon.awssdk.services.textract.TextractClient;

class AmazonTextractClientSupplierTest {

  private TextractRequest request;
  private AmazonTextractClientSupplier clientSupplier;

  @BeforeEach
  public void setUp() {
    clientSupplier = new AmazonTextractClientSupplier();
    AwsBaseConfiguration configuration = new AwsBaseConfiguration("region", "");

    request = new TextractRequest();
    request.setConfiguration(configuration);
  }

  @Test
  void getSyncTextractClient() {
    TextractClient client =
        (TextractClient) clientSupplier.getSyncTextractClient(request);
    assertThat(client).isInstanceOf(TextractClient.class);
  }

  @Test
  void getAsyncTextractClient() {
    TextractAsyncClient client = clientSupplier.getAsyncTextractClient(request);
    assertNotNull(client);
    assertThat(client).isInstanceOf(TextractAsyncClient.class);
  }
}
