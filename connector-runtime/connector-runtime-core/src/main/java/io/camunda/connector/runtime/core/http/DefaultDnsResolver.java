package io.camunda.connector.runtime.core.http;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of the DnsResolver interface. This class is responsible for resolving
 * hostnames to IP addresses. It uses the standard Java library to perform the DNS resolution.
 *
 * @see InetAddress
 */
public class DefaultDnsResolver implements DnsResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDnsResolver.class);

  @Override
  public String[] resolve(String host) {
    try {
      LOGGER.debug("Resolving host: {}", host);
      var resolvedAddresses =
          Stream.of(InetAddress.getAllByName(host))
              .map(InetAddress::getHostAddress)
              .toArray(String[]::new);
      LOGGER.debug("Resolved host addresses: {}", resolvedAddresses);
      return resolvedAddresses;
    } catch (UnknownHostException e) {
      LOGGER.error("No addresses found for service: {}.", host);
      throw new RuntimeException(e);
    }
  }
}
