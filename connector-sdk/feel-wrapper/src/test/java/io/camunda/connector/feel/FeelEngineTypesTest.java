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
package io.camunda.connector.feel;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import org.camunda.feel.FeelEngine;
import org.camunda.feel.impl.JavaValueMapper;
import org.camunda.feel.impl.SpiServiceLoader;
import org.junit.jupiter.api.Test;
import scala.jdk.javaapi.CollectionConverters;

public class FeelEngineTypesTest {

  private final FeelEngine feelEngine =
      new FeelEngine.Builder()
          .customValueMapper(new JavaValueMapper())
          .functionProvider(SpiServiceLoader.loadFunctionProvider())
          .build();

  record RecordWithInputStream(InputStream is) {}

  // map with an input stream and an integer
  Map<String, Object> map1 =
      Map.of("a", new RecordWithInputStream(new ByteArrayInputStream("Hello".getBytes())), "b", 2);

  @Test
  public void testFeelEngineWithInputStream() {
    var scalaMap = scala.collection.immutable.Map.from(CollectionConverters.asScala(map1));
    var result = feelEngine.evalExpression("a", scalaMap);
    if (result.isRight()) {
      var castedResult = result.right().get();
      System.out.println(castedResult);
    } else {
      System.out.println(result.left().get());
    }
  }

  record RecordWithByteArray(byte[] bytes) {}

  // map with a byte array and an integer
  Map<String, Object> map2 = Map.of("a", new RecordWithByteArray("Hello".getBytes()), "b", 2);

  @Test
  public void testFeelEngineWithByteArray() {
    var scalaMap = scala.collection.immutable.Map.from(CollectionConverters.asScala(map2));
    var result = feelEngine.evalExpression("a", scalaMap);
    if (result.isRight()) {
      var castedResult = result.right().get();
      System.out.println(castedResult);
    } else {
      System.out.println(result.left().get());
    }
  }

  record RecordWithString(String s) {}

  // map with a string and an integer
  Map<String, Object> map3 =
      Map.of("a", Map.of("a", new ByteArrayInputStream("hello".getBytes())), "b", 2);

  @Test
  public void testFeelEngineWithString() {
    var scalaMap = scala.collection.immutable.Map.from(CollectionConverters.asScala(map3));
    var result = feelEngine.evalExpression("a", scalaMap);
    if (result.isRight()) {
      var castedResult = result.right().get();
      System.out.println(castedResult.getClass());
    } else {
      System.out.println(result.left().get());
    }
  }

  public static class HelloType {
    public String hello = "hello";
    private String hiddenHello = "hiddenHello";
  }

  // map with a custom class and an integer
  Map<String, Object> map4 = Map.of("a", Map.of("a", new HelloType()), "b", 2);

  @Test
  public void testFeelEngineWithCustomClass() {
    var result = feelEngine.evalExpression("a", map4);
    if (result.isRight()) {
      var castedResult = result.right().get();
      System.out.println(castedResult.getClass());
      System.out.println(castedResult);
    } else {
      System.out.println(result.left().get());
    }
  }
}
