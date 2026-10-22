/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.box.BoxUtil.Entry;
import io.camunda.connector.box.BoxUtil.Page;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class BoxUtilPaginationTest {

  @Test
  void findsItemOnLaterPageWhenAnEarlierPageIsShort() {
    var markers = new ArrayList<String>();
    var result =
        BoxUtil.findChildIdByName(
            "target",
            marker -> {
              markers.add(marker);
              return switch (markers.size()) {
                case 1 -> new Page(List.of(new Entry("1", "a")), "m1");
                case 2 -> new Page(List.of(new Entry("2", "b")), "m2");
                default -> new Page(List.of(new Entry("3", "target")), null);
              };
            });

    assertThat(result).contains("3");
    assertThat(markers).containsExactly(null, "m1", "m2");
  }

  @Test
  void traversesMoreThanTenThousandChildren() {
    int pages = 11;
    int pageSize = 1000;
    var fetched = new AtomicInteger();
    var result =
        BoxUtil.findChildIdByName(
            "item-10500",
            marker -> {
              int page = fetched.getAndIncrement();
              var entries = new ArrayList<Entry>();
              for (int i = page * pageSize; i < (page + 1) * pageSize; i++) {
                entries.add(new Entry("id-" + i, "item-" + i));
              }
              return new Page(entries, page + 1 < pages ? String.valueOf(page + 1) : "");
            });

    assertThat(result).contains("id-10500");
  }

  @Test
  void returnsEmptyAfterLastPageWhenNotFound() {
    var result =
        BoxUtil.findChildIdByName(
            "missing", marker -> new Page(List.of(new Entry("1", "a")), marker == null ? "m" : ""));

    assertThat(result).isEmpty();
  }
}
