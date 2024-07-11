package io.camunda.connector.aws.bedrock;

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

public class BaseTest {

  public static Stream<String> loadInvokeModelVariables() {
    try {
      return loadTestCasesFromResourceFile(
          "src/test/resources/invokemodel/invokeModelExample.json");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

    public static Stream<String> loadConverseVariables() {
        try {
            return loadTestCasesFromResourceFile(
                    "src/test/resources/converse/converseExample.json");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
}
