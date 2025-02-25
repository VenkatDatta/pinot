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
package org.apache.pinot.query.planner;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.calcite.util.Pair;
import org.apache.pinot.query.planner.physical.DispatchablePlanMetadata;
import org.apache.pinot.query.planner.stage.StageNode;
import org.apache.pinot.query.routing.MailboxMetadata;
import org.apache.pinot.query.routing.QueryServerInstance;
import org.apache.pinot.query.routing.StageMetadata;
import org.apache.pinot.query.routing.VirtualServerAddress;
import org.apache.pinot.query.routing.WorkerMetadata;


/**
 * The {@code QueryPlan} is the dispatchable query execution plan from the result of
 * {@link org.apache.pinot.query.planner.logical.StagePlanner}.
 *
 * <p>QueryPlan should contain the necessary stage boundary information and the cross exchange information
 * for:
 * <ul>
 *   <li>dispatch individual stages to executor.</li>
 *   <li>instruction for stage executor to establish connection channels to other stages.</li>
 *   <li>instruction for encoding data blocks & transferring between stages based on partitioning scheme.</li>
 * </ul>
 */
public class QueryPlan {
  private final List<Pair<Integer, String>> _queryResultFields;
  private final Map<Integer, StageNode> _queryStageMap;
  private final List<StageMetadata> _stageMetadataList;
  private final Map<Integer, DispatchablePlanMetadata> _dispatchablePlanMetadataMap;

  public QueryPlan(List<Pair<Integer, String>> fields, Map<Integer, StageNode> queryStageMap,
      Map<Integer, DispatchablePlanMetadata> dispatchablePlanMetadataMap) {
    _queryResultFields = fields;
    _queryStageMap = queryStageMap;
    _dispatchablePlanMetadataMap = dispatchablePlanMetadataMap;
    _stageMetadataList = constructStageMetadataList(_dispatchablePlanMetadataMap);
  }

  /**
   * Get the map between stageID and the stage plan root node.
   * @return stage plan map.
   */
  public Map<Integer, StageNode> getQueryStageMap() {
    return _queryStageMap;
  }

  /**
   * Get the stage metadata information based on stageId.
   * @return stage metadata info.
   */
  public StageMetadata getStageMetadata(int stageId) {
    return _stageMetadataList.get(stageId);
  }

  /**
   * Get the dispatch metadata information.
   * @return dispatch metadata info.
   */
  public Map<Integer, DispatchablePlanMetadata> getDispatchablePlanMetadataMap() {
    return _dispatchablePlanMetadataMap;
  }

  /**
   * Get the query result field.
   * @return query result field.
   */
  public List<Pair<Integer, String>> getQueryResultFields() {
    return _queryResultFields;
  }

  /**
   * Explains the {@code QueryPlan}
   *
   * @return a human-readable tree explaining the query plan
   * @see ExplainPlanStageVisitor#explain(QueryPlan)
   * @apiNote this is <b>NOT</b> identical to the SQL {@code EXPLAIN PLAN FOR} functionality
   *          and is instead intended to be used by developers debugging during feature
   *          development
   */
  public String explain() {
    return ExplainPlanStageVisitor.explain(this);
  }

  /**
   * Convert the {@link DispatchablePlanMetadata} into dispatchable info for each stage/worker.
   */
  private static List<StageMetadata> constructStageMetadataList(
      Map<Integer, DispatchablePlanMetadata> dispatchablePlanMetadataMap) {
    StageMetadata[] stageMetadataList = new StageMetadata[dispatchablePlanMetadataMap.size()];
    for (Map.Entry<Integer, DispatchablePlanMetadata> dispatchableEntry : dispatchablePlanMetadataMap.entrySet()) {
      DispatchablePlanMetadata dispatchablePlanMetadata = dispatchableEntry.getValue();

      // construct each worker metadata
      WorkerMetadata[] workerMetadataList = new WorkerMetadata[dispatchablePlanMetadata.getTotalWorkerCount()];
      for (Map.Entry<QueryServerInstance, List<Integer>> queryServerEntry
          : dispatchablePlanMetadata.getServerInstanceToWorkerIdMap().entrySet()) {
        for (int workerId : queryServerEntry.getValue()) {
          VirtualServerAddress virtualServerAddress = new VirtualServerAddress(queryServerEntry.getKey(), workerId);
          WorkerMetadata.Builder builder = new WorkerMetadata.Builder();
          builder.setVirtualServerAddress(virtualServerAddress);
          Map<Integer, MailboxMetadata> stageToMailboxMetadata =
              dispatchablePlanMetadata.getWorkerIdToMailBoxIdsMap().get(workerId);
          builder.putAllMailBoxInfosMap(stageToMailboxMetadata);
          if (dispatchablePlanMetadata.getScannedTables().size() == 1) {
            builder.addTableSegmentsMap(dispatchablePlanMetadata.getWorkerIdToSegmentsMap().get(workerId));
          }
          workerMetadataList[workerId] = builder.build();
        }
      }

      // construct the stageMetadata
      int stageId = dispatchableEntry.getKey();
      StageMetadata.Builder builder = new StageMetadata.Builder();
      builder.setWorkerMetadataList(Arrays.asList(workerMetadataList));
      if (dispatchablePlanMetadata.getScannedTables().size() == 1) {
        builder.addTableName(dispatchablePlanMetadata.getScannedTables().get(0));
      }
      if (dispatchablePlanMetadata.getTimeBoundaryInfo() != null) {
        builder.addTimeBoundaryInfo(dispatchablePlanMetadata.getTimeBoundaryInfo());
      }
      stageMetadataList[stageId] = builder.build();
    }
    return Arrays.asList(stageMetadataList);
  }
}
