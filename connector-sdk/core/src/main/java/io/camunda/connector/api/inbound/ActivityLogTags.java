package io.camunda.connector.api.inbound;

/**
 * Common tags that can be used in activity logs when logging activities inside an inbound connector.
 */
public final class ActivityLogTags {

  /**
   * A log tag for entries related to the lifecycle of the event consumer (managed by the connector).
   * For example, use this tag to log that a queue consumer has restarted.
   */
  public static final String CONSUMER = "Consumer";

  /**
   * A log tag for entries related to message/event processing.
   * For example, use this tag to log that an incoming event was processed by the connector.
   */
  public static final String MESSAGE = "Message";
}
