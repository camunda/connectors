/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.connector.box.model.BoxPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

public class BoxPathTests {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "/", "//", "/ / "})
  public void testRoot(String path) {
    assertInstanceOf(BoxPath.Root.class, BoxPath.from(path));
  }

  @ParameterizedTest
  @ValueSource(strings = {"1", "0"})
  public void testId(String path) {
    assertInstanceOf(BoxPath.Id.class, BoxPath.from(path));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/a", "/a/b"})
  public void testSegments(String path) {
    assertInstanceOf(BoxPath.Segments.class, BoxPath.from(path));
  }

  @Test
  public void testLastSegment() {
    BoxPath.Segments segments = (BoxPath.Segments) BoxPath.from("/a");
    assertTrue(segments.isPathEnd());
  }

  @Test
  public void withoutFirstSegment() {
    BoxPath.Segments segments = ((BoxPath.Segments) BoxPath.from("/a/b")).withoutFirstSegment();
    assertEquals(List.of("b"), segments.segments());
    assertTrue(segments.isPathEnd());
  }
}
