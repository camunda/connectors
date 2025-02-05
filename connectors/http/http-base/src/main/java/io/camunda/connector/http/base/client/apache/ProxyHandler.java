package io.camunda.connector.http.base.client.apache;

import io.camunda.connector.api.error.ConnectorInputException;
import java.net.*;
import java.util.Arrays;
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
    try {
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
    } catch (NumberFormatException e) {
      throw new ConnectorInputException(
          "Invalid proxy port: " + (System.getProperty(protocol + ".proxyPort")), e);
    }
    return provider;
  }

  public static HttpHost getProxyHost(String uncheckedProtocol, String requestUri) {
    String protocol = fallbackOnHttpForInvalidProtocol(uncheckedProtocol);
    try {
      if (isValidProxy(protocol)) {
        return new HttpHost(
            protocol,
            System.getProperty(protocol + ".proxyHost"),
            Integer.parseInt(System.getProperty(protocol + ".proxyPort")));
      } else if (systemEnvIsSetForProtocol(protocol)
          && !doesTargetMatchNonProxy(protocol, requestUri)) {
        var proxyData = getProxySettingsFromEnvVar(protocol);
        return new HttpHost(protocol, proxyData.host, proxyData.port);
      }
    } catch (NumberFormatException e) {
      throw new ConnectorInputException(
          "Invalid proxy port: " + (System.getProperty(protocol + ".proxyPort")), e);
    }
    return null;
  }

  private static boolean doesTargetMatchNonProxy(String protocol, String requestUri) {
    String nonProxyHostsEnvVar =
        System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_NON_PROXY_HOSTS");
    return (nonProxyHostsEnvVar != null
        && Arrays.stream(nonProxyHostsEnvVar.split("\\|"))
            .anyMatch(
                nonProxyHost ->
                    requestUri.matches(nonProxyHost.replace(".", "\\.").replace("*", ".*"))));
  }

  public static HttpRoutePlanner getRoutePlanner(String uncheckedProtocol, HttpHost proxyHost) {
    String protocol = fallbackOnHttpForInvalidProtocol(uncheckedProtocol);
    if (isValidProxy(protocol)) {
      return new SystemDefaultRoutePlanner(
          DefaultSchemePortResolver.INSTANCE, ProxySelector.getDefault());
    } else if (systemEnvIsSetForProtocol(protocol) && proxyHost != null) {
      return new DefaultProxyRoutePlanner(proxyHost);
    }
    return null;
  }

  private static String fallbackOnHttpForInvalidProtocol(String protocol) {
    return protocol != null && protocol.equalsIgnoreCase("https") ? "https" : "http";
  }

  private static boolean systemEnvIsSetForProtocol(String protocol) {
    return StringUtils.isNotBlank(
            System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_HOST"))
        && StringUtils.isNotBlank(
            System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_PORT"));
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

  private static CustomProxy getProxySettingsFromEnvVar(String protocol)
      throws NumberFormatException {
    String proxyHost = System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_HOST");
    String proxyPort = System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_PORT");
    String proxyUser =
        System.getenv().getOrDefault("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_USER", "");
    String proxyPassword =
        System.getenv().getOrDefault("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_PASSWORD", "");

    return new CustomProxy(proxyHost, Integer.parseInt(proxyPort), proxyUser, proxyPassword);
  }
}
