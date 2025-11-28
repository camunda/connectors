/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.loadtest;

import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.annotation.Variable;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.loadtest.model.LoadTestRequest;
import io.camunda.connector.loadtest.model.LoadTestResult;
import io.camunda.connector.loadtest.model.RandomLoadTestRequest;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Connector for load testing with I/O wait and CPU burn operations. */
@OutboundConnector(name = "Load Test Connector", type = "io.camunda:connector:load-test:1")
@ElementTemplate(
    name = "Load Test Connector",
    id = "io.camunda.connectors.loadTest.v1",
    version = 1,
    icon = "icon.svg")
public class LoadTestConnector implements OutboundConnectorProvider {

  private static final Logger LOG = LoggerFactory.getLogger(LoadTestConnector.class);
  private static final long MAX_DURATION_MS = 10_000L;

  @Operation(id = "loadTest", name = "Load Test")
  public LoadTestResult loadTest(@Variable LoadTestRequest request) {
    long ioWaitMs = validateAndCapDuration(request.ioWaitMs(), "I/O wait");
    long cpuBurnMs = validateAndCapDuration(request.cpuBurnMs(), "CPU burn");

    LOG.debug("Starting load test: ioWaitMs={}, cpuBurnMs={}", ioWaitMs, cpuBurnMs);

    long actualIoWaitMs = 0;
    long actualCpuBurnMs = 0;
    long accumulator = 0;

    // Perform I/O wait first
    if (ioWaitMs > 0) {
      actualIoWaitMs = performIoWait(ioWaitMs);
    }

    // Perform CPU burn
    if (cpuBurnMs > 0) {
      var cpuResult = performCpuBurn(cpuBurnMs);
      actualCpuBurnMs = cpuResult.actualDurationMs;
      accumulator = cpuResult.accumulator;
    }

    LOG.debug(
        "Load test completed: actualIoWaitMs={}, actualCpuBurnMs={}, accumulator={}",
        actualIoWaitMs,
        actualCpuBurnMs,
        accumulator);

    return new LoadTestResult(ioWaitMs, cpuBurnMs, actualIoWaitMs, actualCpuBurnMs, accumulator);
  }

  @Operation(id = "randomLoadTest", name = "Random Load Test")
  public LoadTestResult randomLoadTest(@Variable RandomLoadTestRequest request) {
    Random random = new Random();

    long ioWaitMs = 0;
    if (request.minIoWaitMs() != null || request.maxIoWaitMs() != null) {
      long minIo = request.minIoWaitMs() != null ? request.minIoWaitMs() : 0L;
      long maxIo = request.maxIoWaitMs() != null ? request.maxIoWaitMs() : 0L;
      if (maxIo > minIo) {
        ioWaitMs = minIo + random.nextLong(maxIo - minIo + 1);
      } else {
        ioWaitMs = minIo;
      }
      ioWaitMs = Math.min(ioWaitMs, MAX_DURATION_MS);
    }

    long cpuBurnMs = 0;
    if (request.minCpuBurnMs() != null || request.maxCpuBurnMs() != null) {
      long minCpu = request.minCpuBurnMs() != null ? request.minCpuBurnMs() : 0L;
      long maxCpu = request.maxCpuBurnMs() != null ? request.maxCpuBurnMs() : 0L;
      if (maxCpu > minCpu) {
        cpuBurnMs = minCpu + random.nextLong(maxCpu - minCpu + 1);
      } else {
        cpuBurnMs = minCpu;
      }
      cpuBurnMs = Math.min(cpuBurnMs, MAX_DURATION_MS);
    }

    LOG.debug(
        "Random load test generated: ioWaitMs={}, cpuBurnMs={} (from ranges [{}-{}], [{}-{}])",
        ioWaitMs,
        cpuBurnMs,
        request.minIoWaitMs(),
        request.maxIoWaitMs(),
        request.minCpuBurnMs(),
        request.maxCpuBurnMs());

    long actualIoWaitMs = 0;
    long actualCpuBurnMs = 0;
    long accumulator = 0;

    // Perform I/O wait first
    if (ioWaitMs > 0) {
      actualIoWaitMs = performIoWait(ioWaitMs);
    }

    // Perform CPU burn
    if (cpuBurnMs > 0) {
      var cpuResult = performCpuBurn(cpuBurnMs);
      actualCpuBurnMs = cpuResult.actualDurationMs;
      accumulator = cpuResult.accumulator;
    }

    LOG.debug(
        "Random load test completed: actualIoWaitMs={}, actualCpuBurnMs={}, accumulator={}",
        actualIoWaitMs,
        actualCpuBurnMs,
        accumulator);

    return new LoadTestResult(ioWaitMs, cpuBurnMs, actualIoWaitMs, actualCpuBurnMs, accumulator);
  }

  private long validateAndCapDuration(Long durationMs, String label) {
    if (durationMs == null || durationMs < 0) {
      return 0L;
    }
    if (durationMs > MAX_DURATION_MS) {
      LOG.warn("{} duration {} ms exceeds maximum {}, capping", label, durationMs, MAX_DURATION_MS);
      return MAX_DURATION_MS;
    }
    return durationMs;
  }

  private long performIoWait(long durationMs) {
    long startNanos = System.nanoTime();
    try {
      Thread.sleep(durationMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("I/O wait interrupted", e);
    }
    long endNanos = System.nanoTime();
    return (endNanos - startNanos) / 1_000_000L;
  }

  private CpuBurnResult performCpuBurn(long durationMs) {
    long startNanos = System.nanoTime();
    long endNanos = startNanos + (durationMs * 1_000_000L);

    // Use xorshift-like algorithm to prevent JIT optimization
    long accumulator = System.nanoTime() ^ 0x5DEECE66DL;
    int iterations = 0;

    while (System.nanoTime() < endNanos) {
      // Mix of arithmetic, bitwise, and logical operations
      accumulator ^= accumulator << 13;
      accumulator ^= accumulator >>> 7;
      accumulator ^= accumulator << 17;
      accumulator = accumulator * 31 + iterations;
      iterations++;

      // Additional computation to increase CPU load
      accumulator += Math.abs(accumulator % 97);
    }

    long actualDurationMs = (System.nanoTime() - startNanos) / 1_000_000L;
    return new CpuBurnResult(actualDurationMs, accumulator);
  }

  private record CpuBurnResult(long actualDurationMs, long accumulator) {}
}
