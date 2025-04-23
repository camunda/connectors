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
