// Copyright 2018 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.collect.nestedset;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.build.lib.skyframe.serialization.ObjectCodecs;
import com.google.devtools.build.lib.skyframe.serialization.SerializationContext;
import com.google.devtools.build.lib.skyframe.serialization.SerializationException;
import com.google.devtools.build.lib.skyframe.serialization.testutils.SerializationTester;
import java.io.IOException;

/** Utilities for testing NestedSet serialization. */
public class NestedSetCodecTestUtils {

  private static final NestedSet<String> SHARED_NESTED_SET =
      NestedSetBuilder.<String>stableOrder().add("e").build();

  /** Perform serialization/deserialization checks for several simple NestedSet examples. */
  public static void checkCodec(ObjectCodecs objectCodecs, boolean allowFutureBlocking)
      throws Exception {
    new SerializationTester(
            NestedSetBuilder.emptySet(Order.STABLE_ORDER),
            NestedSetBuilder.emptySet(Order.NAIVE_LINK_ORDER),
            NestedSetBuilder.create(Order.STABLE_ORDER, "a"),
            NestedSetBuilder.create(Order.STABLE_ORDER, "a", "b", "c"),
            NestedSetBuilder.<String>stableOrder()
                .add("a")
                .add("b")
                .addTransitive(
                    NestedSetBuilder.<String>stableOrder()
                        .add("c")
                        .addTransitive(SHARED_NESTED_SET)
                        .build())
                .addTransitive(
                    NestedSetBuilder.<String>stableOrder()
                        .add("d")
                        .addTransitive(SHARED_NESTED_SET)
                        .build())
                .addTransitive(NestedSetBuilder.emptySet(Order.STABLE_ORDER))
                .build())
        .setObjectCodecs(objectCodecs)
        .makeMemoizingAndAllowFutureBlocking(allowFutureBlocking)
        .setVerificationFunction(NestedSetCodecTestUtils::verifyDeserialization)
        .runTests();
  }

  public static ListenableFuture<Void> writeToStoreFuture(
      NestedSetStore store, NestedSet<?> nestedSet, SerializationContext serializationContext)
      throws IOException, SerializationException {
    return store
        .computeFingerprintAndStore((Object[]) nestedSet.rawChildren(), serializationContext)
        .writeStatus();
  }

  private static void verifyDeserialization(
      NestedSet<String> subject, NestedSet<String> deserialized) {
    assertThat(subject.getOrder()).isEqualTo(deserialized.getOrder());
    assertThat(subject.toSet()).isEqualTo(deserialized.toSet());
    verifyStructure(subject.rawChildren(), deserialized.rawChildren());
  }

  private static void verifyStructure(Object lhs, Object rhs) {
    if (lhs == NestedSet.EMPTY_CHILDREN) {
      assertThat(rhs).isSameAs(NestedSet.EMPTY_CHILDREN);
    } else if (lhs instanceof Object[]) {
      assertThat(rhs).isInstanceOf(Object[].class);
      Object[] lhsArray = (Object[]) lhs;
      Object[] rhsArray = (Object[]) rhs;
      int n = lhsArray.length;
      assertThat(rhsArray).hasLength(n);
      for (int i = 0; i < n; ++i) {
        verifyStructure(lhsArray[i], rhsArray[i]);
      }
    } else {
      assertThat(lhs).isEqualTo(rhs);
    }
  }
}
