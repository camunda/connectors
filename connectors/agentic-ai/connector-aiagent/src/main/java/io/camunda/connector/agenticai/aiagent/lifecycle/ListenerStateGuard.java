/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.lifecycle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Checks the state a listener contributes to the agent context. The state must be a tree of plain
 * JSON values: maps with string keys, lists, strings, booleans, finite numbers and null. Its JSON
 * serialization must not exceed a byte limit. Accepted state is returned as a deep copy, so later
 * changes by the listener cannot alter what is persisted.
 */
final class ListenerStateGuard {

  /** Maximum nesting of maps and lists; also rejects self-referencing structures. */
  private static final int MAX_DEPTH = 32;

  /** Why a contribution was rejected. The label is safe to log, it never contains state. */
  enum Rejection {
    UNSUPPORTED_VALUE("unsupported value type"),
    TOO_DEEP("nesting too deep"),
    NOT_SERIALIZABLE("not serializable"),
    TOO_LARGE("serialized size exceeds the limit");

    private final String label;

    Rejection(String label) {
      this.label = label;
    }

    String label() {
      return label;
    }
  }

  /** Either the accepted copy of the state or the reason it was rejected. */
  sealed interface Verdict {

    record Accepted(Map<String, Object> state) implements Verdict {}

    record Rejected(Rejection reason) implements Verdict {}
  }

  private final ObjectMapper objectMapper;
  private final int maxBytes;

  ListenerStateGuard(ObjectMapper objectMapper, int maxBytes) {
    this.objectMapper = objectMapper;
    this.maxBytes = maxBytes;
  }

  Verdict check(Map<String, Object> state) {
    final Map<String, Object> copy;
    try {
      copy = copyMap(state, 1);
    } catch (RejectedException e) {
      return new Verdict.Rejected(e.rejection);
    }
    try {
      if (objectMapper.writeValueAsBytes(copy).length > maxBytes) {
        return new Verdict.Rejected(Rejection.TOO_LARGE);
      }
    } catch (JsonProcessingException | RuntimeException e) {
      return new Verdict.Rejected(Rejection.NOT_SERIALIZABLE);
    }
    return new Verdict.Accepted(copy);
  }

  private static Map<String, Object> copyMap(Map<?, ?> map, int depth) {
    if (depth > MAX_DEPTH) {
      throw new RejectedException(Rejection.TOO_DEEP);
    }
    final var copy = new LinkedHashMap<String, Object>();
    for (final var entry : map.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        throw new RejectedException(Rejection.UNSUPPORTED_VALUE);
      }
      copy.put(key, copyValue(entry.getValue(), depth));
    }
    return copy;
  }

  private static @Nullable Object copyValue(@Nullable Object value, int depth) {
    return switch (value) {
      case null -> null;
      case String s -> s;
      case Boolean b -> b;
      case Number n -> {
        if (!Double.isFinite(n.doubleValue())) {
          throw new RejectedException(Rejection.UNSUPPORTED_VALUE);
        }
        yield n;
      }
      case Map<?, ?> map -> copyMap(map, depth + 1);
      case List<?> list -> {
        if (depth + 1 > MAX_DEPTH) {
          throw new RejectedException(Rejection.TOO_DEEP);
        }
        final var copy = new ArrayList<@Nullable Object>(list.size());
        for (final var element : list) {
          copy.add(copyValue(element, depth + 1));
        }
        yield copy;
      }
      default -> throw new RejectedException(Rejection.UNSUPPORTED_VALUE);
    };
  }

  private static final class RejectedException extends RuntimeException {

    private final Rejection rejection;

    RejectedException(Rejection rejection) {
      super(rejection.name(), null, false, false);
      this.rejection = rejection;
    }
  }
}
