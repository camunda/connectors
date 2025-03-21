/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
