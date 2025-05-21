package io.camunda.connector.generator.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.generator.dsl.http.OperationParseResult;
import io.camunda.connector.generator.openapi.util.OperationUtil;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class OperationUtilTest {

  static Stream<Arguments> getMultipartHeaderTestData() {
    return Stream.of(
        Arguments.of("multipart/form-data", true), Arguments.of("application/json", false));
  }

  @ParameterizedTest
  @MethodSource("getMultipartHeaderTestData")
  void shouldAddMultipartHeader_WhenMultipartBody(String type, boolean expectHeaderIsAdded) {
    // given
    var source =
        "openapi: 3.0.0\n"
            + "info:\n"
            + "  title: test\n"
            + "  version: 1.0.0\n"
            + "servers:\n"
            + "  - url: https://camunda.proxy.beeceptor.com\n"
            + "paths:\n"
            + "  /mypath:\n"
            + "    post:\n"
            + "      summary: Uploads a profile image\n"
            + "      requestBody:\n"
            + "        content:\n"
            + "          "
            + type
            + ":\n"
            + "            schema:\n"
            + "              type: object\n"
            + "              properties:\n"
            + "                id:\n"
            + "                  type: string\n"
            + "                  format: uuid\n"
            + "                profileImage:\n"
            + "                  type: string\n"
            + "                  format: binary\n"
            + "      responses:\n"
            + "        '200': { description: OK }";
    var input = new OpenApiGenerationSource(List.of(source));

    List<OperationParseResult> operationParseResults =
        OperationUtil.extractOperations(
            input.openAPI(), input.includeOperations(), input.options());

    assertThat(operationParseResults).hasSize(1);

    boolean hasMatch =
        operationParseResults.getFirst().builder().getProperties().stream()
            .anyMatch(
                p ->
                    p.id().equals("Content-Type")
                        && p.valueOrExample().equals("multipart/form-data"));

    assertThat(hasMatch).isEqualTo(expectHeaderIsAdded);
  }
}
