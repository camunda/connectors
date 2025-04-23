package io.camunda.connector.runtime.core.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class InstanceForwardingHttpClientTest {

  private static final String IP_1 = "10.0.0.10";
  private static final String IP_2 = "10.0.0.11";

  private final DefaultInstancesUrlBuilder urlBuilder =
      new DefaultInstancesUrlBuilder(
          8080, "headless-service-url:8080", (host -> new String[] {IP_1, IP_2}));

  @Test
  public void shouldReturnMultipleResponses_whenMultipleRuntimesAndGetRequest()
      throws IOException, InterruptedException {
    // given
    var mockedHttpClient = mock(HttpClient.class);
    when(mockedHttpClient.send(any(), any())).thenReturn(mock(HttpResponse.class));
    InstanceForwardingHttpClient instanceForwardingHttpClient =
        new InstanceForwardingHttpClient(mockedHttpClient, urlBuilder);

    // when
    instanceForwardingHttpClient.execute(
        "GET",
        "test/path?queryParam1=value1&queryParam2=value2",
        null,
        Map.of("Authorization", "Bearer exyz"),
        null);

    // then
    verify(mockedHttpClient, times(2))
        .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    verify(mockedHttpClient)
        .send(
            argThat(
                request ->
                    request
                            .uri()
                            .toString()
                            .equals(
                                "https://"
                                    + IP_1
                                    + ":8080/test/path?queryParam1=value1&queryParam2=value2")
                        && request.method().equals("GET")
                        && request.headers().map().containsKey("Authorization")
                        && request
                            .headers()
                            .map()
                            .get("Authorization")
                            .getFirst()
                            .equals("Bearer exyz")),
            any());
    verify(mockedHttpClient)
        .send(
            argThat(
                request ->
                    request
                            .uri()
                            .toString()
                            .equals(
                                "https://"
                                    + IP_2
                                    + ":8080/test/path?queryParam1=value1&queryParam2=value2")
                        && request.method().equals("GET")
                        && request.headers().map().containsKey("Authorization")
                        && request
                            .headers()
                            .map()
                            .get("Authorization")
                            .getFirst()
                            .equals("Bearer exyz")),
            any());
  }
}
