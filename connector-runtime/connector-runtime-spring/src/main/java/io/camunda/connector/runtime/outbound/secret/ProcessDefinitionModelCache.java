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

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import dev.failsafe.Timeout;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

/**
 * Fetches and parses a process definition's deployed BPMN model, cached per {@code
 * (physicalTenantId, processDefinitionKey)} — shared by every static-analysis consumer that needs
 * the model (currently {@link ProcessDefinitionSecretKeyCache} and {@code
 * ProcessDefinitionIntrinsicFunctionAllowListCache}), so a process definition's model is fetched
 * and parsed once regardless of how many such consumers exist, not once per consumer.
 */
public class ProcessDefinitionModelCache {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionModelCache.class);
  private static final int XML_FETCH_MAX_RETRIES = 3;
  private static final Duration XML_FETCH_INITIAL_RETRY_DELAY = Duration.ofSeconds(1);

  /**
   * Reserves time after the XML fetch/retry completes for whatever the caller still needs to do
   * before its own deadline (parsing the model, then the consumer's own extraction pass), so the
   * fetch itself doesn't consume the entire budget and leave none for that remaining work.
   */
  private static final Duration XML_FETCH_DEADLINE_SAFETY_MARGIN = Duration.ofSeconds(5);

  private final String physicalTenantId;
  private final CamundaClient camundaClient;
  private final Cache cache;
  private final Duration xmlFetchInitialRetryDelay;

  public ProcessDefinitionModelCache(
      String physicalTenantId, CamundaClient camundaClient, Cache cache) {
    this(physicalTenantId, camundaClient, cache, XML_FETCH_INITIAL_RETRY_DELAY);
  }

  /** Test-only seam: lets retry tests use a near-zero delay instead of the real one. */
  public ProcessDefinitionModelCache(
      String physicalTenantId,
      CamundaClient camundaClient,
      Cache cache,
      Duration xmlFetchInitialRetryDelay) {
    this.physicalTenantId = physicalTenantId;
    this.camundaClient = camundaClient;
    this.cache = cache;
    this.xmlFetchInitialRetryDelay = xmlFetchInitialRetryDelay;
  }

  private record CachedProcessDefinitionKey(String physicalTenantId, long processDefinitionKey) {}

  public BpmnModelInstance getModel(long processDefinitionKey, Instant deadline) {
    var cacheKey = new CachedProcessDefinitionKey(physicalTenantId, processDefinitionKey);
    return cache.get(cacheKey, () -> fetchAndParse(processDefinitionKey, deadline));
  }

  private BpmnModelInstance fetchAndParse(long processDefinitionKey, Instant deadline) {
    String bpmnXml = fetchBpmnXmlWithRetry(processDefinitionKey, deadline);
    // The XML declaration on every deployed BPMN file names UTF-8 explicitly (Bpmn.writeModelToXX
    // always emits it); encoding the fetched String back to bytes with the platform default
    // charset instead would corrupt non-ASCII characters (element/process names, etc.) on any JVM
    // whose default isn't UTF-8.
    return Bpmn.readModelFromStream(
        new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
  }

  private String fetchBpmnXmlWithRetry(long processDefinitionKey, Instant deadline) {
    Duration remaining =
        Duration.between(Instant.now(), deadline).minus(XML_FETCH_DEADLINE_SAFETY_MARGIN);
    if (remaining.isNegative() || remaining.isZero()) {
      throw new IllegalStateException(
          "BPMN XML fetch deadline already elapsed for process definition key "
              + processDefinitionKey);
    }
    Timeout<String> xmlFetchTimeout = Timeout.<String>builder(remaining).withInterrupt().build();
    if (remaining.compareTo(xmlFetchInitialRetryDelay) <= 0) {
      return Failsafe.with(xmlFetchTimeout)
          .get(
              () ->
                  camundaClient.newProcessDefinitionGetXmlRequest(processDefinitionKey).execute());
    }
    RetryPolicy<String> xmlFetchRetryPolicy =
        RetryPolicy.<String>builder()
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
        .get(() -> camundaClient.newProcessDefinitionGetXmlRequest(processDefinitionKey).execute());
  }
}
