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
package io.camunda.connector.runtime.inbound.webhook;

import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;
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

  static void logIfWebhookPathDeprecated(RegisteredExecutable.Activated connector, String webhook) {

    if (!CURRENT_WEBHOOK_PATH_PATTERN.matcher(webhook).matches()) {
      String message =
          DEPRECATED_WEBHOOK_MESSAGE_PREFIX + webhook + DEPRECATED_WEBHOOK_MESSAGE_SUFFIX;
      Activity activity = Activity.level(Severity.WARNING).tag(WARNING_TAG).message(message);

      connector.context().log(activity);
    }
  }
}
