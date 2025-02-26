/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.http.base.client.apache;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.org.webcompere.systemstubs.SystemStubs.restoreSystemProperties;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.matching.MultipartValuePatternBuilder;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.ExecutionEnvironment;
import io.camunda.connector.http.base.TestDocumentFactory;
import io.camunda.connector.http.base.authentication.OAuthConstants;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.base.model.auth.ApiKeyAuthentication;
import io.camunda.connector.http.base.model.auth.ApiKeyLocation;
import io.camunda.connector.http.base.model.auth.BasicAuthentication;
import io.camunda.connector.http.base.model.auth.BearerAuthentication;
import io.camunda.connector.http.base.model.auth.OAuthAuthentication;
import io.camunda.document.CamundaDocument;
import io.camunda.document.reference.DocumentReference;
import io.camunda.document.store.DocumentCreationRequest;
import io.camunda.document.store.InMemoryDocumentStore;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import wiremock.com.fasterxml.jackson.databind.node.JsonNodeFactory;
import wiremock.com.fasterxml.jackson.databind.node.POJONode;

@WireMockTest
public class CustomApacheHttpClientTest {

  private final CustomApacheHttpClient customApacheHttpClient = CustomApacheHttpClient.getDefault();
  private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();
  private final InMemoryDocumentStore store = InMemoryDocumentStore.INSTANCE;

  @BeforeEach
  public void setUp() {
    store.clear();
  }

  @Nested
  class DocumentDownloadTests {

    @Test
    public void shouldStoreDocument_whenStoreResponseEnabled(WireMockRuntimeInfo wmRuntimeInfo)
        throws IOException {
      stubFor(
          get("/download")
              .willReturn(
                  ok().withHeader(HttpHeaders.CONTENT_TYPE, ContentType.IMAGE_JPEG.getMimeType())
                      .withBodyFile("fileName.jpg")));
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setStoreResponse(true);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/download");
      HttpCommonResult result =
          customApacheHttpClient.execute(
              request,
              new ProxyHandler(),
              new ExecutionEnvironment.SelfManaged(new TestDocumentFactory()));
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      assertThat(result.headers().get(HttpHeaders.CONTENT_TYPE))
          .isEqualTo(ContentType.IMAGE_JPEG.getMimeType());
      assertThat(result.body()).isNull();
      var documents = store.getDocuments();
      assertThat(documents).hasSize(1);
      var responseDocument = (CamundaDocument) result.document();
      DocumentReference.CamundaDocumentReference responseDocumentReference =
          (DocumentReference.CamundaDocumentReference) responseDocument.reference();
      assertThat(responseDocumentReference).isNotNull();
      var documentContent = documents.get(responseDocumentReference.documentId());
      assertThat(documentContent)
          .isEqualTo(getClass().getResourceAsStream("/__files/fileName.jpg").readAllBytes());
    }
  }

  @Nested
  class DocumentUploadTests {

    @Test
    public void shouldReturn201_whenUploadDocument(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(post("/path").withMultipartRequestBody(aMultipart()).willReturn(created()));
      var ref =
          store.createDocument(
              DocumentCreationRequest.from("The content of this file".getBytes())
                  .fileName("file.txt")
                  .contentType("text/plain")
                  .build());
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      var bodyMap = new HashMap<>();
      bodyMap.put("document", new CamundaDocument(ref.metadata(), ref, store));
      bodyMap.put("otherField", "otherValue");
      bodyMap.put("nullField", null);
      request.setHeaders(Map.of("Content-Type", ContentType.MULTIPART_FORM_DATA.getMimeType()));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      request.setBody(bodyMap);
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);

      verify(
          postRequestedFor(urlEqualTo("/path"))
              .withHeader(
                  "Content-Type", and(containing("multipart/form-data"), containing("boundary=")))
              .withRequestBodyPart(
                  new MultipartValuePatternBuilder()
                      .withName("otherField")
                      .withBody(equalTo("otherValue"))
                      .build())
              .withRequestBodyPart(
                  new MultipartValuePatternBuilder()
                      .withName("document")
                      .withBody(equalTo("The content of this file"))
                      .withHeader("Content-Type", equalTo("text/plain"))
                      .build()));
    }

