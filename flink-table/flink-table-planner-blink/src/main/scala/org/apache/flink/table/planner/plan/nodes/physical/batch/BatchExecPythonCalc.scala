/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.planner.plan.nodes.physical.batch

import org.apache.calcite.plan.{RelOptCluster, RelTraitSet}
import org.apache.calcite.rel.RelNode
import org.apache.calcite.rel.`type`.RelDataType
import org.apache.calcite.rel.core.Calc
import org.apache.calcite.rex.RexProgram
import org.apache.flink.api.dag.Transformation
import org.apache.flink.configuration.{ConfigOption, Configuration, MemorySize}
import org.apache.flink.table.dataformat.BaseRow
import org.apache.flink.table.planner.delegation.BatchPlanner
import org.apache.flink.table.planner.plan.nodes.common.CommonPythonCalc
import org.apache.flink.table.planner.plan.nodes.exec.ExecNode

/**
  * Batch physical RelNode for Python ScalarFunctions.
  */
class BatchExecPythonCalc(
    cluster: RelOptCluster,
    traitSet: RelTraitSet,
    inputRel: RelNode,
    calcProgram: RexProgram,
    outputRowType: RelDataType)
  extends BatchExecCalcBase(
    cluster,
    traitSet,
    inputRel,
    calcProgram,
    outputRowType)
  with CommonPythonCalc {

  override def copy(traitSet: RelTraitSet, child: RelNode, program: RexProgram): Calc = {
    new BatchExecPythonCalc(cluster, traitSet, child, program, outputRowType)
  }

  override protected def translateToPlanInternal(planner: BatchPlanner): Transformation[BaseRow] = {
    val inputTransform = getInputNodes.get(0).translateToPlan(planner)
      .asInstanceOf[Transformation[BaseRow]]
    val ret = createPythonOneInputTransformation(
      inputTransform,
      calcProgram,
      "BatchExecPythonCalc")

    ExecNode.setManagedMemoryWeight(
      ret, getPythonWorkerMemory(planner.getTableConfig.getConfiguration))
  }

  private def getPythonWorkerMemory(config: Configuration): Long = {
    val clazz = loadClass("org.apache.flink.python.PythonOptions")
    val pythonFrameworkMemorySize = MemorySize.parse(
      config.getString(
        clazz.getField("PYTHON_FRAMEWORK_MEMORY_SIZE").get(null)
          .asInstanceOf[ConfigOption[String]]))
    val pythonBufferMemorySize = MemorySize.parse(
      config.getString(
        clazz.getField("PYTHON_DATA_BUFFER_MEMORY_SIZE").get(null)
          .asInstanceOf[ConfigOption[String]]))
    pythonFrameworkMemorySize.add(pythonBufferMemorySize).getBytes
  }
}