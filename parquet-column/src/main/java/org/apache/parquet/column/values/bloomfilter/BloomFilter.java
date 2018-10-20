/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.parquet.column.values.bloomfilter;

import org.apache.parquet.io.api.Binary;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A Bloom filter is a compact structure to indicate whether an item is not in a set or probably
 * in a set. The Bloom filter usually consists of a bit set that represents a elements set,
 * a hash strategy and a Bloom filter algorithm.
 */
public abstract class BloomFilter {
  // Bloom filter Hash strategy.
  public enum HashStrategy {
    MURMUR3_X64_128,
  }

  // Bloom filter algorithm.
  public enum Algorithm {
    BLOCK,
  }

  /**
   * Write the Bloom filter to an output stream. It writes the Bloom filter header includes the
   * bitset's length in size of byte, the hash strategy, the algorithm, and the bitset.
   *
   * @param out the output stream to write
   */
  public abstract void writeTo(OutputStream out) throws IOException;

  /**
   * Insert an element to the Bloom filter, the element content is represented by
   * the hash value of its plain encoding result.
   *
   * @param hash the hash result of element.
   */
  public abstract void insert(long hash);

  /**
   * Determine whether an element is in set or not.
   *
   * @param hash the hash value of element plain encoding result.
   * @return false if element is must not in set, true if element probably in set.
   */
  public abstract boolean find(long hash);

  /**
   * Compute hash for int value by using its plain encoding result.
   *
   * @param value the value to hash
   * @return hash result
   */
  public abstract long hash(int value);

  /**
   * Compute hash for long value by using its plain encoding result.
   *
   * @param value the value to hash
   * @return hash result
   */
  public abstract long hash(long value) ;

  /**
   * Compute hash for double value by using its plain encoding result.
   *
   * @param value the value to hash
   * @return hash result
   */
  public abstract long hash(double value);

  /**
   * Compute hash for float value by using its plain encoding result.
   *
   * @param value the value to hash
   * @return hash result
   */
  public abstract long hash(float value);
  /**
   * Compute hash for Binary value by using its plain encoding result.
   *
   * @param value the value to hash
   * @return hash result
   */
  public abstract long hash(Binary value);

  /**
   * Get the number of bytes for bitset in this Bloom filter.
   *
   * @return The number of bytes for bitset in this Bloom filter.
   */
  public abstract long getBitsetSize();
}
