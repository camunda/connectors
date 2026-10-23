/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.proxy.aws;

import static io.camunda.connector.http.client.proxy.ProxyConfiguration.SCHEME_HTTP;
import static io.camunda.connector.http.client.proxy.ProxyConfiguration.SCHEME_HTTPS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.http.client.proxy.NonProxyHosts;
import io.camunda.connector.http.client.proxy.ProxyConfiguration;
import io.camunda.connector.http.client.proxy.ProxyConfiguration.ProxyDetails;
import java.net.URI;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.http.apache.ApacheHttpClient;

@ExtendWith(MockitoExtension.class)
class AwsSdkHttpProxySupportTest {

  private static final String PROXY_HOST = "proxy.example.com";
  private static final int PROXY_PORT = 8080;
  private static final String PROXY_USER = "proxyuser";
  private static final String PROXY_PASSWORD = "proxypass";
  private static final String NON_PROXY_HOST_LOCALHOST_REGEX = "localhost.*";
  private static final String NON_PROXY_HOST_127 = "127\\.0\\.0\\.1";

  @Mock private ProxyConfiguration proxyConfiguration;

  private AwsSdkHttpProxySupport proxySupport;

  @BeforeEach
  void setUp() {
    proxySupport = new AwsSdkHttpProxySupport(proxyConfiguration);
  }

  @Test
  void shouldConfigureProxyForHttpsEndpoint() {
    // given
    when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.empty());

    ApacheHttpClient.Builder httpClientBuilder =
        Mockito.mock(ApacheHttpClient.Builder.class, Answers.RETURNS_SELF);

