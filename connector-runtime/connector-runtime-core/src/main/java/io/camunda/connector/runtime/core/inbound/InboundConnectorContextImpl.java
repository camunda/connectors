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
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.*;
import io.camunda.connector.api.inbound.CorrelationResult.Failure;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.ActivationConditionNotMet;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.InvalidInput;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.Other;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.ZeebeClientStatus;
import io.camunda.connector.api.inbound.CorrelationResult.Success;
import io.camunda.connector.api.inbound.CorrelationResult.Success.MessageAlreadyCorrelated;
import io.camunda.connector.api.inbound.CorrelationResult.Success.MessagePublished;
import io.camunda.connector.api.inbound.CorrelationResult.Success.ProcessInstanceCreated;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.runtime.core.AbstractConnectorContext;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogEntry;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogWriter;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivitySource;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import io.camunda.connector.runtime.core.secret.SecretFilter;
import io.camunda.connector.runtime.core.secret.SecretUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InboundConnectorContextImpl extends AbstractConnectorContext
    implements InboundConnectorContext, InboundConnectorManagementContext {

  private final Logger LOG = LoggerFactory.getLogger(InboundConnectorContextImpl.class);
  private ValidInboundConnectorDetails connectorDetails;
  private final Map<String, Object> properties;

  private final InboundCorrelationHandler correlationHandler;
  private final ObjectMapper objectMapper;

  private final Consumer<Throwable> cancellationCallback;
  private final ActivityLogWriter activityLogWriter;
  private final DocumentFactory documentFactory;
  private final Long activationTimestamp;
  private Health health = Health.unknown();
  private Map<String, Object> propertiesWithSecrets;
  private volatile List<String> reReadSecrets;

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
      ActivityLogWriter activityLogWriter) {
    this(
        secretProvider,
        validationProvider,
        documentFactory,
        connectorDetails,
        correlationHandler,
        cancellationCallback,
        objectMapper,
        activityLogWriter,
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
      ActivityLogWriter activityLogWriter,
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
    this.activityLogWriter = activityLogWriter;
    this.activationTimestamp = System.currentTimeMillis();
  }

  public InboundConnectorContextImpl(
      SecretProvider secretProvider,
      ValidationProvider validationProvider,
      ValidInboundConnectorDetails connectorDetails,
      InboundCorrelationHandler correlationHandler,
      Consumer<Throwable> cancellationCallback,
      ObjectMapper objectMapper,
      ActivityLogWriter logs) {
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
    return correlationHandler.canActivate(connectorDetails.connectorElements(), variables);
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
      var result =
          correlationHandler.correlate(connectorDetails.connectorElements(), correlationRequest);
      logCorrelationResult(result);
      return result;
    } catch (ConnectorInputException connectorInputException) {
      return new CorrelationResult.Failure.InvalidInput(
          connectorInputException.getMessage(), connectorInputException);
    } catch (FeelEngineWrapperException feelEngineWrapperException) {
      log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withMessage(
                      "Failed to evaluate FEEL expression: "
                          + feelEngineWrapperException.getMessage()));
      return new CorrelationResult.Failure.Other(feelEngineWrapperException);
    } catch (Exception exception) {
      log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withMessage("Failed to correlate inbound event: " + exception.getMessage()));
      LOG.error("Failed to correlate inbound event", exception);
      return new CorrelationResult.Failure.Other(exception);
    }
  }

  private void logCorrelationResult(CorrelationResult correlationResult) {
    switch (correlationResult) {
      case Success success:
        logCorrelationSuccess(success);
        break;
      case Failure failure:
        logCorrelationFailure(failure);
        break;
    }
  }

  private void logCorrelationSuccess(Success success) {
    switch (success) {
      case ProcessInstanceCreated processInstanceCreated:
        logRuntime(
            activity ->
                activity
                    .withSeverity(Severity.INFO)
                    .withTag(ActivityLogTag.CORRELATION)
                    .withMessage("Process instance created")
                    .withData(
                        Map.of("processInstanceKey", processInstanceCreated.processInstanceKey())));
        break;
      case MessagePublished messagePublished:
        logRuntime(
            activity ->
                activity
                    .withSeverity(Severity.INFO)
                    .withTag(ActivityLogTag.CORRELATION)
                    .withMessage("Message published")
                    .withData(Map.of("messageKey", messagePublished.messageKey())));
        break;
      case MessageAlreadyCorrelated ignored:
        logRuntime(
            activity ->
                activity
                    .withSeverity(Severity.INFO)
                    .withTag(ActivityLogTag.CORRELATION)
                    .withMessage("Message already correlated"));
        break;
      case Success.MessageCorrelated messageCorrelated:
        logRuntime(
            activity ->
                activity
                    .withSeverity(Severity.INFO)
                    .withTag(ActivityLogTag.CORRELATION)
                    .withMessage("Message correlated to process instance")
                    .withData(
                        Map.of("processInstanceKey", messageCorrelated.processInstanceKey())));

        break;
      case Success.ProcessInstanceCreatedWithResult processInstanceCreatedWithResult:
        logRuntime(
            activity ->
                activity
                    .withSeverity(Severity.INFO)
                    .withTag(ActivityLogTag.CORRELATION)
                    .withMessage("Process instance created with result")
                    .withData(
                        Map.of(
                            "processInstanceKey",
                            processInstanceCreatedWithResult.processInstanceKey())));

        break;
    }
  }

  private void logCorrelationFailure(Failure failure) {
    switch (failure) {
      case ActivationConditionNotMet ignored:
        logRuntime(
            activity ->
                activity
                    .withSeverity(Severity.WARNING)
                    .withTag(ActivityLogTag.CORRELATION)
                    .withMessage("Activation condition not met"));
        break;
      case InvalidInput ignored:
        logRuntime(
            activity ->
                activity
                    .withSeverity(Severity.ERROR)
                    .withTag(ActivityLogTag.CORRELATION)
                    .withMessage("Invalid input: " + failure.message()));
        break;
      case ZeebeClientStatus ignored:
        logRuntime(
            activity ->
                activity
                    .withSeverity(Severity.ERROR)
                    .withTag(ActivityLogTag.CORRELATION)
                    .withMessage("Zeebe client status error: " + failure.message()));
        break;
      case Other ignored:
        logRuntime(
            activity ->
                activity
                    .withSeverity(Severity.ERROR)
                    .withTag(ActivityLogTag.CORRELATION)
                    .withMessage("Other error: " + failure.message()));
        break;
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
   * read. That text is fixed at construction here — {@code properties} is final and {@link
   * #updateConnectorDetails} does not rebuild it — so unlike the outbound path there is no
   * separately-timed state for a hot swap to leave stale.
   *
   * <p>This is what the filter still stops: a name only a sibling element declares, and any name in
   * text a caller supplies rather than the element's own properties. The escalation it was written
   * for -- {@link SecretUtil#replaceSecrets} running the bare pass over the brace pass's output, so
   * that a resolved value containing reference-shaped text reached a secret no model declares -- is
   * closed at its source: the single scan consumes each reference whole and never re-reads what it
   * resolved.
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
    if (health == null) {
      throw new IllegalArgumentException("Health must not be null");
    }
    var masked = redactHealth(health);
    if (!isWithheld(masked) && masked.equals(this.health)) {
      return;
    }
    var activityLog =
        Activity.newBuilder()
            .withTag(ActivityLogTag.HEALTH)
            .withMessage(
                String.format(
                    "Health status changed to %s, details: %s",
                    masked.getStatus(), masked.getDetails()))
            .withSeverity(masked.getStatus() == Health.Status.UP ? Severity.INFO : Severity.ERROR)
            .andReportHealth(masked)
            .build();
    // append the activity log to store the health status change history
    activityLogWriter.log(
        new ActivityLogEntry(
            ExecutableId.fromDeduplicationId(connectorDetails.deduplicationId()),
            ActivitySource.CONNECTOR,
            activityLog));
    this.health = masked;
  }

  @Override
  public Health getHealth() {
    return health;
  }

  @Override
  public void log(Activity log) {
    var masked = redact(log);
    if (masked.healthChange() != null) {
      this.health = masked.healthChange();
    }
    activityLogWriter.log(
        new ActivityLogEntry(
            ExecutableId.fromDeduplicationId(connectorDetails.deduplicationId()),
            ActivitySource.CONNECTOR,
            masked));
  }

  @Override
  public void log(Consumer<ActivityBuilder> activityBuilderConsumer) {
    if (activityBuilderConsumer == null) {
      throw new IllegalArgumentException("Activity builder consumer cannot be null");
    }
    var builder = Activity.newBuilder();
    activityBuilderConsumer.accept(builder);
    log(builder.build());
  }

  private void logRuntime(Consumer<ActivityBuilder> activityBuilderConsumer) {
    var builder = Activity.newBuilder();
    activityBuilderConsumer.accept(builder);
    activityLogWriter.log(
        new ActivityLogEntry(
            ExecutableId.fromDeduplicationId(connectorDetails.deduplicationId()),
            ActivitySource.RUNTIME,
            redact(builder.build())));
  }

  /**
   * The values to redact from a message, or {@code null} when they could not all be read.
   *
   * <p>The values this context substituted are only ever unioned into a successful, complete
   * re-read, never a substitute for one: a connector can declare a secret it never got around to
   * binding before the provider went down.
   */
  private List<String> secretValuesForRedaction() {
    var reRead = reReadSecretValues();
    if (reRead == null) {
      return null;
    }
    // what this context substituted, so a secret that rotated since is redacted by the value the
    // message actually carries rather than by the one the re-read came back with
    var captured = getSecretHandler().getResolvedValues();
    if (captured.isEmpty()) {
      return reRead;
    }
    var union = new ArrayList<>(reRead);
    union.addAll(captured);
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

  // names across every grouped element, not just this context's representative one
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
      var values = secretProvider.fetchAll(names, redactionSecretContext());
      // fewer values than names means a partial read, treated the same as a failed one
      return values.size() < names.size() ? null : values;
    } catch (Exception e) {
      LOG.warn("Could not fetch secret values to redact activity log: {}", e.getClass().getName());
      return null;
    }
  }

  private SecretContext redactionSecretContext() {
    return new SecretContext(connectorDetails.tenantId(), connectorDetails.processDefinitionId());
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

  // an unmaskable health can't be verified as a repeat either, so it must never be deduped away
  private static boolean isWithheld(Health health) {
    return health.getError() != null
        && REDACTION_UNAVAILABLE_MESSAGE.equals(health.getError().message());
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
        redactMessage(activity.message()),
        activity.data(),
        redactHealth(activity.healthChange()));
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
      propertiesWithSecrets =
          InboundPropertyHandler.getPropertiesWithSecrets(
              getSecretHandler(),
              objectMapper,
              properties,
              new SecretContext(
                  connectorDetails.tenantId(), connectorDetails.processDefinitionId()));
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

  @Override
  public Document resolve(DocumentReference reference) {
    return documentFactory.resolve(reference);
  }

  @Override
  public Document create(DocumentCreationRequest request) {
    return documentFactory.create(request);
  }

  @Override
  public void updateConnectorDetails(ValidInboundConnectorDetails newDetails) {
    var validationErrors = connectorDetails.checkCompatibility(newDetails);
    if (validationErrors.isPresent()) {
      // this is more of a sanity check, should never happen as long as runtime checks properties
      var message = String.join(", ", validationErrors.get());
      throw new IllegalArgumentException(
          "New InboundConnectorDetails are not compatible with the existing ones. Issues: "
              + message);
    }
    connectorDetails = newDetails;
    reReadSecrets = null;
    logRuntime(
        builder ->
            builder
                .withTag(ActivityLogTag.LIFECYCLE)
                .withSeverity(Severity.INFO)
                .withMessage("Updated configuration due to new process version deployment"));
  }
}
