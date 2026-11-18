/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bounds the scripts of one tenant that this runtime runs at once. A tenant ID is unique within its
 * physical tenant only, so each physical tenant counts its tenants on its own. A job beyond the
 * limit does not wait for a slot: it would hold one of the worker's job slots, which other tenants
 * need.
 */
final class TenantLimiter {

  private static final Permit UNLIMITED = () -> {};

  private final Integer limit;
  private final Map<TenantKey, Semaphore> running = new ConcurrentHashMap<>();

  /**
   * @param limit most scripts of one tenant at once; {@code null} for no limit
   */
  TenantLimiter(Integer limit) {
    this.limit = limit;
  }

  static TenantLimiter unlimited() {
    return new TenantLimiter(null);
  }

  Integer limit() {
    return limit;
  }

  /** A slot for one script of the tenant, or empty when the tenant has none left. */
  Optional<Permit> tryAcquire(String physicalTenantId, String tenantId) {
    if (limit == null) {
      return Optional.of(UNLIMITED);
    }
    // One entry per tenant that ran a script: as many as the tenants of the cluster.
    final var slots =
        running.computeIfAbsent(
            new TenantKey(physicalTenantId, tenantId), key -> new Semaphore(limit));
    if (!slots.tryAcquire()) {
      return Optional.empty();
    }
    final var released = new AtomicBoolean();
    return Optional.of(() -> {
      // Released once, so that closing twice cannot raise the tenant's limit.
      if (released.compareAndSet(false, true)) {
        slots.release();
      }
    });
  }

  private record TenantKey(String physicalTenantId, String tenantId) {}

  /** Releases the slot of one script. */
  interface Permit extends AutoCloseable {
    @Override
    void close();
  }
}
