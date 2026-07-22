/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.suppliers;

import io.camunda.connector.aws.AwsClientSupport;
import io.camunda.connector.textract.model.TextractRequest;
import software.amazon.awssdk.services.textract.TextractAsyncClient;
import software.amazon.awssdk.services.textract.TextractClient;

public class AmazonTextractClientSupplier {

  public TextractClient getSyncTextractClient(final TextractRequest request) {
    return AwsClientSupport.createClient(TextractClient.builder(), request);
  }

  public TextractAsyncClient getAsyncTextractClient(final TextractRequest request) {
    return AwsClientSupport.createClient(TextractAsyncClient.builder(), request);
  }
}
