/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.google;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import io.camunda.connector.api.error.ConnectorException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class GoogleApiErrorsTest {

  private static GoogleJsonResponseException googleError(int code, String message) {
    var details = new GoogleJsonError();
    details.setCode(code);
    details.setMessage(message);
    return new GoogleJsonResponseException(
        new HttpResponseException.Builder(code, "Status", new HttpHeaders()), details);
  }

  @Test
  void shouldExposeGoogleReasonAndStatusCode() {
    var wrapped = new RuntimeException(googleError(404, "File not found: 1."));

    var result = (ConnectorException) GoogleApiErrors.translate(wrapped);

    assertThat(result.getErrorCode()).isEqualTo("404");
    assertThat(result.getMessage()).isEqualTo("Google API request failed: 404 File not found: 1.");
    assertThat(result.getCause()).isSameAs(wrapped);
  }

  @Test
  void shouldKeepWrapperMessageAsContext() {
    var wrapped =
        new RuntimeException("IO exception while uploading a file", googleError(403, "No quota"));

    var result = GoogleApiErrors.translate(wrapped);

    assertThat(result.getMessage())
        .isEqualTo("IO exception while uploading a file: Google API request failed: 403 No quota");
  }

  @Test
  void shouldLeaveOtherExceptionsUntouched() {
    var plain = new RuntimeException(new IOException("boom"));
    var connector = new ConnectorException("X", "already handled");

    assertThat(GoogleApiErrors.translate(plain)).isSameAs(plain);
    assertThat(GoogleApiErrors.translate(connector)).isSameAs(connector);
  }
}
