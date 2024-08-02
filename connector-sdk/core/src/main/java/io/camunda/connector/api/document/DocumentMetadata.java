package io.camunda.connector.api.document;

import java.util.Map;

public interface DocumentMetadata {

  Map<String, Object> getKeys();

  Object getKey(String key);

  String getContentType();

  String getFileName();

  String getDescription();
}
