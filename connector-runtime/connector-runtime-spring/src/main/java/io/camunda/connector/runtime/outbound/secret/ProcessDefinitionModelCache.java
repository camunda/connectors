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
package io.camunda.connector.runtime.outbound.secret;

import com.github.benmanes.caffeine.cache.Cache;
import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeException;
import dev.failsafe.RetryPolicy;
import dev.failsafe.Timeout;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.exception.OperateException;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetches a process definition's deployed BPMN model via {@link CamundaOperateClient}, cached per
 * process definition key — shared by every static-analysis consumer that needs the model (currently
 * {@link ProcessDefinitionSecretKeyCache} and the intrinsic-function allow-list cache), so a
 * process definition's model is fetched once regardless of how many such consumers exist, not once
 * per consumer. Extracted from {@link ProcessDefinitionSecretKeyCache}'s own original fetch/retry
 * logic, which the allow-list cache would otherwise have had to duplicate.
 */
public class ProcessDefinitionModelCache {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionModelCache.class);
  private static final int XML_FETCH_MAX_RETRIES = 3;
  private static final Duration XML_FETCH_INITIAL_RETRY_DELAY = Duration.ofSeconds(1);

  /**
   * Retries stop this far ahead of the activated job's deadline, leaving room for the connector
   * function itself to run before the job's lease expires -- a fetch that only succeeds after the
   * lease is gone risks the job being reassigned while this worker keeps executing, per the
   * duplicate-side-effect concern in {@code ConnectorJobHandler}.
   */
  private static final Duration XML_FETCH_DEADLINE_SAFETY_MARGIN = Duration.ofSeconds(5);

  private final CamundaOperateClient camundaOperateClient;
  private final Cache<Long, BpmnModelInstance> cache;
  private final Duration xmlFetchInitialRetryDelay;

  public ProcessDefinitionModelCache(
      CamundaOperateClient camundaOperateClient, Cache<Long, BpmnModelInstance> cache) {
    this(camundaOperateClient, cache, XML_FETCH_INITIAL_RETRY_DELAY);
  }

  /** Test-only seam: lets retry tests use a near-zero delay instead of the real one. */
  public ProcessDefinitionModelCache(
      CamundaOperateClient camundaOperateClient,
      Cache<Long, BpmnModelInstance> cache,
      Duration xmlFetchInitialRetryDelay) {
    this.camundaOperateClient = camundaOperateClient;
    this.cache = cache;
    this.xmlFetchInitialRetryDelay = xmlFetchInitialRetryDelay;
  }

  public BpmnModelInstance getModel(long processDefinitionKey, Instant deadline) {
    if (camundaOperateClient == null) {
      throw new ProcessDefinitionModelUnavailableException(
          "No CamundaOperateClient available to look up the process definition model for key "
              + processDefinitionKey
              + ". This happens when camunda.connector.polling.enabled=false, which skips the"
              + " bean that provides it.");
    }
    return cache.get(processDefinitionKey, key -> fetchModelUnchecked(key, deadline));
  }

  /**
   * Caffeine's mapping function is a plain {@code Function}, which cannot declare {@link
   * OperateException}; wrapping it in {@link ProcessDefinitionModelLookupException} is the only
   * wrapper this class introduces — Caffeine, unlike Spring's {@code Cache#get(Object, Callable)},
   * rethrows an unchecked mapping-function failure unwrapped, so every other exception on this path
   * reaches the caller exactly as thrown.
   */
  private BpmnModelInstance fetchModelUnchecked(long processDefinitionKey, Instant deadline) {
    try {
      return fetchBpmnModelWithRetry(processDefinitionKey, deadline);
    } catch (OperateException e) {
      throw new ProcessDefinitionModelLookupException(
          "Failed to look up the process definition model for key " + processDefinitionKey, e);
    }
  }

  private BpmnModelInstance fetchBpmnModelWithRetry(long processDefinitionKey, Instant deadline)
      throws OperateException {
    Duration remaining =
        Duration.between(Instant.now(), deadline).minus(XML_FETCH_DEADLINE_SAFETY_MARGIN);
    if (remaining.isNegative() || remaining.isZero()) {
      throw new IllegalStateException(
          "BPMN XML fetch deadline already elapsed for process definition key "
              + processDefinitionKey);
    }
    Timeout<BpmnModelInstance> xmlFetchTimeout =
        Timeout.<BpmnModelInstance>builder(remaining).withInterrupt().build();
    try {
      if (remaining.compareTo(xmlFetchInitialRetryDelay) <= 0) {
        return Failsafe.with(xmlFetchTimeout)
            .get(() -> camundaOperateClient.getProcessDefinitionModel(processDefinitionKey));
      }
      RetryPolicy<BpmnModelInstance> xmlFetchRetryPolicy =
          RetryPolicy.<BpmnModelInstance>builder()
              .withBackoff(
                  xmlFetchInitialRetryDelay,
                  xmlFetchInitialRetryDelay.multipliedBy(1L << (XML_FETCH_MAX_RETRIES - 1)))
              .withMaxRetries(XML_FETCH_MAX_RETRIES)
              .withMaxDuration(remaining)
              .onFailedAttempt(
                  event ->
                      LOG.warn(
                          "Attempt {}/{} to fetch BPMN XML failed: {}",
                          event.getAttemptCount(),
                          XML_FETCH_MAX_RETRIES + 1,
                          event.getLastException().getClass().getName()))
              .build();
      return Failsafe.with(xmlFetchTimeout, xmlFetchRetryPolicy)
          .get(() -> camundaOperateClient.getProcessDefinitionModel(processDefinitionKey));
    } catch (FailsafeException e) {
      // A checked OperateException from the retried call arrives here wrapped, per Failsafe's
      // get(CheckedSupplier) contract; unwrap it so this method's own throws clause -- and every
      // caller's existing OperateException handling -- still applies. A FailsafeException with no
      // cause (notably TimeoutExceededException, thrown directly by the Timeout policy rather than
      // wrapping a checked exception) is rethrown as-is.
      if (e.getCause() instanceof OperateException operateException) {
        throw operateException;
      }
      throw e;
    }
  }
}
