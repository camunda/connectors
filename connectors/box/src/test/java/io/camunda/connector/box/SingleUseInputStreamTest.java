/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class SingleUseInputStreamTest {

  @Test
  void readsContentOnce() throws IOException {
    var stream = new SingleUseInputStream(new ByteArrayInputStream(new byte[] {1, 2, 3}));

    assertThat(stream.readAllBytes()).containsExactly(1, 2, 3);
  }

  @Test
  void failsLoudlyWhenReadAgainAfterBeingConsumed() throws IOException {
    var stream = new SingleUseInputStream(new ByteArrayInputStream(new byte[] {1, 2, 3}));
    stream.readAllBytes();

    assertThatThrownBy(stream::read)
        .isInstanceOf(IOException.class)
        .hasMessageContaining("already");
  }

  @Test
  void failsLoudlyWhenReadAfterClose() throws IOException {
    var stream = new SingleUseInputStream(new ByteArrayInputStream(new byte[] {1, 2, 3}));
    stream.close();

    assertThatThrownBy(stream::readAllBytes).isInstanceOf(IOException.class);
  }
}
