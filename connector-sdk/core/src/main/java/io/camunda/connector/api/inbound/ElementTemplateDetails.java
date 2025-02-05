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
package io.camunda.connector.api.inbound;

import io.camunda.zeebe.model.bpmn.instance.BaseElement;

/**
 * Provides details about the element template applied to the element.
 *
 * @param id The id of the element template (io.camunda.connectors.webhook.WebhookConnector.v1 for
 *     example)
 * @param version The version of the element template
 * @param icon The icon of the element template (might be SVG or PNG)
 */
public record ElementTemplateDetails(String id, String version, String icon) {
  private static final String NAMESPACE = "http://camunda.org/schema/zeebe/1.0";

  private static final String TEMPLATE_ID = "modelerTemplate";
  private static final String TEMPLATE_VERSION = "modelerTemplateVersion";
  private static final String TEMPLATE_ICON = "modelerTemplateIcon";

  public ElementTemplateDetails(BaseElement element) {
    this(
        element.getAttributeValueNs(NAMESPACE, TEMPLATE_ID),
        element.getAttributeValueNs(NAMESPACE, TEMPLATE_VERSION),
        element.getAttributeValueNs(NAMESPACE, TEMPLATE_ICON));
  }
}
