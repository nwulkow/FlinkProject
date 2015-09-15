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

import org.apache.flink.api.scala._
import org.apache.flink.ml.classification.SVM
import org.apache.flink.ml.common.LabeledVector
import org.apache.flink.ml.math.DenseVector


object GeneMerging_Clean {

  def main(args: Array[String]) {
    if (!parseParameters(args)) {
      return
    }

    // get execution environment
    val env = ExecutionEnvironment.getExecutionEnvironment

    val genedatafull = getGeneDataFullSet(env, firstDatasetPath)  // Läd die Datei mit ID und Anzahl an verschiedenen Genen (Es werden 20 Gene betrachtet)
    val stagedatafull = getStageDataFullSet(env, secondDatasetPath) // Läd die Datei mit ID und CancerStage

  // Beide DataSets werden anhand der ID gejoint
    val result = stagedatafull.join(genedatafull).where("ID").equalTo("ID").apply((f,s) => new DataFull(f.ID,f.CancerStage, s.Gene1, s.Gene2,s.Gene3, s.Gene4,s.Gene5, s.Gene6,s.Gene7, s.Gene8,s.Gene9, s.Gene10,s.Gene11, s.Gene12,s.Gene13, s.Gene14,s.Gene15, s.Gene16,s.Gene17, s.Gene18,s.Gene19, s.Gene20))
    // Jede Zeile im Resultat davon wird auf einen neuen LabeledVector abgebildet
    val lvset = result.map(c => LabeledVector(c.CancerStage.toDouble ,DenseVector(c.Gene1.toDouble, c.Gene2.toDouble,c.Gene3.toDouble, c.Gene4.toDouble,c.Gene5.toDouble, c.Gene6.toDouble,c.Gene7.toDouble, c.Gene8.toDouble,c.Gene9.toDouble, c.Gene10.toDouble,c.Gene11.toDouble, c.Gene12.toDouble, c.Gene13.toDouble, c.Gene14.toDouble,c.Gene15.toDouble, c.Gene16.toDouble,c.Gene17.toDouble, c.Gene18.toDouble,c.Gene19.toDouble, c.Gene20.toDouble)))


    val svm = SVM()
      .setBlocks(env.getParallelism)
      .setIterations(100)
      .setRegularization(0.001)
      .setStepsize(0.1)
      .setSeed(42)

    //SVM wird angewendet
    svm.fit(lvset)
    print(svm.weightsOption)



    // emit result
    result.writeAsCsv(outputPath, "\n", ",")

    // execute program
    env.execute("GeneMerging")
  }

  // *************************************************************************
  //     USER DATA TYPES
  // *************************************************************************

  case class GeneDataFull(ID: String, Gene1: String, Gene2: String, Gene3: String ,Gene4: String, Gene5: String, Gene6: String, Gene7: String, Gene8: String, Gene9: String, Gene10: String, Gene11: String, Gene12: String, Gene13: String ,Gene14: String, Gene15: String, Gene16: String, Gene17 : String, Gene18: String, Gene19: String, Gene20: String)
  case class StageDataFull(ID: String, CancerStage: String)
  case class DataFull(ID: String, CancerStage: String, Gene1: String, Gene2: String, Gene3: String ,Gene4: String, Gene5: String,Gene6: String, Gene7: String, Gene8: String, Gene9: String, Gene10: String, Gene11: String, Gene12: String, Gene13: String ,Gene14: String, Gene15: String, Gene16: String, Gene17 : String, Gene18: String, Gene19: String,Gene20: String)
  // *************************************************************************
  //     UTIL METHODS
  // *************************************************************************

  private var lineitemPath: String = null
  private var firstDatasetPath: String =  ""
  private var secondDatasetPath: String = ""
  private var outputPath: String = null

  private def parseParameters(args: Array[String]): Boolean = {
    if (args.length == 4) {
      lineitemPath = args(0)
      firstDatasetPath = args(1)
      secondDatasetPath = args(2)
      outputPath = args(3)
      true
    } else {

      false
    }
  }


  private def getGeneDataFullSet(env: ExecutionEnvironment, path: String): DataSet[GeneDataFull] = {
    env.readCsvFile[GeneDataFull](
      path,
      fieldDelimiter = ",")
  }


  private def getStageDataFullSet(env: ExecutionEnvironment, path: String): DataSet[StageDataFull] = {
    env.readCsvFile[StageDataFull](
      path,
      fieldDelimiter = ",")
  }
}
