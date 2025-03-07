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
import io.camunda.connector.idp.extraction.supplier.TextractClientSupplier;
import io.camunda.connector.idp.extraction.util.ExtractionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.textract.TextractClient;

class TextractClientSupplierTest {

  private ExtractionRequest request;
  private TextractClientSupplier clientSupplier;

  @BeforeEach
  public void setUp() {
    clientSupplier = new TextractClientSupplier();
    AwsBaseConfiguration configuration = new AwsBaseConfiguration("region", "");

    AwsProvider baseRequest = new AwsProvider();
    baseRequest.setConfiguration(configuration);
    request =
        new ExtractionRequest(ExtractionTestUtils.TEXTRACT_EXTRACTION_REQUEST_DATA, baseRequest);
  }

  @Test
  void getTextractClient() {
    TextractClient client = clientSupplier.getTextractClient((AwsProvider) request.baseRequest());
    assertThat(client).isInstanceOf(TextractClient.class);
  }
}
