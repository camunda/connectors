/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.google.gcs.model.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import org.junit.jupiter.api.Test;

class GcsCredentialJacksonTest {

  private static final ObjectMapper MAPPER = ConnectorsObjectMapperSupplier.getCopy();

  private static final String SA_KEY =
      "{ \"type\": \"service_account\", \"project_id\": \"gcs-connector-tests\" }";

  @Test
  void deserializesReusableCredentialValueIntoConfiguration() throws Exception {
    String value = "{\"authentication\":{\"jsonKey\":" + MAPPER.writeValueAsString(SA_KEY) + "}}";

    GcsCredentialConfiguration cfg = MAPPER.readValue(value, GcsCredentialConfiguration.class);

    assertThat(cfg.authentication()).isNotNull();
    assertThat(cfg.authentication().getJsonKey()).isEqualTo(SA_KEY);
  }

  @Test
  void bindsReusableCredentialThroughRequest() throws Exception {
    String value =
        "{\"googleGcsCredential\":{\"authentication\":{\"jsonKey\":"
            + MAPPER.writeValueAsString(SA_KEY)
            + "}}}";

    ObjectStorageRequest request = MAPPER.readValue(value, ObjectStorageRequest.class);

    assertThat(request.getAuthentication()).isNotNull();
    assertThat(request.getAuthentication().getJsonKey()).isEqualTo(SA_KEY);
  }
}
