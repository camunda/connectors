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
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.api.inbound.*;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.runtime.core.AbstractConnectorContext;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretFilter.Secret;
import io.camunda.connector.runtime.core.secret.SecretUtil;
import io.camunda.document.Document;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.document.factory.DocumentFactoryImpl;
import io.camunda.document.reference.DocumentReference;
import io.camunda.document.store.DocumentCreationRequest;
import io.camunda.document.store.InMemoryDocumentStore;
import java.util.ArrayList;
import java.util.Arrays;
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
  private final ValidInboundConnectorDetails connectorDetails;
  private final Map<String, Object> properties;

  private final InboundCorrelationHandler correlationHandler;
  private final ObjectMapper objectMapper;

  private final Consumer<Throwable> cancellationCallback;
  private final EvictingQueue<Activity> logs;
  private final DocumentFactory documentFactory;
  private final Long activationTimestamp;
  private Health health = Health.unknown();
  private Map<String, Object> propertiesWithSecrets;
  private volatile List<String> reReadSecrets;
  private final Set<String> capturedSecretValues = ConcurrentHashMap.newKeySet();

  private static final String REDACTION_UNAVAILABLE_MESSAGE =
      "Message withheld: could not verify it does not contain a secret value";

  public InboundConnectorContextImpl(
      SecretProvider secretProvider,
      ValidationProvider validationProvider,
      DocumentFactory documentFactory,
      ValidInboundConnectorDetails connectorDetails,
      InboundCorrelationHandler correlationHandler,
      Consumer<Throwable> cancellationCallback,
      ObjectMapper objectMapper,
      EvictingQueue<Activity> logs) {
    this(
        secretProvider,
        validationProvider,
        documentFactory,
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
      DocumentFactory documentFactory,
      ValidInboundConnectorDetails connectorDetails,
      InboundCorrelationHandler correlationHandler,
      Consumer<Throwable> cancellationCallback,
      ObjectMapper objectMapper,
      EvictingQueue<Activity> logs,
      boolean secretFilterEnabled) {
    super(secretProvider, secretFilter(connectorDetails, secretFilterEnabled), validationProvider);
    this.documentFactory = documentFactory;
    this.correlationHandler = correlationHandler;
    this.connectorDetails = connectorDetails;
    this.properties =
        InboundPropertyHandler.readWrappedProperties(
            connectorDetails.rawPropertiesWithoutKeywords());
    this.objectMapper = objectMapper;
    this.cancellationCallback = cancellationCallback;
    this.logs = logs;
    this.activationTimestamp = System.currentTimeMillis();
  }

  public InboundConnectorContextImpl(
      SecretProvider secretProvider,
      ValidationProvider validationProvider,
      ValidInboundConnectorDetails connectorDetails,
      InboundCorrelationHandler correlationHandler,
      Consumer<Throwable> cancellationCallback,
      ObjectMapper objectMapper,
      EvictingQueue<Activity> logs) {
    this(
        secretProvider,
        validationProvider,
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE),
        connectorDetails,
        correlationHandler,
        cancellationCallback,
        objectMapper,
        logs);
  }

  @Override
  public ActivationCheckResult canActivate(Object variables) {
    return capturing(
        correlationHandler.canActivate(connectorDetails.connectorElements(), variables));
  }

  @Override
  public CorrelationResult correlateWithResult(Object variables) {
    return this.correlateWithResultInternal(
        CorrelationRequest.builder().variables(variables).build());
  }

  @Override
  public CorrelationResult correlate(CorrelationRequest correlationRequest) {
    return this.correlateWithResultInternal(correlationRequest);
  }

  private CorrelationResult correlateWithResultInternal(CorrelationRequest correlationRequest) {
    try {
      return capturing(
          correlationHandler.correlate(connectorDetails.connectorElements(), correlationRequest));
    } catch (ConnectorInputException connectorInputException) {
      return new CorrelationResult.Failure.InvalidInput(
          connectorInputException.getMessage(), connectorInputException);
    } catch (FeelEngineWrapperException feelEngineWrapperException) {
      log(
          Activity.level(Severity.ERROR)
              .tag("error")
              .message(feelEngineWrapperException.getMessage()));
      return new CorrelationResult.Failure.Other(feelEngineWrapperException);
    } catch (Exception exception) {
      log(
          Activity.level(Severity.ERROR)
              .tag("error")
              .message("Failed to correlate inbound event " + exception.getMessage()));
      LOG.error("Failed to correlate inbound event", exception);
      return new CorrelationResult.Failure.Other(exception);
    }
  }

  /**
   * Every successful activation check and correlation hands the connector a context for the element
   * that matched, and that context resolves its own element's raw properties through a secret
   * handler of its own. What it resolves has to reach this context's captured values: a value the
   * connector binds through the activated element and that rotates before the connector reports an
   * error is no longer re-readable, so nothing else would recognise it in the activity log or the
   * health error that follows.
   *
   * <p>Upstream binds element properties on the connector-level context itself, so its captures
   * land there directly. Here the element context is a separate object built by a shared factory,
   * with no reference back to this one, so it is drained as the connector reads it instead.
   */
  private ActivationCheckResult capturing(ActivationCheckResult result) {
    if (result instanceof ActivationCheckResult.Success.CanActivate canActivate) {
      return new ActivationCheckResult.Success.CanActivate(
          capturing(canActivate.activatedElement()));
    }
    return result;
  }

  private CorrelationResult capturing(CorrelationResult result) {
    return switch (result) {
      case CorrelationResult.Success.ProcessInstanceCreated created ->
          new CorrelationResult.Success.ProcessInstanceCreated(
              capturing(created.activatedElement()),
              created.processInstanceKey(),
              created.tenantId());
      case CorrelationResult.Success.MessagePublished published ->
          new CorrelationResult.Success.MessagePublished(
              capturing(published.activatedElement()),
              published.messageKey(),
              published.tenantId());
      case CorrelationResult.Success.MessageAlreadyCorrelated correlated ->
          new CorrelationResult.Success.MessageAlreadyCorrelated(
              capturing(correlated.activatedElement()));
      default -> result;
    };
  }

  private ProcessElementContext capturing(ProcessElementContext element) {
    return element instanceof DefaultProcessElementContext resolving
        ? new CapturingProcessElementContext(resolving, capturedSecretValues)
        : element;
  }

  /**
   * Adds what an element context resolves to the connector-level captured values, at the point the
   * connector reads it and so before it can report anything derived from it.
   */
  private record CapturingProcessElementContext(
      DefaultProcessElementContext delegate, Set<String> capturedSecretValues)
      implements ProcessElementContext {

    @Override
    public ProcessElement getElement() {
      return delegate.getElement();
    }

    @Override
    public <T> T bindProperties(Class<T> cls) {
      try {
        return delegate.bindProperties(cls);
      } finally {
        capturedSecretValues.addAll(delegate.resolvedSecretValues());
      }
    }

    @Override
    public Map<String, Object> getProperties() {
      try {
        return delegate.getProperties();
      } finally {
        capturedSecretValues.addAll(delegate.resolvedSecretValues());
      }
    }
  }

  @Override
  public void cancel(Throwable exception) {
    try {
      cancellationCallback.accept(redactCancellationError(exception));
    } catch (Throwable e) {
      LOG.error("Failed to deliver the cancellation signal to the runtime", e);
    }
  }

  /**
   * Redacts what a connector cancels with, because the runtime publishes it: the executable
   * registry turns it into {@code Health.down(exceptionThrown)}, which reports the throwable's type
   * and its {@link Throwable#toString()} through the health endpoint. A connector that cancels with
   * an API's rejection carries that API's text, and a rejected credential is often echoed in it, so
   * this needs the same redaction reported health and activity log entries already get.
   *
   * <p>A retry request keeps its type and its retry metadata: the registry restarts the executable
   * from them, so anything else would silently change whether it comes back, how often, and how
   * soon. Anything else is reported as a type of its own, naming the type it replaced, since the
   * message a throwable carries cannot be rewritten in place.
   */
  private Throwable redactCancellationError(Throwable exception) {
    if (exception == null || exception.getMessage() == null) {
      return exception;
    }
    var redacted = redactMessage(exception.getMessage());
    if (redacted.equals(exception.getMessage())) {
      return exception;
    }
    if (exception instanceof ConnectorRetryException retry) {
      return ConnectorRetryException.builder()
          .errorCode(retry.getErrorCode())
          .message(redacted)
          .retries(retry.getRetries())
          .backoffDuration(retry.getBackoffDuration())
          .build();
    }
    return new RedactedCancellationError(exception.getClass().getName() + ": " + redacted);
  }

  /** Stands in for a cancellation error whose message had to be rewritten to redact a secret. */
  private static final class RedactedCancellationError extends RuntimeException {

    private RedactedCancellationError(String message) {
      super(message);
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
   * model declaring {@code {{secrets.FOO:BAR}}} also admitted the truncated {@code FOO}. That is
   * the hardening it called separate work, and it is done: the single scan closes the same gap for
   * every {@code SecretUtil} caller at once, which is what made the shared use by the outbound
   * allow-list and by exception redaction safe to change.
   *
   * <p>Static because it feeds the {@code super(...)} call, before any field is assigned.
   */
  private static SecretFilter secretFilter(
      ValidInboundConnectorDetails connectorDetails, boolean secretFilterEnabled) {
    if (!secretFilterEnabled) {
      return SecretFilter.allowAll();
    }
    return SecretFilter.allowOnly(
        connectorDetails.rawPropertiesWithoutKeywords().entrySet().stream()
            .flatMap(
                entry ->
                    SecretUtil.retrieveSecretKeysInInput(entry.getValue()).stream()
                        .map(name -> new Secret(name, Arrays.asList(entry.getKey().split("\\.")))))
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
    switch (masked.severity()) {
      case DEBUG -> LOG.debug(masked.toString());
      case ERROR -> LOG.error(masked.toString());
      case INFO -> LOG.info(masked.toString());
      case WARNING -> LOG.warn(masked.toString());
    }
    this.logs.add(masked);
  }

  @Override
  public Queue<Activity> getLogs() {
    return this.logs;
  }

  @Override
  public List<InboundConnectorElement> connectorElements() {
    return connectorDetails.connectorElements();
  }

  @Override
  public Long getActivationTimestamp() {
    return activationTimestamp;
  }

  private Map<String, Object> getPropertiesWithSecrets(Map<String, Object> properties) {
    if (propertiesWithSecrets == null) {
      var handler = getSecretHandler();
      try {
        propertiesWithSecrets =
            InboundPropertyHandler.getPropertiesWithSecrets(
                handler, objectMapper, properties, new SecretContext(connectorDetails.tenantId()));
      } finally {
        capturedSecretValues.addAll(handler.getResolvedValues());
      }
    }
    return propertiesWithSecrets;
  }

  // captures are only ever unioned into a successful, complete re-read, never a substitute for one
  private List<String> secretValuesForRedaction() {
    var reRead = reReadSecretValues();
    if (reRead == null) {
      return null;
    }
    if (capturedSecretValues.isEmpty()) {
      return reRead;
    }
    var union = new ArrayList<>(reRead);
    union.addAll(capturedSecretValues);
    return union;
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

  // names across every grouped element, not just the representative one this context binds from:
  // elements are grouped on the properties deduplication hashes, and PROPERTIES_EXCLUDED_FROM_
  // DEDUPLICATION lets siblings still differ in resultExpression, activationCondition,
  // correlationKeyExpression and the rest, so a sibling can declare a secret of its own and resolve
  // it through the element context correlation hands the connector
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
      var values = secretProvider.fetchAll(names, new SecretContext(connectorDetails.tenantId()));
      // fewer values than names means a partial read, treated the same as a failed one
      return values.size() < names.size() ? null : values;
    } catch (Exception e) {
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
    // an error is only ever set through Health.down(...), so rebuilding as down keeps the status
    return Health.down(
        new Health.Error(redactMessage(error.code()), redactMessage(error.message())),
        health.getDetails());
  }

  private Activity redact(Activity activity) {
    return new Activity(
        activity.severity(),
        activity.tag(),
        activity.timestamp(),
        redactMessage(activity.message()));
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

  @Override
  public Document resolve(DocumentReference reference) {
    return documentFactory.resolve(reference);
  }

  @Override
  public Document create(DocumentCreationRequest request) {
    return documentFactory.create(request);
  }
}
