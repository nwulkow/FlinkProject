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
package de.fuberlin.de.largedataanalysis

import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.ml.common.{WeightVector, LabeledVector}
import org.apache.flink.ml.MLUtils
import org.apache.flink.ml.classification.SVM
import org.apache.flink.api.scala.DataSet

object MLSVM{

  def main(args: Array[String]) {
    val env = ExecutionEnvironment.getExecutionEnvironment
    // Einlesen von Training- und Test-Set
    val astroTrain: DataSet[LabeledVector] = MLUtils.readLibSVM(env ,"/home/mi/nwulkow/ADL/Data/regression_train_100.txt")  //SVMtrain.txt")
    val astroTest: DataSet[LabeledVector] = MLUtils.readLibSVM(env, "/home/mi/nwulkow/ADL/Data/regression_test_100.txt")

    val svm = SVM()
    .setBlocks(env.getParallelism)
    .setIterations(100)
    .setRegularization(0.001)
    .setStepsize(0.1)
    .setSeed(42)

    val t1 = System.currentTimeMillis()

    svm.fit(astroTrain)

    val t2 = System.currentTimeMillis()

    val time = t2 - t1

    println("Time used in ms = " + time)

    val predictionPairs = svm.predict(astroTest)
    val f = svm.weightsOption
    val r =  f.get.collect()(0).data

    predictionPairs.writeAsCsv("/home/mi/nwulkow/ADL/Data/SVMoutput/", "\n", ",")
    env.execute("Scala SVM")

// Den relativen Fehler habe ich dann woanders berechnet
}

}