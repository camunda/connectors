package io.camunda.connector.http.base.client.apache;

import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.org.webcompere.systemstubs.SystemStubs.restoreSystemProperties;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.HttpMethod;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import wiremock.com.fasterxml.jackson.databind.node.POJONode;

@WireMockTest
public class ProxyTest {

  private static final WireMockServer proxy = new WireMockServer(options().dynamicPort());
  private static CustomApacheHttpClient proxiedApacheHttpClient;
  private static GenericContainer<?> proxyContainer;

  @BeforeAll
  public static void setUp() {
    proxy.start();
    proxyContainer =
        new GenericContainer<>(DockerImageName.parse("sameersbn/squid:3.5.27-2"))
            .withExposedPorts(3128)
            .withClasspathResourceMapping("squid.conf", "/etc/squid/squid.conf", BindMode.READ_ONLY)
            .withClasspathResourceMapping("passwords", "/etc/squid/passwords", BindMode.READ_ONLY)
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forListeningPort());
    Testcontainers.exposeHostPorts(proxy.port());
    proxyContainer.withAccessToHost(true);
    proxyContainer.start();
    // Set up the HttpClient to use the proxy
    String proxyHost = proxyContainer.getHost();
    Integer proxyPort = proxyContainer.getMappedPort(3128);
    setAllSystemProperties(proxyHost, proxyPort);
    proxiedApacheHttpClient = CustomApacheHttpClient.create(HttpClients.custom());
  }

  private static void setAllSystemProperties(String proxyHost, Integer proxyPort) {
    System.setProperty("http.proxyHost", proxyHost);
    System.setProperty("http.proxyPort", proxyPort.toString());
    System.setProperty("http.nonProxyHosts", "");
    System.setProperty("https.proxyHost", proxyHost);
    System.setProperty("https.proxyPort", proxyPort.toString());
    System.setProperty("https.nonProxyHosts", "");
    System.setProperty("http.proxyUser", "my-user");
    System.setProperty("http.proxyPassword", "demo");
  }

  private static void unsetAllSystemProperties() {
    System.setProperty("http.proxyHost", "");
    System.setProperty("http.proxyPort", "");
    System.setProperty("http.nonProxyHosts", "");
    System.setProperty("https.proxyHost", "");
    System.setProperty("https.proxyPort", "");
    System.setProperty("https.nonProxyHosts", "");
    System.setProperty("http.proxyUser", "");
    System.setProperty("http.proxyPassword", "");
  }

  @AfterAll
  public static void tearDown() {
    proxyContainer.stop();
    unsetAllSystemProperties();
    proxy.stop();
  }

  @AfterEach
  public void resetProxy() {
    proxy.resetAll();
  }

  @Test
  public void shouldReturn200_whenAuthenticationRequiredAndProvidedAsSystemProperty(
      WireMockRuntimeInfo wmRuntimeInfo) {
    proxy.stubFor(get("/protected").willReturn(ok().withBody("Hello, world!")));

    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.GET);
    request.setUrl(getWireMockBaseUrlWithPath("/protected"));
    HttpCommonResult result = proxiedApacheHttpClient.execute(request);
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.body()).isEqualTo("Hello, world!");
    assertThat(result.headers().get("Via")).asString().contains("squid");
    proxy.verify(getRequestedFor(urlEqualTo("/protected")));
  }

  private static Stream<Arguments> provideValidDataAsEnvVars() {
    return Stream.of(
        Arguments.of("my-user", "demo", "/protected"),
        Arguments.of( // username: user-with?special%char password: pass%?word
            "user-with?special%char", "pass%?word", "/protected"),
        Arguments.of("", "", "/path"));
  }

  @ParameterizedTest
  @MethodSource("provideValidDataAsEnvVars")
  public void shouldReturn200_whenValidEnvVar(String user, String password, String path)
      throws Exception {
    restoreSystemProperties(
        () -> {
          withEnvironmentVariables(
                  "CONNECTOR_HTTP_PROXY_HOST",
                  "localhost",
                  "CONNECTOR_HTTP_PROXY_PORT",
                  proxyContainer.getMappedPort(3128).toString(),
                  "CONNECTOR_HTTP_PROXY_USER",
                  user,
                  "CONNECTOR_HTTP_PROXY_PASSWORD",
                  password)
              .execute(
                  () -> {
                    proxy.stubFor(get(path).willReturn(ok().withBody("Hello, world!")));
                    unsetAllSystemProperties();

                    HttpCommonRequest request = new HttpCommonRequest();
                    request.setMethod(HttpMethod.GET);
                    request.setUrl(getWireMockBaseUrlWithPath(path));
                    HttpCommonResult result =
                        proxiedApacheHttpClient.execute(
                            request); // http://host.testcontainers.internal:33029/protected
                    assertThat(result).isNotNull();
                    assertThat(result.status()).isEqualTo(200);
                    assertThat(result.body()).isEqualTo("Hello, world!");
                    assertThat(result.headers().get("Via")).asString().contains("squid");
                    proxy.verify(getRequestedFor(urlEqualTo(path)));
                  });
        });
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "invalid"})
  public void
      shouldThrowException_whenAuthenticationRequiredAndNotProvidedOrInvalidAsSystemProperty(
          String input) {
    proxy.stubFor(get("/protected").willReturn(ok().withBody("Hello, world!")));
    System.setProperty("http.proxyUser", input);
    System.setProperty("http.proxyPassword", input);

    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.GET);
    request.setUrl(getWireMockBaseUrlWithPath("/protected"));
    ConnectorException e =
        assertThrows(ConnectorException.class, () -> proxiedApacheHttpClient.execute(request));
    assertThat(e.getMessage()).isEqualTo("Proxy Authentication Required");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "my-user", "invalid"})
  public void shouldThrowException_whenAuthenticationRequiredAndNotProvidedAsEnvVars(
      String loginData) throws Exception {
    restoreSystemProperties(
        () -> {
          withEnvironmentVariables(
                  "CONNECTOR_HTTP_PROXY_HOST",
                  "localhost",
                  "CONNECTOR_HTTP_PROXY_PORT",
                  proxyContainer.getMappedPort(3128).toString(),
                  "CONNECTOR_HTTP_PROXY_USER",
                  loginData,
                  "CONNECTOR_HTTP_PROXY_PASSWORD",
                  loginData)
              .execute(
                  () -> {
                    proxy.stubFor(get("/protected").willReturn(ok().withBody("Hello, world!")));
                    unsetAllSystemProperties();
                    HttpCommonRequest request = new HttpCommonRequest();
                    request.setMethod(HttpMethod.GET);
                    request.setUrl(getWireMockBaseUrlWithPath("/protected"));
                    ConnectorException e =
                        assertThrows(
                            ConnectorException.class,
                            () -> proxiedApacheHttpClient.execute(request));
                    assertThat(e.getMessage()).isEqualTo("Proxy Authentication Required");
                  });
        });
  }

  @Test
  public void shouldUseSystemProperties_WhenEnvVarAndSystemPropertiesAreProvided()
      throws Exception {
    restoreSystemProperties(
        () -> {
          withEnvironmentVariables(
                  "CONNECTOR_HTTPS_PROXY_HOST",
                  "localhost",
                  "CONNECTOR_HTTPS_PROXY_PORT",
                  proxyContainer.getMappedPort(3128).toString(),
                  "CONNECTOR_HTTPS_PROXY_USER",
                  "wrong",
                  "CONNECTOR_HTTPS_PROXY_PASSWORD",
                  "wrong")
              .execute(
                  () -> {
                    proxy.stubFor(get("/protected").willReturn(ok().withBody("Hello, world!")));

                    HttpCommonRequest request = new HttpCommonRequest();
                    request.setMethod(HttpMethod.GET);
                    request.setUrl(getWireMockBaseUrlWithPath("/protected"));
                    HttpCommonResult result = proxiedApacheHttpClient.execute(request);
                    assertThat(result).isNotNull();
                    assertThat(result.status()).isEqualTo(200);
                    assertThat(result.body()).isEqualTo("Hello, world!");
                    assertThat(result.headers().get("Via")).asString().contains("squid");
                    proxy.verify(getRequestedFor(urlEqualTo("/protected")));
                  });
        });
  }

  @Test
  public void shouldReturn200_whenGetAndProxySet() {
    proxy.stubFor(get("/path").willReturn(ok().withBody("Hello, world!")));

    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.GET);
    request.setUrl(getWireMockBaseUrlWithPath("/path"));
    HttpCommonResult result = proxiedApacheHttpClient.execute(request);
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.body()).isEqualTo("Hello, world!");
    assertThat(result.headers().get("Via")).asString().contains("squid");
    proxy.verify(getRequestedFor(urlEqualTo("/path")));
  }

  @Test
  public void shouldReturn200_whenPostAndProxySet() {
    proxy.stubFor(
        post("/path").willReturn(created().withJsonBody(new POJONode(Map.of("key1", "value1")))));

    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.POST);
    request.setUrl(getWireMockBaseUrlWithPath("/path"));
    HttpCommonResult result = proxiedApacheHttpClient.execute(request);
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(201);
    assertThat(result.body()).isEqualTo(Map.of("key1", "value1"));
    assertThat(result.headers().get("Via")).asString().contains("squid");
    proxy.verify(postRequestedFor(urlEqualTo("/path")));
  }

  @Test
  public void shouldReturn200_whenPutAndProxySet() {
    proxy.stubFor(
        put("/path").willReturn(ok().withJsonBody(new POJONode(Map.of("key1", "value1")))));

    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.PUT);
    request.setUrl(getWireMockBaseUrlWithPath("/path"));
    HttpCommonResult result = proxiedApacheHttpClient.execute(request);
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.body()).isEqualTo(Map.of("key1", "value1"));
    assertThat(result.headers().get("Via")).asString().contains("squid");
    proxy.verify(putRequestedFor(urlEqualTo("/path")));
  }

  @Test
  public void shouldReturn200_whenDeleteAndProxySet() {
    proxy.stubFor(delete("/path").willReturn(noContent()));

    HttpCommonRequest request = new HttpCommonRequest();
    request.setMethod(HttpMethod.DELETE);
    request.setUrl(getWireMockBaseUrlWithPath("/path"));
    HttpCommonResult result = proxiedApacheHttpClient.execute(request);
    assertThat(result).isNotNull();
    assertThat(result.status()).isEqualTo(204);
    assertThat(result.headers().get("Via")).asString().contains("squid");
    proxy.verify(deleteRequestedFor(urlEqualTo("/path")));
  }

  private String getWireMockBaseUrlWithPath(String path) {
    return "http://host.testcontainers.internal:" + proxy.port() + path;
  }
}
