/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.suppliers;

import io.camunda.connector.aws.model.impl.AwsBaseConfiguration;
import io.camunda.connector.idp.extraction.model.ExtractionRequest;
import io.camunda.connector.idp.extraction.supplier.S3ClientSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import static org.assertj.core.api.Assertions.assertThat;

class S3ClientSupplierTest {

    private ExtractionRequest request;
    private S3ClientSupplier clientSupplier;

    @BeforeEach
    public void setUp() {
        clientSupplier = new S3ClientSupplier();
        AwsBaseConfiguration configuration = new AwsBaseConfiguration("region", "");

        request = new ExtractionRequest();
        request.setConfiguration(configuration);
    }

    @Test
    void getAsyncS3Client() {
        S3AsyncClient client = clientSupplier.getAsyncS3Client(request);
        assertThat(client).isInstanceOf(S3AsyncClient.class);
    }
}
