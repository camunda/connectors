package io.camunda.connector.http.client.model;

import java.io.InputStream;
import java.util.Optional;

public class CustomHttpBody implements AutoCloseable {

  private final InputStream inputStream;
  private final long contentLength;
  private final String contentType;
  // Cached content
  private byte[] cachedBytes;
  private String cachedString;
  private boolean isConsumed;

  public CustomHttpBody(InputStream inputStream, long contentLength, String contentType) {
    this.inputStream = inputStream;
    this.contentLength = contentLength;
    this.contentType = contentType;
    this.isConsumed = false;
  }

  public InputStream stream() {
    if (isConsumed) {
      throw new IllegalStateException("Response body has already been consumed.");
    }
    isConsumed = true;
    return inputStream;
  }

  /**
   * Returns the content length of the response body. -1 if unknown.
   */
  public long contentLength() {
    return contentLength;
  }

  /**
   * Returns the content type of the response body if available.
   */
  public Optional<String> contentType() {
    return Optional.ofNullable(contentType);
  }

  /**
   * Reads the entire response body as a byte array. Caches the result for subsequent calls.
   */
  public byte[] bytes() {
    if (cachedBytes != null) {
      return cachedBytes;
    }
    if (isConsumed) {
      throw new IllegalStateException("Response body has already been consumed.");
    }
    try {
      cachedBytes = inputStream.readAllBytes();
      isConsumed = true;
      return cachedBytes;
    } catch (Exception e) {
      throw new RuntimeException("Failed to read response body as bytes.", e);
    }
  }

  /**
   * Reads the entire response body as a String using the default charset. Caches the result for
   */
  public String string() {
    if (cachedString != null) {
      return cachedString;
    }
    byte[] bytes = bytes();
    cachedString = new String(bytes);
    return cachedString;
  }

  @Override
  public void close() throws Exception {
    inputStream.close();
  }
}