    try (MockedStatic<ApacheHttpClient> apacheMock = mockStatic(ApacheHttpClient.class)) {
      apacheMock.when(ApacheHttpClient::builder).thenReturn(httpClientBuilder);

      // when
      ApacheHttpClient.Builder result =
          proxySupport.httpClientBuilder(URI.create("https://bedrock.amazonaws.com"));

      // then
      assertThat(result).isSameAs(httpClientBuilder);
      verify(proxyConfiguration).getProxyDetails(SCHEME_HTTPS);
      verify(httpClientBuilder)
          .proxyConfiguration(notNull(software.amazon.awssdk.http.apache.ProxyConfiguration.class));
    }
  }

  @Test
  void shouldConfigureProxyForHttpEndpoint() {
    // given
    when(proxyConfiguration.getProxyDetails(SCHEME_HTTP)).thenReturn(Optional.empty());

    ApacheHttpClient.Builder httpClientBuilder =
        Mockito.mock(ApacheHttpClient.Builder.class, Answers.RETURNS_SELF);

    try (MockedStatic<ApacheHttpClient> apacheMock = mockStatic(ApacheHttpClient.class)) {
      apacheMock.when(ApacheHttpClient::builder).thenReturn(httpClientBuilder);

      // when
      proxySupport.httpClientBuilder(URI.create("http://localhost:8080"));

      // then
      verify(proxyConfiguration).getProxyDetails(SCHEME_HTTP);
    }
  }

  @Test
  void shouldDefaultToHttpsSchemeWhenEndpointIsNull() {
    // given
    when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.empty());

    ApacheHttpClient.Builder httpClientBuilder =
        Mockito.mock(ApacheHttpClient.Builder.class, Answers.RETURNS_SELF);

    try (MockedStatic<ApacheHttpClient> apacheMock = mockStatic(ApacheHttpClient.class)) {
      apacheMock.when(ApacheHttpClient::builder).thenReturn(httpClientBuilder);

      // when
      proxySupport.httpClientBuilder(null);

      // then
      verify(proxyConfiguration).getProxyDetails(SCHEME_HTTPS);
    }
  }

  @Test
  void shouldCreateAwsProxyConfigurationWithoutProxy() {
    // given
    when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.empty());

    // when
    var result = proxySupport.awsProxyConfiguration(SCHEME_HTTPS);

    // then
    assertThat(result).isNotNull();
    assertThat(result.host()).isNull();
    assertThat(result.username()).isNull();
    assertThat(result.password()).isNull();
    // Default scheme is HTTP when no proxy configured and useSystemPropertyValues is true
    assertThat(result.scheme()).isEqualTo(SCHEME_HTTP);
    verify(proxyConfiguration).getProxyDetails(SCHEME_HTTPS);
  }

  @Test
  void shouldCreateAwsProxyConfigurationWithProxy() {
    // given
    var proxyDetails = new ProxyDetails(SCHEME_HTTPS, PROXY_HOST, PROXY_PORT, null, null);
    when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.of(proxyDetails));

    try (MockedStatic<NonProxyHosts> nonProxyHostsMock = mockStatic(NonProxyHosts.class)) {
      nonProxyHostsMock
          .when(NonProxyHosts::getNonProxyHostRegexPatterns)
          .thenReturn(Stream.of(NON_PROXY_HOST_LOCALHOST_REGEX, NON_PROXY_HOST_127));

      // when
      var result = proxySupport.awsProxyConfiguration(SCHEME_HTTPS);

      // then
      assertThat(result).isNotNull();
      assertThat(result.host()).isEqualTo(PROXY_HOST);
      assertThat(result.port()).isEqualTo(PROXY_PORT);
      assertThat(result.scheme()).isEqualTo(SCHEME_HTTPS);
      assertThat(result.nonProxyHosts())
          .containsExactlyInAnyOrder(NON_PROXY_HOST_LOCALHOST_REGEX, NON_PROXY_HOST_127);
      assertThat(result.username()).isNull();
      assertThat(result.password()).isNull();
      verify(proxyConfiguration).getProxyDetails(SCHEME_HTTPS);
      nonProxyHostsMock.verify(NonProxyHosts::getNonProxyHostRegexPatterns);
    }
  }

  @Test
  void shouldCreateAwsProxyConfigurationProxyAndCredentials() {
    // given
    var proxyDetails =
        new ProxyDetails(SCHEME_HTTPS, PROXY_HOST, PROXY_PORT, PROXY_USER, PROXY_PASSWORD);
    when(proxyConfiguration.getProxyDetails(SCHEME_HTTPS)).thenReturn(Optional.of(proxyDetails));

    try (MockedStatic<NonProxyHosts> nonProxyHostsMock = mockStatic(NonProxyHosts.class)) {
      nonProxyHostsMock
          .when(NonProxyHosts::getNonProxyHostRegexPatterns)
          .thenReturn(Stream.of(NON_PROXY_HOST_LOCALHOST_REGEX));

      // when
      var result = proxySupport.awsProxyConfiguration(SCHEME_HTTPS);

      // then
      assertThat(result).isNotNull();
      assertThat(result.host()).isEqualTo(PROXY_HOST);
      assertThat(result.port()).isEqualTo(PROXY_PORT);
      assertThat(result.scheme()).isEqualTo(SCHEME_HTTPS);
      assertThat(result.nonProxyHosts()).containsExactly(NON_PROXY_HOST_LOCALHOST_REGEX);
      assertThat(result.username()).isEqualTo(PROXY_USER);
      assertThat(result.password()).isEqualTo(PROXY_PASSWORD);
      verify(proxyConfiguration).getProxyDetails(SCHEME_HTTPS);
    }
  }

  @Test
  void shouldHandleHttpScheme() {
    // given
    var proxyDetails = new ProxyDetails(SCHEME_HTTP, PROXY_HOST, PROXY_PORT, null, null);
    when(proxyConfiguration.getProxyDetails(SCHEME_HTTP)).thenReturn(Optional.of(proxyDetails));

    try (MockedStatic<NonProxyHosts> nonProxyHostsMock = mockStatic(NonProxyHosts.class)) {
      nonProxyHostsMock
          .when(NonProxyHosts::getNonProxyHostRegexPatterns)
          .thenReturn(Stream.empty());

      // when
      var result = proxySupport.awsProxyConfiguration(SCHEME_HTTP);

      // then
      assertThat(result).isNotNull();
      assertThat(result.host()).isEqualTo(PROXY_HOST);
      assertThat(result.port()).isEqualTo(PROXY_PORT);
      assertThat(result.scheme()).isEqualTo(SCHEME_HTTP);
      assertThat(result.nonProxyHosts()).isEmpty();
      assertThat(result.username()).isNull();
      assertThat(result.password()).isNull();
      verify(proxyConfiguration).getProxyDetails(SCHEME_HTTP);
    }
  }
}
