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
import java.util.function.Function;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared helper for running a Camunda Search API query through all pages using {@code endCursor}.
 */
final class PaginatedSearchUtil {

  private static final Logger LOG = LoggerFactory.getLogger(PaginatedSearchUtil.class);

  private PaginatedSearchUtil() {}

  /**
   * Executes a paginated query lazily, fetching the next page only when the current page's items
   * have been consumed. Iteration stops when a page is empty or its {@code endCursor} is blank.
   *
   * @param pageFetcher function fetching a single page (input = pagination cursor, output = page)
   * @param <T> item type
   * @return a lazy stream of all items across all pages
   */
  static <T> Stream<T> queryAllPages(Function<String, SearchResponse<T>> pageFetcher) {
    SearchResponse<T> firstPage = pageFetcher.apply(null);
    return Stream.iterate(
            firstPage,
            page -> page != null && page.items() != null && !page.items().isEmpty(),
            page -> {
              String cursor = page.page().endCursor();
              return isNotBlank(cursor) ? pageFetcher.apply(cursor) : null;
            })
        .peek(
            page ->
                LOG.trace("A page of size {} has been fetched, continuing...", page.items().size()))
        .flatMap(page -> page.items().stream());
  }
}
