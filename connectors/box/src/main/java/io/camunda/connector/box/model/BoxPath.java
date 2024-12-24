/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box.model;

import java.util.Arrays;
import java.util.List;

public sealed interface BoxPath permits BoxPath.Id, BoxPath.Root, BoxPath.Segments {

  Root ROOT = new Root();

  record Root() implements BoxPath {}

  record Id(String id) implements BoxPath {}

  record Segments(List<String> segments) implements BoxPath {

    public Segments {
      if (segments == null || segments.isEmpty()) {
        throw new IllegalArgumentException("segments is null or empty");
      }
    }

    public boolean isPathEnd() {
      return segments().size() == 1;
    }

    public Segments withoutFirstSegment() {
      return new Segments(segments.subList(1, segments.size()));
    }
  }

  static BoxPath from(String path) {
    if (path == null || path.trim().isEmpty()) {
      return ROOT;
    } else if (path.startsWith("/")) {
      var segments =
          Arrays.stream(path.trim().split("/")).filter(s -> !s.trim().isEmpty()).toList();
      if (segments.isEmpty()) {
        return ROOT;
      } else {
        return new Segments(segments);
      }
    } else {
      return new Id(path.trim());
    }
  }
}
