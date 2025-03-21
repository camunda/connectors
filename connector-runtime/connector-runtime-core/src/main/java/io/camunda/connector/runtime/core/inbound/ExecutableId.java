package io.camunda.connector.runtime.core.inbound;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * This class represents the hashed deduplication id of an executable. The deduplication id is
 * hashed using SHA-256 algorithm.
 *
 * <p>The goal of this class is to provide a unique identifier for an executable that can be used
 * across different Connectors Runtime and remain stable.
 */
public class ExecutableId {
  private String id;

  ExecutableId() {}

  public ExecutableId(String deduplicationId) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(deduplicationId.getBytes());
      id = HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  @JsonCreator
  public static ExecutableId fromString(String hashedValue) {
    var hashed = new ExecutableId();
    hashed.id = hashedValue;
    return hashed;
  }

  @JsonValue
  public String getId() {
    return id;
  }
}
