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


import java.io.{FileWriter, File}

import org.apache.commons.io.FileUtils
import org.apache.commons.math.linear.{MatrixUtils, BigMatrix}
import org.apache.flink.api.scala.{DataSet, ExecutionEnvironment}
import org.apache.flink.api.scala._
import org.apache.flink.ml.MLUtils
import org.apache.flink.ml.classification.SVM
import org.apache.flink.ml.common.LabeledVector
import org.apache.flink.ml.math.DenseVector
import scala.io.Source


object Pipeline {

  def main(args: Array[String]) {


    val env = ExecutionEnvironment.getExecutionEnvironment
    // PFAD zu den Dateien, (also nicht zu einem einzelnen File!)
    val path_healthy = "/home/mi/nwulkow/ADL/Projekt/Data/miRNASeq/BCGSC__IlluminaHiSeq_miRNASeq/Level_3/Healthy"
    val path_diseased = "/home/mi/nwulkow/ADL/Projekt/Data/miRNASeq/BCGSC__IlluminaHiSeq_miRNASeq/Level_3/Diseased"
    // List der Files
    //val files_healthy = getListOfFiles(path_healthy)
    //val files_diseased = getListOfFiles(path_diseased)

    import java.io._
    val outputPath_SVM: String = "/home/mi/nwulkow/ADL/Projekt/Data/miRNASeq/BCGSC__IlluminaHiSeq_miRNASeq/Level_3/Output/vectors"
    val outputPath_Matrix: String = "/home/mi/nwulkow/ADL/Projekt/Data/miRNASeq/BCGSC__IlluminaHiSeq_miRNASeq/Level_3/Output/matrix"
    val nodes_file_path = "/home/mi/nwulkow/ADL/Projekt/Data/miRNASeq/BCGSC__IlluminaHiSeq_miRNASeq/Level_3/Output/PRBNodes"
    val pw = new FileWriter(new File(outputPath_SVM), true)
    val pw_matrix = new FileWriter(new File(outputPath_Matrix), true)
    val pw_nodes_file = new FileWriter(new File(nodes_file_path), true)

    var genelist = List[String]()
    var countslist_test = List[Double]()


    // Matrix mit den Werten. Reihen sind die verschiedenen Pesonene, Spalten die Genecounts
    var matrixaslist = List[List[Double]]()


    preprocessdata(path_healthy,1)
    preprocessdata(path_diseased,-1)




    def preprocessdata(path: String, label: Int){


      val  files = getListOfFiles(path)
      for (k <- Range(0, files.length)) {


      // Aktuelles File
      val path_short = path + "/" + files(k).getName


      // Read the text and consider the first line. Find out which columns represent the Gene count
      // Necessary because the files are structured differently
      val text = Source.fromFile(path_short).getLines().toList
      val firstline = text(0)
      val columns = firstline.split("\t")

      val nameindex = 0
      var countindex = 1
      for (i <- Range(0, columns.length)) {
        if (columns(i) == "reads_per_million_miRNA_mapped")
          countindex = i
      }

      val data = readmiRNA(env, path_short, Array(nameindex, countindex))
      //println(data.count())


      // Die Gene counts in eine List umwandeln
      //val outputPath_Counts = "/home/mi/nwulkow/ADL/Projekt/Data/miRNASeq/BCGSC__IlluminaHiSeq_miRNASeq/Level_3/Output/counts"
        var CountsDataSet = data.map(c => c.count)
        CountsDataSet = CountsDataSet.filter { line => line.contains("reads") == false }
        //countslist_test =  CountsDataSet.map(c => c.toDouble).collect().toList

        val counts = CountsDataSet.collect()
        val countslist = counts.toList

        var IDsDataSet = data.map(c => c.ID).filter{line => line.contains("ID") == false}
        val ids = IDsDataSet.collect()
        genelist = ids.toList


      //----------------
        val array: Array[Double] = (countslist map (_.toDouble)).toArray
        val dv = DenseVector(array)

        var lv = LabeledVector(label, dv)

        // geneMatrix füllen
       matrixaslist =  array.toList :: matrixaslist

        pw.write(lv.label.toInt + " ")
        for (j <- Range(0, lv.vector.size)) {
          pw.write((j + 1) + ":" + lv.vector(j) + " ")
        }
        pw.write("\n")

    }



  }

// Korrelationswerte berechnen und schreiben
    var meansList = List[Double]()
    var varianceList = List[Double]()
    val noGenes = matrixaslist(0).length
    matrixaslist = matrixaslist.transpose
    val noPeople = matrixaslist(0).length

    var network = List[Array[Double]]() // Netzwerk wird gespeichert um später ein GDF-File zu erzeugen


    for (j <- Range(0, noGenes)) {
      meansList = meanOfArray(matrixaslist(j)) :: meansList
      varianceList = variance(matrixaslist(j)) :: varianceList
    }
    val threshold = 0.8
    for (j <- Range(0, noGenes)) {
      pw_nodes_file.write(j.toString)
      pw_nodes_file.write("\n")

      for (h <- Range(j, noGenes)) {
        if(threshold < Math.abs(correlationcoefficient(matrixaslist(j), matrixaslist(h))) ){
          pw_matrix.write(j.toString + " " + h.toString)
          pw_matrix.write("\n")
          network = Array[Double](j,h) :: network
        }
      }
    }

    val pageranks = PageRankBasicForPipeline.executePRB(nodes_file_path, outputPath_Matrix, "", matrixaslist(0).length)
    //println(pageranks.collect())

    // Ein GDF-File schreiben mit dem Netzwerk und den PageRankBasic-Werten
    val gdf_path = "/home/mi/nwulkow/ADL/Projekt/Data/miRNASeq/BCGSC__IlluminaHiSeq_miRNASeq/Level_3/Output/gdfFile.gdf"
    writeGDFFile(network, pageranks,gdf_path)

    // FileWriter schließen
    pw.close()
    pw_matrix.close()
    pw_nodes_file.close()


    // SVM
    val svmData: DataSet[LabeledVector] = MLUtils.readLibSVM(env ,outputPath_SVM)

    val svm = SVM()
      .setBlocks(env.getParallelism)
      .setIterations(100)
      .setRegularization(0.001)
      .setStepsize(0.1)
      .setSeed(42)

    svm.fit(svmData)
    val f = svm.weightsOption
    //println(f.get.collect())
    val weights = f.get.collect()
    //println(weights.toList.sortWith(_.valueAt(0) < _.valueAt(0)))
    val weightslist = weights.toList


    var product = (weightslist.map(c => c), countslist_test.map(c => c.toDouble)).zipped.map((c1, c2) => c1 * c2).toArray
    println(product)

    //println( product.toList)
    // Skalarprodukt von zwei Listen
    /*val testlist = List(1,2,3,4,5)
    val testlist2 = List(5,6,7,8,9)
    val product = (testlist2, testlist).zipped.map((c1,c2) => c1*c2)
    print(product.sum)*/


    //env.execute("Pipeline")






  }