    @Test
    public void shouldReturn201_whenUploadDocuments(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(post("/path").withMultipartRequestBody(aMultipart()).willReturn(created()));
      var ref =
          store.createDocument(
              DocumentCreationRequest.from("The content of this file".getBytes())
                  .fileName("file.txt")
                  .contentType("text/plain")
                  .build());
      var ref2 =
          store.createDocument(
              DocumentCreationRequest.from("The content of this file 2".getBytes())
                  .fileName("file2.txt")
                  .contentType("text/plain")
                  .build());
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      var bodyMap = new HashMap<>();
      bodyMap.put(
          "documents",
          List.of(
              new CamundaDocument(ref.metadata(), ref, store),
              new CamundaDocument(ref2.metadata(), ref2, store)));
      bodyMap.put("otherField", "otherValue");
      bodyMap.put("nullField", null);
      request.setHeaders(Map.of("Content-Type", ContentType.MULTIPART_FORM_DATA.getMimeType()));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      request.setBody(bodyMap);
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);

      verify(
          postRequestedFor(urlEqualTo("/path"))
              .withHeader(
                  "Content-Type", and(containing("multipart/form-data"), containing("boundary=")))
              .withRequestBodyPart(
                  new MultipartValuePatternBuilder()
                      .withName("otherField")
                      .withBody(equalTo("otherValue"))
                      .build())
              .withRequestBodyPart(
                  new MultipartValuePatternBuilder()
                      .withName("documents")
                      .withBody(equalTo("The content of this file"))
                      .withHeader("Content-Type", equalTo("text/plain"))
                      .build())
              .withRequestBodyPart(
                  new MultipartValuePatternBuilder()
                      .withName("documents")
                      .withBody(equalTo("The content of this file 2"))
                      .withHeader("Content-Type", equalTo("text/plain"))
                      .build()));
    }
  }

  @Nested
  class ProxyTests {

    private static final WireMockServer proxy = new WireMockServer(options().dynamicPort());
    private static CustomApacheHttpClient proxiedApacheHttpClient;
    private static GenericContainer<?> proxyContainer;

    @BeforeAll
    public static void setUp() {
      proxy.start();
      proxyContainer =
          new GenericContainer<>(DockerImageName.parse("sameersbn/squid:3.5.27-2"))
              .withExposedPorts(3128)
              .withClasspathResourceMapping(
                  "squid.conf", "/etc/squid/squid.conf", BindMode.READ_ONLY)
              .withClasspathResourceMapping("passwords", "/etc/squid/passwords", BindMode.READ_ONLY)
              .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forListeningPort());
      Testcontainers.exposeHostPorts(proxy.port());
      proxyContainer.withAccessToHost(true);
      proxyContainer.start();
      proxiedApacheHttpClient = CustomApacheHttpClient.create(HttpClients.custom());
    }

    private static void setAllSystemProperties() {
      String proxyHost = proxyContainer.getHost();
      Integer proxyPort = proxyContainer.getMappedPort(3128);
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
      unsetAllSystemProperties();
      proxyContainer.stop();
      proxy.stop();
    }

    @BeforeEach
    public void resetProxy() {
      unsetAllSystemProperties();
      proxy.resetAll();
    }

    @Test
    public void shouldReturn200_whenAuthenticationRequiredAndProvidedAsSystemProperty(
        WireMockRuntimeInfo wmRuntimeInfo) {
      proxy.stubFor(get("/protected").willReturn(ok().withBody("Hello, world!")));
      setAllSystemProperties();

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setUrl(getWireMockBaseUrlWithPath(wmRuntimeInfo, "/protected"));
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
    public void shouldReturn200_whenValidEnvVar(
        String user, String password, String path, WireMockRuntimeInfo wmRuntimeInfo)
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

                      HttpCommonRequest request = new HttpCommonRequest();
                      request.setMethod(HttpMethod.GET);
                      request.setUrl(getWireMockBaseUrlWithPath(wmRuntimeInfo, path));
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
            String input, WireMockRuntimeInfo wmRuntimeInfo) {
      proxy.stubFor(get("/protected").willReturn(ok().withBody("Hello, world!")));
      setAllSystemProperties();
      System.setProperty("http.proxyUser", input);
      System.setProperty("http.proxyPassword", input);

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setUrl(getWireMockBaseUrlWithPath(wmRuntimeInfo, "/protected"));
      ConnectorException e =
          assertThrows(ConnectorException.class, () -> proxiedApacheHttpClient.execute(request));
      assertThat(e.getMessage()).isEqualTo("Proxy Authentication Required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "my-user", "invalid"})
    public void shouldThrowException_whenAuthenticationRequiredAndNotProvidedAsEnvVars(
        String loginData, WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
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
                      HttpCommonRequest request = new HttpCommonRequest();
                      request.setMethod(HttpMethod.GET);
                      request.setUrl(getWireMockBaseUrlWithPath(wmRuntimeInfo, "/protected"));
                      ConnectorException e =
                          assertThrows(
                              ConnectorException.class,
                              () -> proxiedApacheHttpClient.execute(request));
                      assertThat(e.getMessage()).isEqualTo("Proxy Authentication Required");
                    });
          });
    }

    @Test
    public void shouldUseSystemProperties_WhenEnvVarAndSystemPropertiesAreProvided(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      restoreSystemProperties(
          () -> {
            withEnvironmentVariables(
                    "CONNECTOR_HTTPS_PROXY_HOST",
                    "localhost",
                    "CONNECTOR_HTTPS_PROXY_PORT",
                    proxyContainer.getMappedPort(3128).toString(),
                    "CONNECTOR_HTTPS_PROXY_USER",
                    "my-user",
                    "CONNECTOR_HTTPS_PROXY_PASSWORD",
                    "demo")
                .execute(
                    () -> {
                      proxy.stubFor(get("/protected").willReturn(ok().withBody("Hello, world!")));
                      setAllSystemProperties();

                      HttpCommonRequest request = new HttpCommonRequest();
                      request.setMethod(HttpMethod.GET);
                      request.setUrl(getWireMockBaseUrlWithPath(wmRuntimeInfo, "/protected"));
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
    public void shouldReturn200_whenGetAndProxySet(WireMockRuntimeInfo wmRuntimeInfo) {
      proxy.stubFor(get("/path").willReturn(ok().withBody("Hello, world!")));
      setAllSystemProperties();

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setUrl(getWireMockBaseUrlWithPath(wmRuntimeInfo, "/path"));
      HttpCommonResult result = proxiedApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      assertThat(result.body()).isEqualTo("Hello, world!");
      assertThat(result.headers().get("Via")).asString().contains("squid");
      proxy.verify(getRequestedFor(urlEqualTo("/path")));
    }

    @Test
    public void shouldReturn200_whenPostAndProxySet(WireMockRuntimeInfo wmRuntimeInfo) {
      proxy.stubFor(
          post("/path").willReturn(created().withJsonBody(new POJONode(Map.of("key1", "value1")))));
      setAllSystemProperties();

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setUrl(getWireMockBaseUrlWithPath(wmRuntimeInfo, "/path"));
      HttpCommonResult result = proxiedApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);
      assertThat(result.body()).isEqualTo(Map.of("key1", "value1"));
      assertThat(result.headers().get("Via")).asString().contains("squid");
      proxy.verify(postRequestedFor(urlEqualTo("/path")));
    }

    @Test
    public void shouldReturn200_whenPutAndProxySet(WireMockRuntimeInfo wmRuntimeInfo) {
      proxy.stubFor(
          put("/path").willReturn(ok().withJsonBody(new POJONode(Map.of("key1", "value1")))));
      setAllSystemProperties();

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.PUT);
      request.setUrl(getWireMockBaseUrlWithPath(wmRuntimeInfo, "/path"));
      HttpCommonResult result = proxiedApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      assertThat(result.body()).isEqualTo(Map.of("key1", "value1"));
      assertThat(result.headers().get("Via")).asString().contains("squid");
      proxy.verify(putRequestedFor(urlEqualTo("/path")));
    }

    @Test
    public void shouldReturn200_whenDeleteAndProxySet(WireMockRuntimeInfo wmRuntimeInfo) {
      proxy.stubFor(delete("/path").willReturn(noContent()));
      setAllSystemProperties();

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.DELETE);
      request.setUrl(getWireMockBaseUrlWithPath(wmRuntimeInfo, "/path"));
      HttpCommonResult result = proxiedApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(204);
      assertThat(result.headers().get("Via")).asString().contains("squid");
      proxy.verify(deleteRequestedFor(urlEqualTo("/path")));
    }

    private String getWireMockBaseUrlWithPath(WireMockRuntimeInfo wmRuntimeInfo, String path) {
      return "http://host.testcontainers.internal:" + proxy.port() + path;
    }
  }

  @Nested
  class EscapeTests {

    @ParameterizedTest
    @EnumSource(HttpMethod.class)
    public void shouldReturn200_whenSpaceInPathAndQueryParameters(
        HttpMethod method, WireMockRuntimeInfo wmRuntimeInfo) {

      stubFor(any(urlEqualTo("/path%20with%20spaces?andQuery=S%C3%A3o%20Paulo")).willReturn(ok()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(method);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path with spaces");
      request.setQueryParameters(Map.of("andQuery", "São Paulo"));
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
    }

    @ParameterizedTest
    @EnumSource(HttpMethod.class)
    public void shouldReturn200_whenSpaceInPathAndQueryParametersInPath(
        HttpMethod method, WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(
          any(urlEqualTo("/path%20with%20spaces?andQuery=Param%20with%20space"))
              .withQueryParams(Map.of("andQuery", equalTo("Param with space")))
              .willReturn(ok()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(method);
      request.setUrl(
          wmRuntimeInfo.getHttpBaseUrl() + "/path with spaces?andQuery=Param with space");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
    }

    @ParameterizedTest
    @EnumSource(HttpMethod.class)
    public void shouldReturn200_whenEscapedSpaceInPathAndQueryParametersInPath(
        HttpMethod method, WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(
          any(urlEqualTo("/path%20with%20spaces?andQuery=Param%20with%20space"))
              .withQueryParams(Map.of("andQuery", equalTo("Param with space")))
              .willReturn(ok()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(method);
      request.setUrl(
          wmRuntimeInfo.getHttpBaseUrl() + "/path%20with%20spaces?andQuery=Param with space");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
    }

    @ParameterizedTest
    @EnumSource(HttpMethod.class)
    public void shouldKeepOriginalEscaping_whenSkipEscapingIsSet(
        HttpMethod method, WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(any(urlEqualTo("/path%2Fwith%2Fencoding")).willReturn(ok()));
      stubFor(any(urlEqualTo("/path/with/encoding")).willReturn(badRequest()));
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(method);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path%2Fwith%2Fencoding");
      request.setSkipEncoding("true");

      HttpCommonResult result = customApacheHttpClient.execute(request);

      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
    }
  }

  @Nested
  class GetTests {

    @Test
    public void shouldReturn200_whenNullHeaders(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(get("/path").willReturn(ok()));
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      var headers = new HashMap<String, String>();
      headers.put("Content-Type", null);
      request.setHeaders(headers);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
    }

    @Test
    public void shouldReturn200_whenNoTimeouts(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(get("/path").willReturn(ok()));
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setConnectionTimeoutInSeconds(null);
      request.setReadTimeoutInSeconds(null);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
    }

    @Test
    public void shouldReturn200WithoutBody_whenEmptyGet(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(get("/path").willReturn(ok()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
    }

    private static Stream<Arguments> provideTestDataForHeaderTest() {
      return Stream.of(
          Arguments.of("Set-Cookie", "false", false, "Test-Value-1"),
          Arguments.of("Set-Cookie", "true", true, List.of("Test-Value-1", "Test-Value-2")),
          Arguments.of("other-than-set-cookie", "false", false, "Test-Value-1"),
          Arguments.of("other-than-set-cookie", "true", false, "Test-Value-1"));
    }

    @ParameterizedTest
    @MethodSource("provideTestDataForHeaderTest")
    public void shouldReturn200_whenDuplicatedHeadersAsListDisabled(
        String headerKey,
        String groupSetCookieHeaders,
        Boolean expectedDoesReturnList,
        Object expectedValue,
        WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(get("/path").willReturn(ok().withHeader(headerKey, "Test-Value-1", "Test-Value-2")));
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      request.setGroupSetCookieHeaders(groupSetCookieHeaders);
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      assertThat(result.headers().get(headerKey) instanceof List).isEqualTo(expectedDoesReturnList);
      assertThat(result.headers().get(headerKey)).isEqualTo(expectedValue);
    }

    @Test
    public void shouldReturn200WithBody_whenGetWithBody(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(get("/path").willReturn(ok("Hello, world!")));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      assertThat(result.body()).isEqualTo("Hello, world!");
    }

    @Test
    public void shouldReturn200WithBody_whenGetWithBodyJSON(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(
          get("/path")
              .willReturn(
                  ok().withJsonBody(
                          JsonNodeFactory.instance
                              .objectNode()
                              .put("name", "John")
                              .put("age", 30)
                              .putNull("message"))));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setHeaders(Map.of("Accept", "application/json"));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      JSONAssert.assertEquals(
          "{\"name\":\"John\",\"age\":30,\"message\":null}",
          objectMapper.writeValueAsString(result.body()),
          JSONCompareMode.STRICT);
    }

    @ParameterizedTest
    @EnumSource(HttpMethod.class)
    public void shouldReturn200WithBody_whenGetWithBodyXML(
        HttpMethod method, WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubFor(
          any(urlEqualTo("/path?format=xml"))
              .willReturn(
                  ok().withBody(
                          "<note>\n"
                              + "  <to>Tove</to>\n"
                              + "  <from>Jani</from>\n"
                              + "  <heading>Reminder</heading>\n"
                              + "  <body>Don't forget me this weekend!</body>\n"
                              + "</note>")));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(method);
      request.setQueryParameters(Map.of("format", "xml"));
      request.setHeaders(Map.of("Accept", "application/xml"));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      assertThat(result.body())
          .isEqualTo(
              "<note>\n"
                  + "  <to>Tove</to>\n"
                  + "  <from>Jani</from>\n"
                  + "  <heading>Reminder</heading>\n"
                  + "  <body>Don't forget me this weekend!</body>\n"
                  + "</note>");
    }

    @Test
    public void shouldReturn500_whenGetWithInvalidBody(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(get("/path").willReturn(serverError().withStatusMessage("Invalid JSON")));
      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      ConnectorException e =
          assertThrows(ConnectorException.class, () -> customApacheHttpClient.execute(request));
      assertThat(e.getErrorCode()).isEqualTo("500");
      assertThat(e.getMessage()).contains("Invalid JSON");
    }

    @Test
    public void shouldReturn404_whenGetWithNonExistingPath(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(get("/path").willReturn(notFound().withBody("Not Found: /path")));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      ConnectorException e =
          assertThrows(ConnectorException.class, () -> customApacheHttpClient.execute(request));
      assertThat(e.getErrorCode()).isEqualTo("404");
      assertThat(e.getMessage()).contains("Not Found");
      assertThat(((Map) e.getErrorVariables().get("response")).get("body"))
          .isEqualTo("Not Found: /path");
    }

    @Test
    public void shouldReturn408_whenGetWithTimeout(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(get("/path").willReturn(ok().withFixedDelay(2000)));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      request.setReadTimeoutInSeconds(1);
      ConnectorException e =
          assertThrows(ConnectorException.class, () -> customApacheHttpClient.execute(request));
      assertThat(e.getErrorCode()).isEqualTo(String.valueOf(HttpStatus.SC_REQUEST_TIMEOUT));
      assertThat(e.getMessage())
          .contains("An error occurred while executing the request, or the connection was aborted");
    }
  }

  @Nested
  class PostTests {

    @Test
    public void shouldReturn201WithoutBody_whenEmptyPost(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(post("/path").willReturn(created()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);
    }

    @Test
    public void shouldReturn201WithBody_whenPostBody(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(post("/path").willReturn(created()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setHeaders(Map.of("header", "headerValue"));
      var bodyMap = new HashMap<>();
      bodyMap.put("key1", "value1");
      bodyMap.put("nullKey", null);
      request.setBody(bodyMap);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);

      verify(
          postRequestedFor(urlEqualTo("/path"))
              .withHeader("Content-Type", equalTo("application/json"))
              .withHeader("header", equalTo("headerValue"))
              .withRequestBody(
                  equalTo(
                      StringEscapeUtils.unescapeJson("{\"key1\":\"value1\",\"nullKey\":null}"))));
    }

    @Test
    public void shouldReturn201WithBody_whenPostBodyURLEncoded(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(post("/path").willReturn(created()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setHeaders(
          Map.of(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED.getMimeType()));
      request.setBody(Map.of("key1", "value1", "key2", "value2"));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);

      verify(
          postRequestedFor(urlEqualTo("/path"))
              .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
              .withRequestBody(
                  and(containing("key1=value1"), containing("&"), containing("key2=value2"))));
    }

    @Test
    public void shouldReturn201WithBody_whenPostBodyMultiPart(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(post("/path").withMultipartRequestBody(aMultipart()).willReturn(created()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setHeaders(
          Map.of(HttpHeaders.CONTENT_TYPE, ContentType.MULTIPART_FORM_DATA.getMimeType()));
      request.setBody(Map.of("key1", "value1", "key2", "value2"));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);

      verify(
          postRequestedFor(urlEqualTo("/path"))
              .withHeader(
                  "Content-Type", and(containing("multipart/form-data"), containing("boundary=")))
              .withRequestBodyPart(
                  new MultipartValuePatternBuilder()
                      .withName("key1")
                      .withBody(equalTo("value1"))
                      .build())
              .withRequestBodyPart(
                  new MultipartValuePatternBuilder()
                      .withName("key2")
                      .withBody(equalTo("value2"))
                      .build()));
    }

    @Test
    public void shouldReturn201WithBody_whenPostBodyMultiPartWithBoundaryProvided(
        WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(post("/path").withMultipartRequestBody(aMultipart()).willReturn(created()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setHeaders(
          Map.of(
              HttpHeaders.CONTENT_TYPE,
              "multipart/form-data; charset=ISO-8859-1; boundary=g7wNbtOKHnEq4vnSoWdDYS88OICfGHzBA68DqmJS"));
      request.setBody(Map.of("key1", "value1", "key2", "value2"));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);

      verify(
          postRequestedFor(urlEqualTo("/path"))
              .withHeader(
                  "Content-Type",
                  and(
                      containing("multipart/form-data"),
                      containing("boundary=g7wNbtOKHnEq4vnSoWdDYS88OICfGHzBA68DqmJS")))
              .withRequestBodyPart(
                  new MultipartValuePatternBuilder()
                      .withName("key1")
                      .withBody(equalTo("value1"))
                      .build())
              .withRequestBodyPart(
                  new MultipartValuePatternBuilder()
                      .withName("key2")
                      .withBody(equalTo("value2"))
                      .build()));
    }

    @Test
    public void shouldReturn201WithBody_whenPostBodyTextPlainWithStringBody(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubFor(post("/path").willReturn(created()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setHeaders(Map.of(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.getMimeType()));
      request.setBody("Hello, world!");
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);

      verify(
          postRequestedFor(urlEqualTo("/path"))
              .withHeader("Content-Type", equalTo("text/plain"))
              .withRequestBody(equalTo("Hello, world!")));
    }

    @Test
    public void shouldReturn201WithBody_whenPostBodyTextPlainWithIntegerBody(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubFor(post("/path").willReturn(created()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setHeaders(Map.of(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.getMimeType()));
      request.setBody(123);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);

      verify(
          postRequestedFor(urlEqualTo("/path"))
              .withHeader("Content-Type", equalTo("text/plain"))
              .withRequestBody(equalTo("123")));
    }

    @Test
    public void shouldReturn200WithBody_whenPostBodyTextPlainWithBooleanBody(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubFor(post("/path").willReturn(created()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.POST);
      request.setHeaders(Map.of(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.getMimeType()));
      request.setBody(true);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(201);

      verify(
          postRequestedFor(urlEqualTo("/path"))
              .withHeader("Content-Type", equalTo("text/plain"))
              .withRequestBody(equalTo("true")));
    }
  }

  @Nested
  class DeleteTests {
    @Test
    public void shouldReturn204WithoutBody_whenDelete(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(delete("/path/id").willReturn(noContent()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.DELETE);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path/id");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.body()).isNull();
      assertThat(result.status()).isEqualTo(204);
    }
  }

  @Nested
  class PutTests {
    @Test
    public void shouldReturn200WithoutBody_whenEmptyPut(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(put("/path").willReturn(ok()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.PUT);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
    }

    @Test
    public void shouldReturn200WithBody_whenPutBody(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(put("/path").willReturn(ok()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.PUT);
      request.setHeaders(Map.of("header", "headerValue"));
      request.setBody(Map.of("key1", "value1"));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);

      verify(
          putRequestedFor(urlEqualTo("/path"))
              .withHeader("Content-Type", equalTo("application/json"))
              .withHeader("header", equalTo("headerValue"))
              .withRequestBody(equalTo(StringEscapeUtils.unescapeJson("{\"key1\":\"value1\"}"))));
    }

    @Test
    public void shouldReturn200WithBody_whenPutBodyURLEncoded(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(put("/path").willReturn(ok()));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.PUT);
      request.setHeaders(
          Map.of(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED.getMimeType()));
      request.setBody(Map.of("key1", "value1", "key2", "value2"));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);

      verify(
          putRequestedFor(urlEqualTo("/path"))
              .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
              .withRequestBody(
                  and(containing("key1=value1"), containing("&"), containing("key2=value2"))));
    }

    @Test
    public void shouldReturn200WithBody_whenPutBodyTextPlainWithStringBody(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubFor(put("/path").willReturn(ok().withBody("Hello, world updated!")));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.PUT);
      request.setHeaders(Map.of(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.getMimeType()));
      request.setBody("Hello, world!");
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      assertThat(result.body()).isEqualTo("Hello, world updated!");
    }

    @Test
    public void shouldReturn200WithBody_whenPutBodyTextPlainWithIntegerBody(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubFor(put("/path").willReturn(ok().withBody("123")));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.PUT);
      request.setHeaders(Map.of(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.getMimeType()));
      request.setBody(123);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      assertThat(result.body()).isEqualTo("123");
    }
  }

  @Nested
  class AuthenticationTests {

    @Test
    public void shouldReturn200WithBody_whenGetWithBasicAuth(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(
          get("/path")
              .withBasicAuth("user", "password")
              .willReturn(
                  ok().withJsonBody(
                          JsonNodeFactory.instance
                              .objectNode()
                              .put("name", "John")
                              .put("age", 30)
                              .putNull("message"))));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setHeaders(Map.of("Accept", "application/json"));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      request.setAuthentication(new BasicAuthentication("user", "password"));
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      JSONAssert.assertEquals(
          "{\"name\":\"John\",\"age\":30,\"message\":null}",
          objectMapper.writeValueAsString(result.body()),
          JSONCompareMode.STRICT);
    }

    @Test
    public void shouldReturn401_whenGetWithWrongBasicAuth(WireMockRuntimeInfo wmRuntimeInfo) {
      stubFor(
          get("/path")
              .withBasicAuth("user", "password")
              .willReturn(unauthorized().withBody("Unauthorized")));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      request.setAuthentication(new BasicAuthentication("user", "password"));
      ConnectorException e =
          assertThrows(ConnectorException.class, () -> customApacheHttpClient.execute(request));
      assertThat(e.getErrorCode()).isEqualTo("401");
      assertThat(e.getMessage()).contains("Unauthorized");
    }

    @Test
    public void shouldReturn200WithBody_whenGetWithBearerAuth(WireMockRuntimeInfo wmRuntimeInfo)
        throws Exception {
      stubFor(
          get("/path")
              .withHeader("Authorization", equalTo("Bearer token"))
              .willReturn(
                  ok().withJsonBody(
                          JsonNodeFactory.instance
                              .objectNode()
                              .put("name", "John")
                              .put("age", 30)
                              .putNull("message"))));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setAuthentication(new BearerAuthentication("token"));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      JSONAssert.assertEquals(
          "{\"name\":\"John\",\"age\":30,\"message\":null}",
          objectMapper.writeValueAsString(result.body()),
          JSONCompareMode.STRICT);
    }

    @Test
    public void shouldReturn200WithBody_whenGetWithApiKeyAuthInHeaders(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubFor(
          get("/path")
              .withHeader("theName", equalTo("theValue"))
              .willReturn(
                  ok().withJsonBody(
                          JsonNodeFactory.instance
                              .objectNode()
                              .put("name", "John")
                              .put("age", 30)
                              .putNull("message"))));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setAuthentication(
          new ApiKeyAuthentication(ApiKeyLocation.HEADERS, "theName", "theValue"));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      JSONAssert.assertEquals(
          "{\"name\":\"John\",\"age\":30,\"message\":null}",
          objectMapper.writeValueAsString(result.body()),
          JSONCompareMode.STRICT);
    }

    @Test
    public void shouldReturn200WithBody_whenGetWithApiKeyAuthInQueryParams(
        WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      stubFor(
          get(urlPathEqualTo("/path"))
              .withQueryParam("theName", equalTo("theValue"))
              .willReturn(
                  ok().withJsonBody(
                          JsonNodeFactory.instance
                              .objectNode()
                              .put("name", "John")
                              .put("age", 30)
                              .putNull("message"))));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setAuthentication(
          new ApiKeyAuthentication(ApiKeyLocation.QUERY, "theName", "theValue"));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      JSONAssert.assertEquals(
          "{\"name\":\"John\",\"age\":30,\"message\":null}",
          objectMapper.writeValueAsString(result.body()),
          JSONCompareMode.STRICT);
    }

    @ParameterizedTest
    @ValueSource(strings = {OAuthConstants.BASIC_AUTH_HEADER, OAuthConstants.CREDENTIALS_BODY})
    public void shouldReturn200WithBody_whenGetWithOAuthAndCredentialsInBody(
        String credentialsLocation, WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      createAuthServer(credentialsLocation);
      stubFor(
          get("/path")
              .withHeader("Authorization", equalTo("Bearer token"))
              .willReturn(
                  ok().withJsonBody(
                          JsonNodeFactory.instance
                              .objectNode()
                              .put("name", "John")
                              .put("age", 30)
                              .putNull("message"))));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setAuthentication(
          new OAuthAuthentication(
              wmRuntimeInfo.getHttpBaseUrl() + "/oauth",
              "clientId",
              "clientSecret",
              "theAudience",
              credentialsLocation,
              "read:resource"));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      HttpCommonResult result = customApacheHttpClient.execute(request);
      assertThat(result).isNotNull();
      assertThat(result.status()).isEqualTo(200);
      JSONAssert.assertEquals(
          "{\"name\":\"John\",\"age\":30,\"message\":null}",
          objectMapper.writeValueAsString(result.body()),
          JSONCompareMode.STRICT);
    }

    @ParameterizedTest
    @ValueSource(strings = {OAuthConstants.BASIC_AUTH_HEADER, OAuthConstants.CREDENTIALS_BODY})
    public void shouldReturn401_whenGetWithOAuthReturns401(
        String credentialsLocation, WireMockRuntimeInfo wmRuntimeInfo) throws Exception {
      createFailingAuthServer(credentialsLocation);
      stubFor(
          get("/path")
              .withHeader("Authorization", equalTo("Bearer token"))
              .willReturn(
                  ok().withJsonBody(
                          JsonNodeFactory.instance
                              .objectNode()
                              .put("name", "John")
                              .put("age", 30)
                              .putNull("message"))));

      HttpCommonRequest request = new HttpCommonRequest();
      request.setMethod(HttpMethod.GET);
      request.setAuthentication(
          new OAuthAuthentication(
              wmRuntimeInfo.getHttpBaseUrl() + "/oauth",
              "clientId",
              "clientSecret",
              "theAudience",
              credentialsLocation,
              "read:resource"));
      request.setUrl(wmRuntimeInfo.getHttpBaseUrl() + "/path");
      var e = assertThrows(ConnectorException.class, () -> customApacheHttpClient.execute(request));
      assertThat(e).isNotNull();
      assertThat(e.getErrorCode()).isEqualTo("401");
      assertThat(e.getMessage()).contains("Unauthorized");
    }

    private void createAuthServer(String credentialsLocation) {
      var request =
          post("/oauth")
              .withHeader(
                  HttpHeaders.CONTENT_TYPE,
                  equalTo(ContentType.APPLICATION_FORM_URLENCODED.getMimeType()))
              .withFormParam("grant_type", equalTo("client_credentials"))
              .withFormParam("audience", equalTo("theAudience"))
              .withFormParam("scope", equalTo("read:resource"));
      if (OAuthConstants.CREDENTIALS_BODY.equals(credentialsLocation)) {
        request
            .withFormParam("client_id", equalTo("clientId"))
            .withFormParam("client_secret", equalTo("clientSecret"));
      } else {
        request.withBasicAuth("clientId", "clientSecret");
      }
      stubFor(
          request.willReturn(
              ok().withJsonBody(
                      JsonNodeFactory.instance
                          .objectNode()
                          .put("access_token", "token")
                          .put("token_type", "Bearer")
                          .put("expires_in", 3600))));
    }

    private void createFailingAuthServer(String credentialsLocation) {
      var request =
          post("/oauth")
              .withHeader(
                  HttpHeaders.CONTENT_TYPE,
                  equalTo(ContentType.APPLICATION_FORM_URLENCODED.getMimeType()))
              .withFormParam("grant_type", equalTo("client_credentials"))
              .withFormParam("audience", equalTo("theAudience"))
              .withFormParam("scope", equalTo("read:resource"));
      if (OAuthConstants.CREDENTIALS_BODY.equals(credentialsLocation)) {
        request
            .withFormParam("client_id", equalTo("clientId"))
            .withFormParam("client_secret", equalTo("clientSecret"));
      } else {
        request.withBasicAuth("clientId", "clientSecret");
      }
      stubFor(request.willReturn(unauthorized().withBody("Unauthorized")));
    }
  }
}
