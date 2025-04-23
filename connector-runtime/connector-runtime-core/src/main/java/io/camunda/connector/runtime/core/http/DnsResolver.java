package io.camunda.connector.runtime.core.http;


public interface DnsResolver {
  String[] resolve(String host);
}
