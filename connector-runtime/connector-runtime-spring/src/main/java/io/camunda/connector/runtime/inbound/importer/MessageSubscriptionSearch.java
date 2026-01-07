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
package io.camunda.connector.runtime.inbound.importer;

import io.camunda.client.api.search.response.MessageSubscription;
import io.camunda.connector.runtime.inbound.search.SearchQueryClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageSubscriptionSearch {

  private static final Logger LOG = LoggerFactory.getLogger(MessageSubscriptionSearch.class);
  private final SearchQueryClient searchQueryClient;

  public MessageSubscriptionSearch(SearchQueryClient searchQueryClient) {
    this.searchQueryClient = searchQueryClient;
  }

  public List<MessageSubscription> query() {
    LOG.trace("Query message subscriptions...");

    var messageSubscriptions =
        PaginatedSearch.queryAllPages(
            LOG, "message subscriptions", searchQueryClient::queryMessageSubscriptions);

    LOG.debug(
        "Fetching message subscriptions has been correctly executed: {} subscriptions found",
        messageSubscriptions.size());

    return messageSubscriptions;
  }
}
