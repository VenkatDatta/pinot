/**
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
package org.apache.pinot.core.query.distinct.raw;

import org.apache.pinot.common.request.context.ExpressionContext;
import org.apache.pinot.core.common.BlockValSet;
import org.apache.pinot.core.operator.blocks.ValueBlock;
import org.apache.pinot.core.query.distinct.DistinctExecutor;
import org.apache.pinot.spi.data.FieldSpec.DataType;
import org.roaringbitmap.RoaringBitmap;


/**
 * {@link DistinctExecutor} for distinct only queries with single raw DOUBLE column.
 */
public class RawDoubleSingleColumnDistinctOnlyExecutor extends BaseRawDoubleSingleColumnDistinctExecutor {

  public RawDoubleSingleColumnDistinctOnlyExecutor(ExpressionContext expression, DataType dataType, int limit,
      boolean nullHandlingEnabled) {
    super(expression, dataType, limit, nullHandlingEnabled);
  }

  @Override
  public boolean process(ValueBlock valueBlock) {
    BlockValSet blockValueSet = valueBlock.getBlockValueSet(_expression);
    int numDocs = valueBlock.getNumDocs();
    if (blockValueSet.isSingleValue()) {
      double[] values = blockValueSet.getDoubleValuesSV();
      if (_nullHandlingEnabled) {
        // TODO(nhejazi): consider having a separate set of classes to handle the case with null handling enabled.
        RoaringBitmap nullBitmap = blockValueSet.getNullBitmap();
        for (int i = 0; i < numDocs; i++) {
          if (nullBitmap != null && nullBitmap.contains(i)) {
            _hasNull = true;
          } else {
            _valueSet.add(values[i]);
            if (_valueSet.size() >= _limit - (_hasNull ? 1 : 0)) {
              return true;
            }
          }
        }
      } else {
        for (int i = 0; i < numDocs; i++) {
          _valueSet.add(values[i]);
          if (_valueSet.size() >= _limit) {
            return true;
          }
        }
      }
    } else {
      // TODO(nhejazi): support proper null handling in multi-valued columns.
      double[][] values = blockValueSet.getDoubleValuesMV();
      for (int i = 0; i < numDocs; i++) {
        for (double value : values[i]) {
          _valueSet.add(value);
          if (_valueSet.size() >= _limit) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
