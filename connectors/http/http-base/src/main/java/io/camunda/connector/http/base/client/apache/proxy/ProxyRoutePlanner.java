package io.camunda.connector.http.base.client.apache.proxy;

import static io.camunda.connector.http.base.client.apache.proxy.ProxyHandler.CONNECTOR_HTTP_NON_PROXY_HOSTS_ENV_VAR;

import java.util.Objects;
import java.util.stream.Stream;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyRoutePlanner extends DefaultProxyRoutePlanner {
  private static final Logger LOG = LoggerFactory.getLogger(ProxyRoutePlanner.class.getName());
  private static final String NON_PROXY_HOSTS_FROM_SYSTEM_PROPERTIES =
      System.getProperty("http.nonProxyHosts");
  private static final String NON_PROXY_HOSTS_FROM_ENV_VAR =
      System.getenv(CONNECTOR_HTTP_NON_PROXY_HOSTS_ENV_VAR);

  public ProxyRoutePlanner(HttpHost proxy) {
    super(proxy);
  }

  @Override
  protected HttpHost determineProxy(HttpHost target, HttpContext context) throws HttpException {
    if (Stream.of(NON_PROXY_HOSTS_FROM_SYSTEM_PROPERTIES, NON_PROXY_HOSTS_FROM_ENV_VAR)
        .filter(Objects::nonNull)
        .anyMatch(
            nonProxyHosts ->
                target.getHostName().matches(sanitizedNonProxyHostsRegex(nonProxyHosts)))) {
      LOG.debug(
          "Not using proxy for target host [{}] as it matched either system properties (http.nonProxyHosts) or environment variables ({})",
          target.getHostName(),
          CONNECTOR_HTTP_NON_PROXY_HOSTS_ENV_VAR);
      return null;
    }
    var proxy = super.determineProxy(target, context);
    LOG.debug("Using proxy for target host [{}] => [{}]", target.getHostName(), proxy);
    return proxy;
  }

  private String sanitizedNonProxyHostsRegex(String nonProxyHosts) {
    return nonProxyHosts != null
        ? nonProxyHosts.replace(
            "*", ".*") // This is required as the nonProxyHosts property uses * as a wildcard
        : null;
  }
}
