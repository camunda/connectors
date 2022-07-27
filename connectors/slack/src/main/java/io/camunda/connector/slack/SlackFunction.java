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
package io.camunda.connector.slack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.slack.api.Slack;
import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.ConnectorFunction;
import io.camunda.connector.api.Validator;

public class SlackFunction implements ConnectorFunction {
  private static final Slack SLACK = Slack.getInstance();

  private static final SlackRequestDeserializer DESERIALIZER =
      new SlackRequestDeserializer("method")
          .registerType("chat.postMessage", ChatPostMessageData.class);
  private static final Gson GSON =
      new GsonBuilder().registerTypeAdapter(SlackRequest.class, DESERIALIZER).create();

  @Override
  public Object execute(ConnectorContext context) throws Exception {

    final var variables = context.getVariables();

    final var slackRequest = GSON.fromJson(variables, SlackRequest.class);

    final var validator = new Validator();
    slackRequest.validate(validator);
    validator.validate();

    slackRequest.replaceSecrets(context.getSecretStore());

    return slackRequest.invoke(SLACK);
  }
}
