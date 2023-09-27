/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SharedExecutorServiceTest {

  @Test
  public void ensuresSingletonInstance() {
    SharedExecutorService instance1 = SharedExecutorService.getInstance();
    SharedExecutorService instance2 = SharedExecutorService.getInstance();
    assertSame(instance1, instance2, "Instances should be the same");
  }

  @Test
  public void testGetExecutorService() throws InterruptedException {
    SharedExecutorService service = SharedExecutorService.getInstance();
    ScheduledExecutorService executor = service.getExecutorService();

    CountDownLatch latch = new CountDownLatch(1);
    executor.schedule(() -> latch.countDown(), 10, TimeUnit.MILLISECONDS);

    assertTrue(latch.await(100, TimeUnit.MILLISECONDS), "Task should complete");
  }
}
