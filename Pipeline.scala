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


    // Pfade und FileWriter für einige temporäre Dateien:
    // SVM-File, Netzwerkmatrix,Knoten des Netzwerkes, unvollständige Matrix,
    import java.io._
    val outputPath_SVM: String = "/home/mi/nwulkow/ADL/Projekt/Data/miRNASeq/BCGSC__IlluminaHiSeq_miRNASeq/Level_3/Output/svmvectors"
    val outputPath_Matrix: String = "/home/mi/nwulkow/ADL/Projekt/Data/miRNASeq/BCGSC__IlluminaHiSeq_miRNASeq/Level_3/Output/matrix"
    val nodes_file_path = "/home/mi/nwulkow/ADL/Projekt/Data/miRNASeq/BCGSC__IlluminaHiSeq_miRNASeq/Level_3/Output/PRBNodes"
    val incompletematrix_path = "/home/mi/nwulkow/ADL/Projekt/Data/miRNASeq/BCGSC__IlluminaHiSeq_miRNASeq/Level_3/Output/matrix_tocomplete"

    val pw = new FileWriter(new File(outputPath_SVM), true)
    val pw_matrix = new FileWriter(new File(outputPath_Matrix), true)
    val pw_nodes_file = new FileWriter(new File(nodes_file_path), true)
    val pw_incompletematrix = new FileWriter(new File(incompletematrix_path), true)



    // Matrix mit den Werten. Reihen sind die verschiedenen Personen, Spalten die Genecounts
    var matrixaslist = List[List[Double]]()

    val testpath = "/home/mi/nwulkow/ADL/Projekt/Data/miRNASeq/BCGSC__IlluminaHiSeq_miRNASeq/Level_3/Test"
    val incompletematrix =  matrixCreation(testpath)
    writeMatrixToCSV(incompletematrix, pw_incompletematrix)
    //
    // Hier die Matrix Completion mit ALS Join. Inputratings = incompletematrix_path
    // resultmatrix = ALSJoin(...)
    //
    preprocessdata(path_healthy,1)
    preprocessdata(path_diseased,-1)

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
    val weights = f.get.collect()
    val weightslist = weights.toList

    // Testen des classifiers
    val testperson_path = "/home/mi/nwulkow/ADL/Projekt/Data/miRNASeq/BCGSC__IlluminaHiSeq_miRNASeq/Level_3/Healthy/TCGA-E2-A1BC-01A-11R-A12O-13.mirna.quantification.txt"
    val testperson_counts: List[Double] = readOnePersonsData(testperson_path)._1

    val product = (weightslist(0).toArray, testperson_counts).zipped.map((c1, c2) => c1 * c2).toArray
    println(product.sum)


    def preprocessdata(path: String, label: Int) {


      val files = getListOfFiles(path)
      for (k <- Range(0, files.length)) {


        // Aktuelles File
        val path_short = path + "/" + files(k).getName

        val array = readOnePersonsData(path_short)._1


        //----------------
        val dv = DenseVector(array.toArray)

        val lv = LabeledVector(label, dv)

        // geneMatrix füllen
        matrixaslist = array.toList :: matrixaslist

        pw.write(lv.label.toInt + " ")
        for (j <- Range(0, lv.vector.size)) {
          pw.write((j + 1) + ":" + lv.vector(j) + " ")
        }
        pw.write("\n")

      }
    }

    def preprocessdataFromMatrix(matrix: Array[Array[Double]], label: Int) {


      for (k <- Range(0, matrix.length)) {


        val array = matrix(0)


        val dv = DenseVector(array.toArray)

        val lv = LabeledVector(label, dv)

        // geneMatrix füllen
        matrixaslist = array.toList :: matrixaslist

        pw.write(lv.label.toInt + " ")
        for (j <- Range(0, lv.vector.size)) {
          pw.write((j + 1) + ":" + lv.vector(j) + " ")
        }
        pw.write("\n")

      }
    }


     def readOnePersonsData(path: String): (List[Double],List[String]) = {

       val text = Source.fromFile(path).getLines().toList
       val firstline = text(0)
       val columns = firstline.split("\t")

       val nameindex = 0
       var countindex = 1
       for (i <- Range(0, columns.length)) {
         if (columns(i) == "reads_per_million_miRNA_mapped")
           countindex = i
       }

       val data = readmiRNA(env, path, Array(nameindex, countindex))
       val tuples = data.collect()
       var countslist = List[Double]()
       var genelist = List[String]()

       //Ich übertrage hier einzeln die Werte aus dem DataSet in Listen. Der Grund ist, dass der
       // collect-Befehl für DataSets die Daten umordnet, was sehr ärgerlich ist
       var j = 0
       while (j < tuples.length){
         if (tuples(j).count.contains("reads") == false) {
           genelist = tuples(j).ID :: genelist
           countslist = tuples(j).count.toDouble :: countslist
         }
         j = j + 1
       }

       // Die Gene counts in eine List umwandeln
       /*var CountsDataSet = data.map(c => c.count)
       CountsDataSet = CountsDataSet.filter { line => line.contains("reads") == false }
       countslist_test = CountsDataSet.map(c => c.toDouble).collect().toList

       val counts = CountsDataSet.collect()
       //val countslist = counts.toList

       val IDsDataSet = data.map(c => c.ID).filter { line => line.contains("ID") == false }
       val ids = IDsDataSet.collect()
       //genelist = ids.toList*/

      return (countslist , genelist)
     }


    def matrixCreation(path: String): Array[Array[Double]] =  {

      val files = getListOfFiles(path)

      var allCounts = List[List[Double]]()
      var allGenes = List[List[String]]()
      var uniqueGenes = List[String]()


      for (i <- Range(0, files.length)) {

        val path_short = path + "/" + files(i).getName
        val temp_lists = readOnePersonsData(path_short)
        uniqueGenes = uniqueGenes ::: temp_lists._2 distinct;
        allGenes = allGenes :+ temp_lists._2
        allCounts =  allCounts :+ temp_lists._1.toList
      }

      // 'uniqueGenes'enthält alle Gene, die in mindestens einem File vorkommen, genau ein Mal

      var matrix = Array.ofDim[Double](files.length,uniqueGenes.length)

      var allstrings = allGenes(0)


      for (j <- Range(0, allstrings.length)) {
        matrix(0)(j) = allCounts(0)(j)
      }

      for (k <- Range(1, files.length)) {
        allstrings = allstrings ::: allGenes(k) distinct;
        val currentgenes = allGenes(k)
        val currentcounts = allCounts(k)

        for (j <- Range(0, allstrings.length)) {
          if (currentgenes.contains(allstrings(j))) {
            val index = currentgenes.indexOf(allstrings(j))
            matrix(1)(j) = currentcounts(index)
          }
          else {
            matrix(1)(j) = 0
          }
        }
      }

      for (i <- Range(0, matrix.length)) {
        for (j <- Range(0, matrix(0).length)) {

          print(matrix(i)(j) + "  ")
        }
        println()
      }
    println(allstrings)

    return matrix
    }

    def writeMatrixToCSV(matrix: Array[Array[Double]], pwmatrix: FileWriter): Unit = {

      for (i <- Range(0, matrix.length)) {
        for (j <- Range(0, matrix(0).length)) {

          pwmatrix.write(i.toString + "," +  j.toString + "," + matrix(i)(j))
          pwmatrix.write("\n")
        }
      }
    }


// Netzwerk Vorbereitung: -----------------------------
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

    //----------------------------------------------







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
