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
package io.camunda.connector.runtime.instances.reducer;

import java.util.ArrayList;
import java.util.Collection;

public class Reducers {

  public static <T> Reducer<Collection<T>> mergeListsReducer() {
    return (a, b) -> {
      if (a == null || a.isEmpty()) {
        return b;
      }
      if (b == null || b.isEmpty()) {
        return a;
      }
      var result = new ArrayList<T>();
      result.addAll(a);
      result.addAll(b);
      return result;
    };
  }
}
