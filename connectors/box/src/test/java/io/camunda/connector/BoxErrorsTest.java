/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

import static org.assertj.core.api.Assertions.assertThat;

import com.box.sdkgen.box.errors.BoxAPIError;
import com.box.sdkgen.box.errors.RequestInfo;
import com.box.sdkgen.box.errors.ResponseInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.box.BoxErrors;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class BoxErrorsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static BoxAPIError apiError(int status, String code, String bodyJson) throws Exception {
    var response = new ResponseInfo(status, Map.of());
    response.body = MAPPER.readTree(bodyJson);
    response.rawBody = bodyJson;
    response.code = code;
    response.contextInfo = response.body.get("context_info");
    response.requestId = "abc123";
    return new BoxAPIError(
        "Status " + status + "; Request ID: abc123",
        new RequestInfo("POST", "https://api.box.com", Map.of(), Map.of()),
        response);
  }

  @Test
  void translatesApiErrorUsingBodyMessageAndContext() throws Exception {
    var error =
        apiError(
            400,
            "bad_request",
            """
            {"type":"error","status":400,"code":"bad_request","message":"Bad Request",
             "context_info":{"errors":[{"reason":"invalid_parameter","name":"parent",
             "message":"Invalid value 'MyFolder'. 'parent' with value 'MyFolder' not found"}]}}
            """);

    var result = BoxErrors.translate(error);

    assertThat(result).isInstanceOf(ConnectorException.class);
    assertThat(((ConnectorException) result).getErrorCode()).isEqualTo("bad_request");
    assertThat(result.getMessage())
        .contains("HTTP 400")
        .contains("Bad Request")
        .contains("'parent' with value 'MyFolder' not found")
        .contains("Request ID: abc123");
    assertThat(result.getCause()).isSameAs(error);
  }

  @Test
  void findsApiErrorInCauseChain() throws Exception {
    var error = apiError(404, "not_found", "{\"code\":\"not_found\",\"message\":\"Not Found\"}");

    var result = BoxErrors.translate(new RuntimeException("Error downloading file: 1", error));

    assertThat(result).isInstanceOf(ConnectorException.class);
    assertThat(result.getMessage()).contains("HTTP 404").contains("Not Found");
  }

  @Test
  void fallsBackToRawBodyAndDefaultCode() throws Exception {
    var error = apiError(502, null, "{\"foo\":\"bar\"}");

    var result = (ConnectorException) BoxErrors.translate(error);

    assertThat(result.getErrorCode()).isEqualTo("BOX_API_ERROR");
    assertThat(result.getMessage()).contains("HTTP 502").contains("{\"foo\":\"bar\"}");
  }

  @Test
  void leavesUnrelatedExceptionsUntouched() {
    var original = new IllegalStateException("boom");
    assertThat(BoxErrors.translate(original)).isSameAs(original);
  }
}
