package io.camunda.connector.http;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;
import static org.apache.http.entity.ContentType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.Gson;
import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.SecretStore;
import io.camunda.connector.http.components.GsonComponentSupplier;
import io.camunda.connector.http.model.HttpJsonResult;
import io.camunda.connector.test.ConnectorContextBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HttpJsonFunctionTest {

  private static final String SUCCESS_CASES_RESOURCE_PATH =
      "src/test/resources/requests/success-test-cases.json";
  private static final String FAIL_CASES_RESOURCE_PATH =
      "src/test/resources/requests/fail-test-cases.json";

  private Gson gson = GsonComponentSupplier.gsonInstance();
  @Mock private GsonFactory gsonFactory;
  @Mock private HttpRequestFactory requestFactory;
  @Mock private SecretStore secretStore;
  @Mock private HttpRequest httpRequest;
  @Mock private HttpResponse httpResponse;

  private HttpJsonFunction functionUnderTest;

  @BeforeEach
  public void setup() {
    functionUnderTest = new HttpJsonFunction(gson, requestFactory, gsonFactory);
  }

  @Test
  public void shouldConstruct_WhenEmptyConstructorInvoked() {
    new HttpJsonFunction();
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("successCases")
  public void shouldReturnResult_WhenExecuted(final String input) throws IOException {
    // given - minimal required entity
    final ConnectorContext context = Mockito.mock(ConnectorContext.class);
    when(context.getVariables()).thenReturn(input);
    when(context.getSecretStore()).thenReturn(secretStore);

    when(secretStore.replaceSecret(anyString())).thenAnswer(i -> i.getArguments()[0]);

    when(requestFactory.buildRequest(
            anyString(), any(GenericUrl.class), nullable(HttpContent.class)))
        .thenReturn(httpRequest);
    when(httpResponse.getHeaders())
        .thenReturn(new HttpHeaders().setContentType(APPLICATION_JSON.getMimeType()));
    when(httpRequest.execute()).thenReturn(httpResponse);

    // when
    Object functionCallResponseAsObject = functionUnderTest.execute(context);

    // then
    verify(httpRequest).execute();
    assertThat(functionCallResponseAsObject).isInstanceOf(HttpJsonResult.class);
    HttpJsonResult functionCallResponse = (HttpJsonResult) functionCallResponseAsObject;
    assertThat(functionCallResponse.getHeaders()).containsValue(APPLICATION_JSON.getMimeType());
  }

  @ParameterizedTest(name = "Executing test case: {0}")
  @MethodSource("failCases")
  public void shouldReturnFallbackResult_WhenMalformedRequest(final String input) {
    final ConnectorContext ctx = ConnectorContextBuilder.create().variables(input).build();

    // when
    Throwable exceptionThrown =
        Assertions.assertThrows(
            RuntimeException.class,
            () -> {
              functionUnderTest.execute(ctx);
            });

    // then
    assertThat(exceptionThrown).isInstanceOf(RuntimeException.class);
  }

  private static Stream loadTestCasesFromResourceFile(final String fileWithTestCasesUri)
      throws IOException {
    final String successCases = readString(new File(fileWithTestCasesUri).toPath(), UTF_8);
    final Gson testingGson = new Gson();
    ArrayList array = testingGson.fromJson(successCases, ArrayList.class);
    return array.stream().map(x -> testingGson.toJson(x)).map(Arguments::of);
  }

  private static Stream successCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_CASES_RESOURCE_PATH);
  }

  private static Stream failCases() throws IOException {
    return loadTestCasesFromResourceFile(FAIL_CASES_RESOURCE_PATH);
  }
}
