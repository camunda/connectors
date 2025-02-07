package io.camunda.connector.http.base.client.apache;

import io.camunda.connector.api.error.ConnectorInputException;
import java.net.*;
import java.util.Arrays;
import java.util.Optional;
import org.apache.commons.lang3.ArrayUtils;
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
  private String protocol;
  private String host;
  private int port;
  private String user;
  private char[] password;
  private String nonProxyHosts;
  private boolean sourceIsSystemProperties;

  public ProxyHandler(String uncheckedProtocol, String requestUrl) {
    protocol = fallbackOnHttpForInvalidProtocol(uncheckedProtocol);
    try {
      if (isValidProxyFromSystemProperties(protocol)) {
        setFromSystemProperties();
      } else if (isValidProxyFromEnvVars(protocol)) {
        setFromEnvVars();
        if (doesTargetMatchNonProxy(protocol, requestUrl)) {
          host = null;
        }
      }
    } catch (NumberFormatException e) {
      throw new ConnectorInputException(
          "Invalid proxy port: " + (System.getProperty(protocol + ".proxyPort")), e);
    }
  }

  private void setFromEnvVars() {
    host = System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_HOST");
    port = Integer.parseInt(System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_PORT"));
    user = System.getenv().getOrDefault("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_USER", "");
    password =
        System.getenv()
            .getOrDefault("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_PASSWORD", "")
            .toCharArray();
    nonProxyHosts = System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_NON_PROXY_HOSTS");
    sourceIsSystemProperties = false;
  }

  private void setFromSystemProperties() {
    host = System.getProperty(protocol + ".proxyHost");
    port = Integer.parseInt(System.getProperty(protocol + ".proxyPort"));
    user = System.getProperty(protocol + ".proxyUser");
    password =
        Optional.ofNullable(System.getProperty(protocol + ".proxyPassword"))
            .map(String::toCharArray)
            .orElseGet(() -> new char[0]);
    sourceIsSystemProperties = true;
  }

  public CredentialsProvider getCredentialsProvider(String uncheckedProtocol) {
    BasicCredentialsProvider provider = new BasicCredentialsProvider();
    if (StringUtils.isNotBlank(host)
        && StringUtils.isNotBlank(user)
        && ArrayUtils.isNotEmpty(password)) {
      provider.setCredentials(
          new AuthScope(host, port), new UsernamePasswordCredentials(user, password));
    }
    return provider;
  }

  public HttpHost getProxyHost(String uncheckedProtocol, String requestUri) {
    return host == null
        ? null
        : new HttpHost(fallbackOnHttpForInvalidProtocol(uncheckedProtocol), host, port);
  }

  private boolean doesTargetMatchNonProxy(String protocol, String requestUri) {
    return (nonProxyHosts != null
        && Arrays.stream(nonProxyHosts.split("\\|"))
            .anyMatch(
                nonProxyHost ->
                    requestUri.matches(nonProxyHost.replace(".", "\\.").replace("*", ".*"))));
  }

  public HttpRoutePlanner getRoutePlanner(String uncheckedProtocol, HttpHost proxyHost) {
    if (sourceIsSystemProperties) {
      return new SystemDefaultRoutePlanner(
          DefaultSchemePortResolver.INSTANCE, ProxySelector.getDefault());
    } else if (proxyHost != null) {
      return new DefaultProxyRoutePlanner(proxyHost);
    }
    return null;
  }

  private static String fallbackOnHttpForInvalidProtocol(String protocol) {
    return protocol != null && protocol.equalsIgnoreCase("https") ? "https" : "http";
  }

  private static boolean isValidProxyFromEnvVars(String protocol) {
    return StringUtils.isNotBlank(
            System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_HOST"))
        && StringUtils.isNotBlank(
            System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_PORT"));
  }

  private static boolean isValidProxyFromSystemProperties(String protocol) {
    return (StringUtils.isNotBlank(System.getProperty(protocol + ".proxyHost"))
        && StringUtils.isNotBlank(System.getProperty(protocol + ".proxyPort")));
  }
}
