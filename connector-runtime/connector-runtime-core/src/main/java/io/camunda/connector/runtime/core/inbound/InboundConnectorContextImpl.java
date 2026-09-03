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
package io.camunda.connector.runtime.core.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.EvictingQueue;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.runtime.core.AbstractConnectorContext;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InboundConnectorContextImpl extends AbstractConnectorContext
    implements InboundConnectorContext, InboundConnectorReportingContext {

  private final Logger LOG = LoggerFactory.getLogger(InboundConnectorContextImpl.class);
  private final InboundConnectorDetails connectorDetails;
  private final Map<String, Object> properties;

  private final InboundCorrelationHandler correlationHandler;
  private final ObjectMapper objectMapper;

  private final Consumer<Throwable> cancellationCallback;

  private Health health = Health.unknown();

  private final EvictingQueue<Activity> logs;

  private volatile List<String> reReadSecrets;

  // an activated element resolves through its own SecretHandler, so its captures are pulled in here
  private final Map<ProcessElement, AbstractConnectorContext> activatedElementContexts =
      new ConcurrentHashMap<>();
  private final Set<String> capturedElementSecretValues = ConcurrentHashMap.newKeySet();

  private static final String REDACTION_UNAVAILABLE_MESSAGE =
      "Message withheld: could not verify it does not contain a secret value";

  public InboundConnectorContextImpl(
      SecretProvider secretProvider,
      ValidationProvider validationProvider,
      ValidInboundConnectorDetails connectorDetails,
      InboundCorrelationHandler correlationHandler,
      Consumer<Throwable> cancellationCallback,
      ObjectMapper objectMapper,
      EvictingQueue logs) {
    this(
        secretProvider,
        validationProvider,
        connectorDetails,
        correlationHandler,
        cancellationCallback,
        objectMapper,
        logs,
        false);
  }

  /**
   * @param secretFilterEnabled when {@code true}, restricts secret resolution to the names declared
   *     by the deployed {@code zeebe:property} text replacement runs over (#7730).
   */
  public InboundConnectorContextImpl(
      SecretProvider secretProvider,
      ValidationProvider validationProvider,
      ValidInboundConnectorDetails connectorDetails,
      InboundCorrelationHandler correlationHandler,
      Consumer<Throwable> cancellationCallback,
      ObjectMapper objectMapper,
      EvictingQueue logs,
      boolean secretFilterEnabled) {
    super(secretProvider, secretFilter(connectorDetails, secretFilterEnabled), validationProvider);
    this.correlationHandler = correlationHandler;
    this.connectorDetails = connectorDetails;
    this.properties =
        InboundPropertyHandler.readWrappedProperties(
            connectorDetails.rawPropertiesWithoutKeywords());
    this.objectMapper = objectMapper;
    this.cancellationCallback = cancellationCallback;
    this.logs = logs;
  }

  @Override
  public CorrelationResult correlateWithResult(Object variables) {
    try {
      var result = correlationHandler.correlate(connectorDetails.connectorElements(), variables);
      trackActivatedElement(result);
      return result;
    } catch (FeelEngineWrapperException e) {
      log(Activity.level(Severity.ERROR).tag("error").message(e.getMessage()));
      return new CorrelationResult.Failure.Other(e);
    } catch (Exception e) {
      log(
          Activity.level(Severity.ERROR)
              .tag("error")
              .message("Failed to correlate inbound event " + e.getMessage()));
      LOG.error("Failed to correlate inbound event", e);
      return new CorrelationResult.Failure.Other(e);
    }
  }

  @Override
  public void cancel(Throwable exception) {
    try {
      cancellationCallback.accept(exception);
    } catch (Throwable e) {
      LOG.error("Failed to deliver the cancellation signal to the runtime", e);
    }
  }

  /**
   * The allow-list is drawn from the same {@code rawPropertiesWithoutKeywords} map that {@link
   * #properties} is built from, so the names permitted and the text filtered always come from one
   * read. Both are fixed at construction on this branch — {@code connectorDetails} and {@code
   * properties} are final and there is no {@code updateConnectorDetails} — so the hot-swap and
   * torn-read failure directions main had to close are not representable here.
   *
   * <p>This is what the filter still stops: a name only a sibling element declares, and any name in
   * text a caller supplies rather than the element's own properties. The escalation it was written
   * for -- {@link SecretUtil#replaceSecrets} running the bare pass over the brace pass's output, so
   * that a resolved value containing reference-shaped text reached a secret no model declares --
   * was confirmed present on this branch before backporting: a secret whose value is the literal
   * {@code secrets.CHAINED} resolved {@code CHAINED} under the previous allow-all filter. It is now
   * closed at its source: the single scan consumes each reference whole and never re-reads what it
   * resolved.
   *
   * <p>This paragraph used to record that the filter was narrower here than on 8.8/8.9, because
   * this branch had neither their nested-match exclusion in {@code retrieveSecretKeysInInput} nor
   * their denied-bracket exclusion in {@code replaceSecretsWithoutParentheses} (#8592, #8593), so a
   * model declaring {@code {{secrets.FOO:BAR}}} also admitted the truncated {@code FOO}. The single
   * scan closes that gap for every {@code SecretUtil} caller at once, so the filter here is no
   * longer the narrower one.
   *
   * <p>Static because it feeds the {@code super(...)} call, before any field is assigned.
   */
  private static SecretFilter secretFilter(
      ValidInboundConnectorDetails connectorDetails, boolean secretFilterEnabled) {
    if (!secretFilterEnabled) {
      return SecretFilter.allowAll();
    }
    return SecretFilter.allowOnly(
        connectorDetails.rawPropertiesWithoutKeywords().values().stream()
            .flatMap(value -> SecretUtil.retrieveSecretKeysInInput(value).stream())
            .distinct()
            .toList());
  }

  @Override
  public Map<String, Object> getProperties() {
    return getPropertiesWithSecrets(properties);
  }

  @Override
  public <T> T bindProperties(Class<T> cls) {
    var mappedObject = objectMapper.convertValue(getPropertiesWithSecrets(properties), cls);
    getValidationProvider().validate(mappedObject);
    return mappedObject;
  }

  @Override
  public InboundConnectorDefinition getDefinition() {
    return new InboundConnectorDefinition(
        connectorDetails.type(),
        connectorDetails.tenantId(),
        connectorDetails.deduplicationId(),
        connectorDetails.connectorElements().stream()
            .map(InboundConnectorElement::element)
            .collect(Collectors.toList()));
  }

  @Override
  public void reportHealth(Health health) {
    this.health = redactHealth(health);
  }

  @Override
  public Health getHealth() {
    return health;
  }

  @Override
  public void log(Activity log) {
    var masked = redact(log);
    LOG.debug("{}", masked);
    this.logs.add(masked);
  }

  /**
   * The values an operator-visible message must not carry. Read once and cached on first complete
   * success, so a transient provider failure fails closed for that one message instead of poisoning
   * every later one. {@code null} means "could not be established", never "nothing to redact".
   *
   * <p>The captured values are additive to a successful, complete re-read and never a substitute
   * for a failed one: a connector can declare a secret it never got around to binding before the
   * provider went down.
   */
  private List<String> secretValuesForRedaction() {
    var reRead = reReadSecretValues();
    if (reRead == null) {
      return null;
    }
    var captured = new ArrayList<>(getSecretHandler().getResolvedValues());
    captured.addAll(capturedElementSecretValues());
    if (captured.isEmpty()) {
      return reRead;
    }
    var union = new ArrayList<>(reRead);
    union.addAll(captured);
    return union;
  }

  /**
   * Records the element context a correlation activated, so what it resolves can be redacted here.
   *
   * <p>An activated element resolves secrets through its own {@link
   * io.camunda.connector.runtime.core.secret.SecretHandler} — {@code ProcessElementContext} is a
   * second binding path, and every successful {@code CorrelationResult} hands the connector one —
   * so its captures live outside this context and have to be pulled in. Without them a value the
   * element bound and this context never did is invisible to the re-read once it rotates, and gets
   * published in the clear.
   *
   * <p>Held per element, so a long-running executable keeps at most one reference per deployed
   * element rather than one per correlation; a displaced context's values are harvested before it
   * is dropped.
   */
  private void trackActivatedElement(CorrelationResult result) {
    if (!(result instanceof CorrelationResult.Success success)
        || !(success.activatedElement() instanceof AbstractConnectorContext elementContext)) {
      return;
    }
    var displaced =
        activatedElementContexts.put(success.activatedElement().getElement(), elementContext);
    if (displaced != null && displaced != elementContext) {
      capturedElementSecretValues.addAll(displaced.getSecretHandler().getResolvedValues());
    }
  }

  private List<String> capturedElementSecretValues() {
    var values = new ArrayList<>(capturedElementSecretValues);
    activatedElementContexts
        .values()
        .forEach(context -> values.addAll(context.getSecretHandler().getResolvedValues()));
    return values;
  }

  private List<String> reReadSecretValues() {
    var cached = reReadSecrets;
    if (cached != null) {
      return cached;
    }
    var values = fetchSecretValuesForRedaction();
    if (values != null) {
      reReadSecrets = values;
    }
    return values;
  }

  /** Scans every grouped element's raw properties, not just this context's representative one. */
  private List<String> fetchSecretValuesForRedaction() {
    var names =
        connectorDetails.connectorElements().stream()
            .flatMap(element -> element.rawProperties().values().stream())
            .flatMap(value -> SecretUtil.retrieveSecretKeysInInput(value).stream())
            .distinct()
            .toList();
    if (names.isEmpty()) {
      return List.of();
    }
    try {
      var values = new ArrayList<String>(names.size());
      for (var name : names) {
        var value = secretProvider.getSecret(name);
        // a name that comes back empty is a partial read, treated the same as a failed one
        if (value == null) {
          return null;
        }
        values.add(value);
      }
      return values;
    } catch (Exception e) {
      // never the provider's own message: it can echo the secret store's response
      LOG.warn("Could not fetch secret values to redact activity log: {}", e.getClass().getName());
      return null;
    }
  }

  private String redactMessage(String message) {
    if (message == null) {
      return null;
    }
    var secrets = secretValuesForRedaction();
    return secrets == null
        ? REDACTION_UNAVAILABLE_MESSAGE
        : SecretUtil.hideSecretsFromMessage(message, secrets);
  }

  private Health redactHealth(Health health) {
    if (health == null || health.getError() == null) {
      return health;
    }
    var error = health.getError();
    return health.withError(
        new Health.Error(redactMessage(error.code()), redactMessage(error.message())));
  }

  private Activity redact(Activity activity) {
    return new Activity(
        activity.severity(),
        activity.tag(),
        activity.timestamp(),
        redactMessage(activity.message()));
  }

  @Override
  public Queue<Activity> getLogs() {
    return this.logs;
  }

  @Override
  public List<InboundConnectorElement> connectorElements() {
    return connectorDetails.connectorElements();
  }

  private Map<String, Object> propertiesWithSecrets;

  private Map<String, Object> getPropertiesWithSecrets(Map<String, Object> properties) {
    if (propertiesWithSecrets == null) {
      propertiesWithSecrets =
          InboundPropertyHandler.getPropertiesWithSecrets(
              getSecretHandler(), objectMapper, properties);
    }
    return propertiesWithSecrets;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InboundConnectorContextImpl that = (InboundConnectorContextImpl) o;
    return Objects.equals(connectorDetails, that.connectorDetails);
  }

  @Override
  public int hashCode() {
    return Objects.hash(connectorDetails);
  }

  @Override
  public String toString() {
    return "InboundConnectorContextImpl{" + "connectorDetails=" + connectorDetails + '}';
  }
}
