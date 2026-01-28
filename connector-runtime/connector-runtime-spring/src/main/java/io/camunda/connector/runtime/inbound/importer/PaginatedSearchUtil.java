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
import org.slf4j.LoggerFactory;

/**
 * Shared helper for running a Camunda Search API query through all pages using {@code endCursor}.
 */
final class PaginatedSearchUtil {

  private static final Logger LOG = LoggerFactory.getLogger(PaginatedSearchUtil.class);

  private PaginatedSearchUtil() {}

  /**
   * Executes a paginated query until an empty page is returned.
   *
   * @param pageFetcher function fetching a single page (input = pagination index, output = page)
   * @param <T> item type
   * @return all items from all pages
   */
  static <T> List<T> queryAllPages(Function<String, SearchResponse<T>> pageFetcher) {
    List<T> items = new ArrayList<>();
    SearchResponse<T> page;

    String paginationIndex = null;
    do {
      page = pageFetcher.apply(paginationIndex);
      String newPaginationIdx = page.page().endCursor();

      LOG.trace("A page of size {} has been fetched, continuing...", page.items().size());

      if (isNotBlank(newPaginationIdx)) {
        paginationIndex = newPaginationIdx;
      }

      items.addAll(page.items());
    } while (page.items() != null && !page.items().isEmpty());

    return items;
  }
}
