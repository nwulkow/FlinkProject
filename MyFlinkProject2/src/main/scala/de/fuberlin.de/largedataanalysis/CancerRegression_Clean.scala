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

//import org.apache.flink.api.java.DataSet
//import org.apache.flink.api.scala.DataSet

import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.ml.MLUtils
import org.apache.flink.ml.common.LabeledVector
import org.apache.flink.ml.math.DenseVector
import org.apache.flink.ml.regression.MultipleLinearRegression

import scala.io.Source

object CancerRegression_Clean {

  def main(args: Array[String]) {

    val text = Source.fromFile("/home/mi/nwulkow/ADL/Data/sampledata.txt").getLines().toList
    val lines = text

    //Find the index of a parameter: // Diese Methode findet den Index eines Parameters im Text-File, also die Stelle, an der er da aufgelistet ist
    // -------------
    def getParameterIndex(parametername: String, topline: Array[String]): Double = {
      //val parametername = "bcr_patient_barcode"
      var parameterIndex: Double = 0
      for (a <- Range(0, topline.length)) {
        if (topline(a).equals(parametername)) {
          //println("pname="+parametername)
          parameterIndex = a
        }
      }
      parameterIndex
    }
    //---------------


    val params: Array[Double] = new Array[Double](3) // params ist ein Array, das die Stellen gewählter Parameter im Datensatz enthält
    val lvs: Array[LabeledVector] = new Array[LabeledVector](lines.length)
    // Die drei Parameter sind: Alter, Rauchgewohntheit, gesunde Ernährung
    params(0) = getParameterIndex("age", lines(0).split("\t"))
    params(1) = getParameterIndex("tobacco_smoking_history_indicator", lines(0).split("\t"))
    params(2) = getParameterIndex("healthynutrition", lines(0).split("\t"))

    val cancerstateIndex = getParameterIndex("person_neoplasm_cancer_status", lines(0).split("\t")) // Der Index des CancerState



    val env = ExecutionEnvironment.getExecutionEnvironment



    //var lvset: DataSet[LabeledVector] = null
    var i: Int = 0
 // Es wird nun ein Array von LabeledVectors erstellt. Dazu werden die entsprechenden Werte der Parameter und des CancerState aus den Daten herausgelesen
    for (i <- Range(1, lines.length)) {

      var currentline = lines(i)
      val splittedline = currentline.split("\t")

      //Parameterwerte finden
      val cancerstate = splittedline(cancerstateIndex.toInt).toDouble
      val age = splittedline(params(0).toInt).toDouble
      val smoke = splittedline(params(1).toInt).toDouble
      val loc = splittedline(params(2).toInt).toDouble

      val vector = DenseVector(age, smoke, loc)
      val lv = LabeledVector(cancerstate, vector)
      // Den Vector zum Array der LVs hinzufügen
      lvs(i - 1) = lv
      //i = i + 1
    }


  // Das Array(LabeledVector) muss nun in ein DataSet[LabeledVector] umgewandelt werden. Hierzu verwende ich folgende hölzerne Methode
    // Das Array wird in ein Text-File geschrieben und zwar in der Art und Weise, sodass SVM die Daten wieder einlesen kann
    //-------------------
    import java.io._
    val outputpath : String = "/home/mi/nwulkow/ADL/Data/outputwrite1"
    val pw = new PrintWriter(new File(outputpath))

    for (i <- Range(0, lvs.length-1)) {
      //println(lvs(i).label)
      pw.write(lvs(i).label.toInt + " ")
      for (j <- Range(1,lvs(i).vector.size)){
        pw.write(j + ":" + lvs(i).vector(j) + " ")
      }
      pw.write("\n")

    }
    pw.close


    import org.apache.flink.api.scala.DataSet
  // Die Daten werden nun mithilfe von als DataSet[LabeledVector] eingelesen
    val lvdataset: DataSet[LabeledVector] = MLUtils.readLibSVM(env ,outputpath)
    //------------------------

  // Regression
    val mlr = MultipleLinearRegression()
      .setStepsize(1.0)
      .setIterations(100)
      .setConvergenceThreshold(0.001)
    mlr.fit(lvdataset)
    val weights = mlr.weightsOption
    println(weights.get)


  }
}
