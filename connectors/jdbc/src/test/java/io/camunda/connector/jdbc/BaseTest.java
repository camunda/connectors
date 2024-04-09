/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public abstract class BaseTest {

  private static final String FAIL_REQUEST_VALIDATION_CASES_PATH =
      "src/test/resources/requests/fail-request-validation-test-cases.json";
  private static final String SUCCESS_CASES_PATH =
      "src/test/resources/requests/success-test-cases.json";
  protected ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER;

  protected static Stream<String> failRequestValidationTestCases() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_REQUEST_VALIDATION_CASES_PATH);
  }

  protected static Stream<String> successTestCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_CASES_PATH);
  }

  @SuppressWarnings("unchecked")
  protected static Stream<String> loadTestCasesFromResourceFile(final String fileWithTestCasesUri)
      throws IOException {
    final String cases = readString(new File(fileWithTestCasesUri).toPath(), UTF_8);
    final ObjectMapper mapper = ConnectorsObjectMapperSupplier.getCopy();
    var array = mapper.readValue(cases, ArrayList.class);
    return array.stream()
        .map(
            value -> {
              try {
                return mapper.writeValueAsString(value);
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            })
        .map(Arguments::of);
  }

  public interface ActualValue {

    interface Authentication {
      String USERNAME = "testUserName";
      String PASSWORD = "testPassword";
      String URI = "jdbc:mysql//userName:password@localhost:5672";
      String PORT = "5672";
      String HOST = "localhost";
    }

    interface Query {
      String QUERY = "SELECT * FROM table";
    }

    interface Variables {
      String VARIABLES = "[\"var1\", \"var2\"]";
    }
  }

  protected interface SecretsConstant {

    interface Authentication {
      String URI = "URI_KEY";
      String USERNAME = "USERNAME_KEY";
      String PASSWORD = "PASSWORD_KEY";
      String HOST = "HOST_KEY";
      String PORT = "PORT_KEY";
    }

    interface Query {
      String QUERY = "QUERY_KEY";
    }

    interface Variables {
      String VARIABLES = "VARIABLES_KEY";
    }
  }
}
