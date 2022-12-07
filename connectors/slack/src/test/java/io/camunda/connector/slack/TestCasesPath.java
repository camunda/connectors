/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.slack;

interface TestCasesPath {
  String PATH = "src/test/resources/requests/test-cases/";

  String REPLACE_SECRETS = PATH + "replace-secrets.json";
  String EXECUTE_WITH_EMAIL = PATH + "execute-function-with-email.json";
  String EXECUTE_WITH_USERNAME = PATH + "execute-function-with-username.json";
  String EXECUTE_WITH_CHANNEL_NAME = PATH + "execute-function-with-channel-name.json";
  String EXECUTE_CREATE_CHANNEL = PATH + "execute-create-channel.json";

  String EXECUTE_INVITE_TO_CHANNEL = PATH + "execute-invite-to-channel.json";

  String VALIDATE_REQUIRED_FIELDS_FAIL = PATH + "validate-fields-fail.json";
  String FROM_JSON_FAIL = PATH + "with-wrong-method.json";
}
