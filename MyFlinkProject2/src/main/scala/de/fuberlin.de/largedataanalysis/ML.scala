/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.examples.scala.relational

import org.apache.flink.api.scala.DataSet
import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.api.scala._
import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.examples.scala.relational.TPCHQuery3.Customer

import org.apache.flink.ml.common.LabeledVector
import org.apache.flink.ml.math.DenseVector
import org.apache.flink.ml.math.DenseVector
import org.apache.flink.ml.optimization.PredictionFunction
import org.apache.flink.ml.regression
import org.apache.flink.ml.pipeline.Predictor
import org.apache.flink.ml.MLUtils

import org.apache.flink.ml.classification.SVM
import org.apache.flink.ml.regression.MultipleLinearRegression
import org.apache.flink.ml.regression.MultipleLinearRegression

object ML {

  def main(args: Array[String]) {

    val env = ExecutionEnvironment.getExecutionEnvironment
    val trainingdata = env.readCsvFile[(String, String)]("/home/mi/nwulkow/ADL/regression_100.txt")
    val testingdata = env.readCsvFile[(String, String)]("/home/mi/nwulkow/ADL/regressiontestclass_test.txt")


    val trainingLV = trainingdata
      .map { tuple =>
      val list = tuple.productIterator.toList
      val numList = list.map(_.asInstanceOf[String].toDouble)
      LabeledVector(1,DenseVector(numList.take(1).toArray))
    }

    val testingLV = testingdata
      .map { tuple =>
      val list = tuple.productIterator.toList
      val numList = list.map(_.asInstanceOf[String].toDouble)
      Vector(numList.take(1).toArray)
    }


    val mlr = MultipleLinearRegression()
      .setStepsize(1.0)
      .setIterations(100)
      .setConvergenceThreshold(0.001)
    mlr.fit(trainingLV)
    // The fitted model can now be used to make predictions
    val predictions = mlr.predict(testingLV)


  }


  private def getDataSet(env: ExecutionEnvironment, path: String): DataSet[(Double,Double)] = {
    env.readCsvFile[(Double,Double)](
      path,
      fieldDelimiter = ",")
  }




}
