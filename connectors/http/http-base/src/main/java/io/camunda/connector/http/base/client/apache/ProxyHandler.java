package io.camunda.connector.http.base.client.apache;

import io.camunda.connector.api.error.ConnectorInputException;
import java.net.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyHandler {
  record ProxyDetails(
      String protocol,
      String host,
      int port,
      String user,
      char[] password,
      String nonProxyHosts,
      boolean sourceIsSystemProperties) {}

  private Map<String, ProxyDetails> map = new HashMap<>();
  private static final Logger LOGGER = LoggerFactory.getLogger(ProxyHandler.class);

  public ProxyHandler() {
    try {
      setFromEnvVars("http");
      setFromEnvVars("https");
      setFromSystemProperties("http");
      setFromSystemProperties("https");
    } catch (NumberFormatException e) {
      throw new ConnectorInputException("Invalid proxy port", e);
    }
  }

  private void setFromEnvVars(String protocol) throws NumberFormatException {
    if (isValidProxyFromEnvVars(protocol)) {
      String host = System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_HOST");
      int port =
          Integer.parseInt(System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_PORT"));
      String user =
          System.getenv().getOrDefault("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_USER", "");
      char[] password =
          System.getenv()
              .getOrDefault("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_PASSWORD", "")
              .toCharArray();
      String nonProxyHosts =
          System.getenv("CONNECTOR_" + protocol.toUpperCase() + "_PROXY_NON_PROXY_HOSTS");
      map.put(
          protocol, new ProxyDetails(protocol, host, port, user, password, nonProxyHosts, false));
      LOGGER.debug("Proxy settings applied from environment variables for {}", protocol);
    }
  }

  private void setFromSystemProperties(String protocol) throws NumberFormatException {
    if (isValidProxyFromSystemProperties(protocol)) {
      String host = System.getProperty(protocol + ".proxyHost");
      int port = Integer.parseInt(System.getProperty(protocol + ".proxyPort"));
      String user = System.getProperty(protocol + ".proxyUser");
      char[] password =
          Optional.ofNullable(System.getProperty(protocol + ".proxyPassword"))
              .map(String::toCharArray)
              .orElseGet(() -> new char[0]);
      String nonProxyHosts = System.getProperty(protocol + ".nonProxyHosts");
      map.put(
          protocol, new ProxyDetails(protocol, host, port, user, password, nonProxyHosts, true));
      LOGGER.debug("Proxy settings applied from system properties for {}", protocol);
    }
  }

  public CredentialsProvider getCredentialsProvider(String protocol) {
    BasicCredentialsProvider provider = new BasicCredentialsProvider();
    ProxyDetails p = map.get(protocol);
    if (p != null
        && StringUtils.isNotBlank(p.host())
        && StringUtils.isNotBlank(p.user())
        && ArrayUtils.isNotEmpty(p.password())) {
      provider.setCredentials(
          new AuthScope(p.host(), p.port()),
          new UsernamePasswordCredentials(p.user(), p.password()));
    }
    return provider;
  }

  public HttpHost getProxyHost(String protocol, String requestUrl) {
    ProxyDetails p = map.get(protocol);
    return p == null || doesTargetMatchNonProxy(protocol, requestUrl)
        ? null
        : new HttpHost(p.protocol(), p.host(), p.port());
  }

  private boolean doesTargetMatchNonProxy(String protocol, String requestUri) {
    ProxyDetails p = map.get(protocol);
    return (p != null
        && p.nonProxyHosts() != null
        && Arrays.stream(p.nonProxyHosts().split("\\|"))
            .anyMatch(
                nonProxyHost ->
                    requestUri.matches(nonProxyHost.replace(".", "\\.").replace("*", ".*"))));
  }

  public HttpRoutePlanner getRoutePlanner(String protocol, HttpHost proxyHost) {
    ProxyDetails p = map.get(protocol);
    if (p != null && p.sourceIsSystemProperties()) {
      return new SystemDefaultRoutePlanner(
          DefaultSchemePortResolver.INSTANCE, ProxySelector.getDefault());
    } else if (proxyHost != null) {
      return new DefaultProxyRoutePlanner(proxyHost);
    }
    return null;
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
