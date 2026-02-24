/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.agentcore.memory;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public abstract class BaseTest {

  protected static final ObjectMapper mapper = ConnectorsObjectMapperSupplier.getCopy();

  protected interface ActualValue {
    String ACCESS_KEY = "test-access-key";
    String SECRET_KEY = "test-secret-key";
    String REGION = "us-east-1";
    String MEMORY_ID = "mem-test-123";
    String NAMESPACE = "/strategies/preferences/actors/user-1";
    String SEARCH_QUERY = "user preferences";
  }

  protected interface SecretsConstant {
    String ACCESS_KEY = "ACCESS_KEY";
    String SECRET_KEY = "SECRET_KEY";
  }

  protected static OutboundConnectorContextBuilder getContextBuilderWithSecrets() {
    return OutboundConnectorContextBuilder.create()
        .validation(new DefaultValidationProvider())
        .secret(SecretsConstant.ACCESS_KEY, ActualValue.ACCESS_KEY)
        .secret(SecretsConstant.SECRET_KEY, ActualValue.SECRET_KEY);
  }

  protected static <T> T readData(String path, Class<T> type) throws IOException {
    final String cases = readString(new File(path).toPath(), UTF_8);
    return mapper.readValue(cases, type);
  }

  @SuppressWarnings("unchecked")
  protected static Stream<String> loadTestCasesFromResourceFile(final String fileWithTestCasesUri)
      throws IOException {
    var array = readData(fileWithTestCasesUri, ArrayList.class);
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
}