  case class GeneData(ID: String, count: String)



  private def readmiRNA(env: ExecutionEnvironment, path: String, columns: Array[Int]): DataSet[GeneData] = {
    env.readCsvFile[GeneData](
      path,
      fieldDelimiter = "\t",
    includedFields = columns)
  }


  def getListOfFiles(dir: String):List[File] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory) {
      d.listFiles.filter(_.isFile).toList
    } else {
      List[File]()
    }
  }

  //--------------------------
  // Statistik Mathoden:

  def meansOfGeneCounts(list: List[List[Double]]): List[Double] = {

    var means = List[Double]()
    for (j <- Range(0, list.length)) {
      means = meanOfArray(list(j)) :: means

    }
    return means

  }


  def meanOfArray(array: List[Double]): Double = {

    var sum = 0d
    for (j <- Range(0, array.length)) {
      sum = sum + array(j)
    }
    return (sum / array.length)


  }


  def correlationvalue(array1: List[Double], array2: List[Double]): Double = {

    val mean1 = meanOfArray(array1)
    val mean2 = meanOfArray(array2)

    var sum = 0d
    for (j <- Range(0, array1.length)) {
      sum = sum + (array1(j) - mean1) * (array2(j) - mean2)
    }

    return sum
  }

  def correlationvalue(array1: List[Double], array2: List[Double], mean1: Double, mean2: Double): Double = {

    var sum = 0d
    for (j <- Range(0, array1.length)) {
      sum = sum + (array1(j) - mean1) * (array2(j) - mean2)
    }

    return sum
  }



  def variance(array: List[Double]): Double = {

    var sum = 0d
    val mean = meanOfArray(array)
    for (j <- Range(0, array.length)) {
      sum = sum + Math.pow((array(j)-mean),2)
    }
    return Math.pow(sum, 0.5)

  }


  def correlationcoefficient(array1: List[Double], array2: List[Double]): Double = {

    val correlation = correlationvalue(array1,array2)
    val var1 = variance(array1)
    val var2 = variance(array2)
    return correlation / (var1 * var2)
  }

  def correlationcoefficient(array1: List[Double], array2: List[Double], var1: Double, var2: Double): Double = {

    val correlation = correlationvalue(array1,array2)
    return correlation / (var1 * var2)
  }



  def writeGDFFile(network: List[Array[Double]], ranks: DataSet[Double], gdfpath: String) {

    val gdf_file = new FileWriter(new File(gdfpath), true)

    gdf_file.write("nodedef>name VARCHAR, rank DOUBLE")
    gdf_file.write("\n")
    val rankscollect = ranks.collect()
    for (j <- Range(0, rankscollect.length)) {
      gdf_file.write(j.toString + "," +  rankscollect(j).toString)
      gdf_file.write("\n")
    }
    gdf_file.write("edgedef>node1 VARCHAR, node2 VARCHAR")
    gdf_file.write("\n")
    for (i <- Range(0, network.length)) {
      val current = network(i)
      gdf_file.write(current(0).toInt + "," +  current(1).toInt)
      gdf_file.write("\n")
    }

    gdf_file.close()

  }

}
