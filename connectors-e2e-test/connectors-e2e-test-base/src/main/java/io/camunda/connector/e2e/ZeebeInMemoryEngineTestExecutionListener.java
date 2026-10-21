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
package io.camunda.connector.e2e;

import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.engine.EngineFactory;
import io.camunda.zeebe.spring.test.AbstractZeebeTestExecutionListener;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Replacement for {@code io.camunda.zeebe.spring.test.ZeebeTestExecutionListener}.
 *
 * <p>Both the original listener ({@code TestSocketUtils}) and {@link EngineFactory#create()} pick a
 * port by probing and releasing it before the engine's gRPC server binds it. The probed port comes
 * from (or may fall into) the OS ephemeral range, which outgoing connections and Docker port
 * mappings draw from as well, so under parallel builds with Testcontainers it is frequently taken
 * in the meantime ("Address already in use"). A failed start then hangs for 5 minutes on stop.
 *
 * <p>This listener probes random ports below the Linux ephemeral range (32768+) on the wildcard
 * address, which nothing assigns implicitly, and only keeps an engine that started successfully.
 */
public class ZeebeInMemoryEngineTestExecutionListener extends AbstractZeebeTestExecutionListener
    implements TestExecutionListener, Ordered {

  private static final int MIN_PORT = 10000;
  private static final int MAX_PORT = 32000;
  private static final int MAX_ATTEMPTS = 100;

  private ZeebeTestEngine zeebeEngine;

  @Override
  public void beforeTestMethod(@NonNull TestContext testContext) {
    var engine = EngineFactory.create(findFreePort());
    engine.start();
    zeebeEngine = engine;
    setupWithZeebeEngine(testContext, zeebeEngine);
  }

  @Override
  public void afterTestMethod(@NonNull TestContext testContext) {
    if (zeebeEngine == null) {
      return;
    }
    try {
      cleanup(testContext, zeebeEngine);
    } finally {
      zeebeEngine.stop();
      zeebeEngine = null;
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  private static int findFreePort() {
    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      int port = ThreadLocalRandom.current().nextInt(MIN_PORT, MAX_PORT);
      try (var socket = new ServerSocket(port)) {
        return socket.getLocalPort();
      } catch (IOException e) {
        // port in use, try another one
      }
    }
    throw new IllegalStateException(
        "Could not find a free port in range [" + MIN_PORT + ", " + MAX_PORT + ")");
  }
}
