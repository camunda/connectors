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
package io.camunda.connector.api.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActivityBuilderTest {

  @Test
  void withMessageAndException_includesMessageExceptionMessageAndStackTrace() {
    var exception = new IllegalStateException("Something went wrong");

    var activity = Activity.newBuilder().withMessage("Operation failed", exception).build();

    assertThat(activity.message())
        .startsWith("Operation failed\nSomething went wrong\n")
        .contains("java.lang.IllegalStateException: Something went wrong")
        .contains("at io.camunda.connector.api.inbound.ActivityBuilderTest");
  }

  @Test
  void withException_includesExceptionMessageAndStackTrace() {
    var exception = new IllegalStateException("Something went wrong");

    var activity = Activity.newBuilder().withMessage(exception).build();

    assertThat(activity.message())
        .startsWith("Something went wrong\n")
        .contains("java.lang.IllegalStateException: Something went wrong")
        .contains("at io.camunda.connector.api.inbound.ActivityBuilderTest");
  }

  @Test
  void withException_whenExceptionMessageIsNull_fallsBackToStackTraceOnly() {
    var exception = new IllegalStateException();

    var activity = Activity.newBuilder().withMessage(exception).build();

    assertThat(activity.message())
        .startsWith("java.lang.IllegalStateException")
        .contains("at io.camunda.connector.api.inbound.ActivityBuilderTest");
  }

  @Test
  void withMessageAndException_whenExceptionMessageIsNull_keepsCustomMessageAndStackTrace() {
    var exception = new IllegalStateException();

    var activity = Activity.newBuilder().withMessage("Operation failed", exception).build();

    assertThat(activity.message())
        .startsWith("Operation failed\njava.lang.IllegalStateException")
        .contains("at io.camunda.connector.api.inbound.ActivityBuilderTest");
  }

  @Test
  void withMessageOnly_returnsMessageUnchanged() {
    var activity = Activity.newBuilder().withMessage("Just a message").build();

    assertThat(activity.message()).isEqualTo("Just a message");
  }
}
