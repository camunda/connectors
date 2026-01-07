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

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.camunda.client.api.search.response.SearchResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;

/**
 * Shared helper for running a Camunda Search API query through all pages using {@code endCursor}.
 */
final class PaginatedSearch {

  private PaginatedSearch() {}

  /**
   * Executes a paginated query until an empty page is returned.
   *
   * @param log logger used for trace output
   * @param entityName singular/plural name used in logs (e.g. "process definitions")
   * @param pageFetcher function fetching a single page (input = pagination index, output = page)
   * @param <T> item type
   * @return all items from all pages
   */
  static <T> List<T> queryAllPages(
      Logger log, String entityName, Function<String, SearchResponse<T>> pageFetcher) {
    List<T> items = new ArrayList<>();
    SearchResponse<T> page;

    log.trace("Running paginated query");

    String paginationIndex = null;
    do {
      page = pageFetcher.apply(paginationIndex);
      String newPaginationIdx = page.page().endCursor();

      log.trace("A page of {} {} has been fetched, continuing...", page.items().size(), entityName);

      if (isNotBlank(newPaginationIdx)) {
        paginationIndex = newPaginationIdx;
      }

      items.addAll(page.items());
    } while (page.items() != null && !page.items().isEmpty());

    return items;
  }
}
