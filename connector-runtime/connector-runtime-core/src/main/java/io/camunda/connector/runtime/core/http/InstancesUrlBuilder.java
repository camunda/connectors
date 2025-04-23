package io.camunda.connector.runtime.core.http;

import java.util.List;

/** Interface for building URLs for multiple Connectors runtime instances. */
public interface InstancesUrlBuilder {

  /**
   * Builds a list of URLs for the given path. The URLs are constructed using the base URLs of the
   * Connectors runtime instances and the provided path.
   *
   * @param path the path to append to the base URLs
   * @return a list of constructed URLs
   */
  List<String> buildUrls(String path);
}
