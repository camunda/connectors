package io.camunda.connector.runtime.inbound.webhook;

import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.runtime.inbound.lifecycle.ActiveInboundConnector;
import java.util.regex.Pattern;

final class WebhookConnectorValidationUtil {
  private static final String WARNING_TAG = "Warning";
  // Reflect changes to this pattern in webhook element templates
  private static final Pattern CURRENT_WEBHOOK_PATH_PATTERN =
      Pattern.compile("^[a-zA-Z0-9]+([-_][a-zA-Z0-9]+)*$");
  private static final String DEPRECATED_WEBHOOK_MESSAGE_SUFFIX =
      ". This may lead to unexpected behavior. Consider adjusting the path to match the pattern: "
          + CURRENT_WEBHOOK_PATH_PATTERN;
  private static final String DEPRECATED_WEBHOOK_MESSAGE_PREFIX = "Deprecated webhook path: ";

  static void logIfWebhookPathDeprecated(ActiveInboundConnector connector, String webhook) {

    if (!CURRENT_WEBHOOK_PATH_PATTERN.matcher(webhook).matches()) {
      String message =
          DEPRECATED_WEBHOOK_MESSAGE_PREFIX + webhook + DEPRECATED_WEBHOOK_MESSAGE_SUFFIX;
      Activity activity = Activity.level(Severity.WARNING).tag(WARNING_TAG).message(message);

      connector.context().log(activity);
    }
  }
}
