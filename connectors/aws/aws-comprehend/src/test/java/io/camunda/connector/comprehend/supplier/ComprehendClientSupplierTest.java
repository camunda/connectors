/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.comprehend.supplier;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.comprehend.AmazonComprehendAsyncClient;
import com.amazonaws.services.comprehend.AmazonComprehendClient;
import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.comprehend.model.ComprehendRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ComprehendClientSupplierTest {

  private ComprehendRequest request;

  private ComprehendClientSupplier clientSupplier;

  @BeforeEach
  public void setUp() {
    request = new ComprehendRequest();
    request.setConfiguration(new AwsBaseConfiguration("region", ""));

    clientSupplier = new ComprehendClientSupplier();
  }

  @Test
  void getSyncClient() {
    var amazonComprehendClient = clientSupplier.getSyncClient(request);

    assertThat(amazonComprehendClient).isNotNull();
    assertThat(amazonComprehendClient).isInstanceOf(AmazonComprehendClient.class);
  }

  @Test
  void getAsyncClient() {
    var amazonComprehendAsyncClient = clientSupplier.getAsyncClient(request);

    assertThat(amazonComprehendAsyncClient).isNotNull();
    assertThat(amazonComprehendAsyncClient).isInstanceOf(AmazonComprehendAsyncClient.class);
  }
}
