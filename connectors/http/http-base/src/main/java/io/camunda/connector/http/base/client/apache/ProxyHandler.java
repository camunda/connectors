package io.camunda.connector.http.base.client.apache;

import io.camunda.connector.api.error.ConnectorInputException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.core5.http.HttpHost;

public class ProxyHandler {

  record CustomProxy(String host, int port, String username, String password) {}

  public static CredentialsProvider getCredentialsProvider(String uncheckedProtocol) {
    BasicCredentialsProvider provider = new BasicCredentialsProvider();
    String protocol = fallbackOnHttpForInvalidProtocol(uncheckedProtocol);
    if (isValidProxyWithAuth(protocol)) {
      provider.setCredentials(
          new AuthScope(
              System.getProperty(protocol + ".proxyHost"),
              Integer.parseInt(System.getProperty(protocol + ".proxyPort"))),
          new UsernamePasswordCredentials(
              System.getProperty(protocol + ".proxyUser"),
              System.getProperty(protocol + ".proxyPassword").toCharArray()));
    } else if (systemEnvIsSetForProtocol(protocol)) {
      CustomProxy proxyData = getProxySettingsFromEnvVar(protocol);
      provider.setCredentials(
          new AuthScope(proxyData.host, proxyData.port),
          new UsernamePasswordCredentials(proxyData.username, proxyData.password.toCharArray()));
    }
    return provider;
  }

  public static HttpHost getProxyHost(String uncheckedProtocol) {
    String protocol = fallbackOnHttpForInvalidProtocol(uncheckedProtocol);
    if (isValidProxy(protocol)) {
      return new HttpHost(
          protocol,
          System.getProperty(protocol + ".proxyHost"),
          Integer.parseInt(System.getProperty(protocol + ".proxyPort")));
    } else if (systemEnvIsSetForProtocol(protocol)) {
      var proxyData = getProxySettingsFromEnvVar(protocol);
      return new HttpHost(protocol, proxyData.host, proxyData.port);
    }
    return null;
  }

  public static HttpRoutePlanner getRoutePlanner(String uncheckedProtocol, HttpHost proxyHost) {
    String protocol = fallbackOnHttpForInvalidProtocol(uncheckedProtocol);
    if (isValidProxy(protocol)) {
      return new SystemDefaultRoutePlanner(
          DefaultSchemePortResolver.INSTANCE, ProxySelector.getDefault());
    } else if (System.getenv(protocol.toUpperCase() + "_PROXY") != null) {
      return new DefaultProxyRoutePlanner(proxyHost);
    }
    return null;
  }

  private static String fallbackOnHttpForInvalidProtocol(String protocol) {
    return protocol != null && protocol.equalsIgnoreCase("https") ? "https" : "http";
  }

  private static boolean systemEnvIsSetForProtocol(String protocol) {
    return StringUtils.isNotBlank(System.getenv(protocol.toUpperCase() + "_PROXY"));
  }

  private static boolean isValidProxy(String protocol) {
    return (StringUtils.isNotBlank(System.getProperty(protocol + ".proxyHost"))
        && StringUtils.isNotBlank(System.getProperty(protocol + ".proxyPort")));
  }

  private static boolean isValidProxyWithAuth(String protocol) {
    return (StringUtils.isNotBlank(System.getProperty(protocol + ".proxyHost"))
        && StringUtils.isNotBlank(System.getProperty(protocol + ".proxyPort"))
        && StringUtils.isNotBlank(System.getProperty(protocol + ".proxyUser"))
        && StringUtils.isNotBlank(System.getProperty(protocol + ".proxyPassword")));
  }

  private static CustomProxy getProxySettingsFromEnvVar(String protocol) {
    String proxyString = System.getenv(protocol.toUpperCase() + "_PROXY");
    String user = "";
    String password = "";

    try {
      URLDecoder.decode(proxyString, StandardCharsets.UTF_8); // Check if URL is correctly encoded.
      URL url = new URI(proxyString).toURL();
      String userInfo = url.getUserInfo();

      if (userInfo != null) {
        String[] credentials = userInfo.split(":");
        if (credentials.length == 2) {
          user = URLDecoder.decode(credentials[0], StandardCharsets.UTF_8);
          password = URLDecoder.decode(credentials[1], StandardCharsets.UTF_8);
        }
      }

      return new CustomProxy(url.getHost(), url.getPort(), user, password);
    } catch (MalformedURLException | URISyntaxException | IllegalArgumentException e) {
      throw new ConnectorInputException(
          "Invalid proxy settings. Proxy environment variable is incorrect: " + e.getMessage(), e);
    }
  }
}
