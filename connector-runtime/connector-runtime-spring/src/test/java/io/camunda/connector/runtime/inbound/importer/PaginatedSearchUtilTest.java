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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.impl.search.response.SearchResponseImpl;
import io.camunda.client.impl.search.response.SearchResponsePageImpl;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class PaginatedSearchUtilTest {

  @Test
  public void shouldReturnAllItemsFromSinglePage() {
    // Given
    SearchResponse<String> page = createPage(List.of("item1", "item2", "item3"), "cursor1");
    SearchResponse<String> emptyPage = createPage(List.of(), null);
    @SuppressWarnings("unchecked")
    Function<String, SearchResponse<String>> pageFetcher = mock(Function.class);
    when(pageFetcher.apply(null)).thenReturn(page);
    when(pageFetcher.apply("cursor1")).thenReturn(emptyPage);

    // When
    List<String> result = PaginatedSearchUtil.queryAllPages(pageFetcher);

    // Then
    assertEquals(3, result.size());
    assertEquals(List.of("item1", "item2", "item3"), result);
    verify(pageFetcher, times(1)).apply(null);
    verify(pageFetcher, times(1)).apply("cursor1");
  }

  @Test
  public void shouldReturnAllItemsFromMultiplePages() {
    // Given
    SearchResponse<String> page1 = createPage(List.of("item1", "item2"), "cursor1");
    SearchResponse<String> page2 = createPage(List.of("item3", "item4"), "cursor2");
    SearchResponse<String> page3 = createPage(List.of("item5"), "cursor3");
    SearchResponse<String> emptyPage = createPage(List.of(), null);

    @SuppressWarnings("unchecked")
    Function<String, SearchResponse<String>> pageFetcher = mock(Function.class);
    when(pageFetcher.apply(null)).thenReturn(page1);
    when(pageFetcher.apply("cursor1")).thenReturn(page2);
    when(pageFetcher.apply("cursor2")).thenReturn(page3);
    when(pageFetcher.apply("cursor3")).thenReturn(emptyPage);

    // When
    List<String> result = PaginatedSearchUtil.queryAllPages(pageFetcher);

    // Then
    assertEquals(5, result.size());
    assertEquals(List.of("item1", "item2", "item3", "item4", "item5"), result);
    verify(pageFetcher, times(1)).apply(null);
    verify(pageFetcher, times(1)).apply("cursor1");
    verify(pageFetcher, times(1)).apply("cursor2");
    verify(pageFetcher, times(1)).apply("cursor3");
  }

  @Test
  public void shouldHandleEmptyResponse() {
    // Given
    SearchResponse<String> emptyPage = createPage(List.of(), null);
    @SuppressWarnings("unchecked")
    Function<String, SearchResponse<String>> pageFetcher = mock(Function.class);
    when(pageFetcher.apply(null)).thenReturn(emptyPage);

    // When
    List<String> result = PaginatedSearchUtil.queryAllPages(pageFetcher);

    // Then
    assertTrue(result.isEmpty());
    verify(pageFetcher, times(1)).apply(null);
  }

  @Test
  public void shouldStopWhenEmptyPageReturned() {
    // Given
    SearchResponse<String> page1 = createPage(List.of("item1"), "cursor1");
    SearchResponse<String> emptyPage = createPage(List.of(), null);

    @SuppressWarnings("unchecked")
    Function<String, SearchResponse<String>> pageFetcher = mock(Function.class);
    when(pageFetcher.apply(null)).thenReturn(page1);
    when(pageFetcher.apply("cursor1")).thenReturn(emptyPage);

    // When
    List<String> result = PaginatedSearchUtil.queryAllPages(pageFetcher);

    // Then
    assertEquals(1, result.size());
    assertEquals(List.of("item1"), result);
    verify(pageFetcher, times(1)).apply(null);
    verify(pageFetcher, times(1)).apply("cursor1");
  }

  private <T> SearchResponse<T> createPage(List<T> items, String endCursor) {
    final var page = new SearchResponsePageImpl((long) items.size(), null, endCursor);
    return new SearchResponseImpl<>(items, page);
  }
}
