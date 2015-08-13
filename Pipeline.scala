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

import scala.reflect.io.Path

import com.github.projectflink.als.ALSJoin
import com.github.projectflink.common.als.{ALSUtils, Factors, Rating}
import com.github.projectflink.util.FlinkTools
import org.apache.flink.api.scala._
import org.apache.flink.util.Collector
import org.jblas.{Solve, SimpleBlas, FloatMatrix}

import org.apache.commons.io.FileUtils
import org.apache.commons.math.linear.{MatrixUtils, BigMatrix}
import org.apache.flink.api.scala.{DataSet, ExecutionEnvironment}
import org.apache.flink.api.scala._
import org.apache.flink.ml.MLUtils
import org.apache.flink.ml.classification.SVM
import org.apache.flink.ml.common.LabeledVector
import org.apache.flink.ml.math.DenseVector
import scala.io.Source
import java.io._

object Pipeline {


  val env = ExecutionEnvironment.getExecutionEnvironment
  var matrixaslist = List[List[Double]]()


  def main(args: Array[String]) {

    if (!parseParameters(args)) {
      return
    }

    val outputpath: Path = Path(path + "/Output")
    outputpath.createDirectory()


    val outputPath_SVM = path + "/Output/svmvectors"
    val pw = new FileWriter(new File(outputPath_SVM))

    // PFAD zu den Dateien, (also nicht zu einem einzelnen File!)
    val path_healthy = path + "/Healthy"
    val path_diseased = path + "/Diseased"


    // Pfade und FileWriter für einige temporäre Dateien:
    // SVM-File, Netzwerkmatrix,Knoten des Netzwerkes, unvollständige Matrix,
    val outputPath_Matrix: String = path + "/Output/matrix"
    val nodes_file_path = path + "/Output/PRBNodes"
    val incompletematrix_path = path + "/Output/matrix_tocomplete"
    val resultlabels_path = path + "/Output/labels"

    val network_matrix_healthy_path = path + "/Output/network_matrix_healthy"
    val network_matrix_diseased_path = path + "/Output/network_matrix_diseased"
    val network_nodes_healthy_path = path + "/Output/network_nodes_healthy"
    val network_nodes_diseased_path = path + "/Output/network_nodes_diseased"
    val network_gdf_healthy = path + "/Output/gdf_healthy"
    val network_gdf_diseased = path + "/Output/gdf_diseased"

    val pw_incompletematrix = new FileWriter(new File(incompletematrix_path))
    val pw_resultlabels = new FileWriter(new File(resultlabels_path))



    // Matrix mit den Werten. Reihen sind die verschiedenen Personen, Spalten die Genecounts
    val numberHealthy = getListOfFiles(path_healthy).length
    val numberDiseased = getListOfFiles(path_diseased).length
    val matrixAndGenes = matrixCreation(Array[String](path_healthy, path_diseased), pw_incompletematrix)
    val incompleteMatrix = matrixAndGenes._1
    val allGenes = matrixAndGenes._2

    //
    // Hier die Matrix Completion mit ALS Join. Inputratings = incompletematrix_path
    // resultmatrix = ALSJoin(...)
    //

    ALSJoin.doMatrixCompletion(incompletematrix_path,10,100,42,Some("dummy string"),path,allGenes.length, allGenes.length)

    preprocessdataFromMatrix(incompleteMatrix, numberHealthy, pw)


    // SVM
    val svmData: DataSet[LabeledVector] = MLUtils.readLibSVM(env, outputPath_SVM)

    val svm = SVM()
      .setBlocks(env.getParallelism)
      .setIterations(100)
      .setRegularization(0.001)
      .setStepsize(0.1)
      .setSeed(42)

    svm.fit(svmData)
    val f = svm.weightsOption
    val weights = f.get.collect()
    val weightsList = weights.toList


    // Testen des classifiers
    // ------------

    val files = getListOfFiles(testFiles_path)
    val scalarproducts = new Array[Double](files.length)
    for (k <- Range(0, files.length)) {

      val current_testperson = testFiles_path + "/" + files(k).getName
      val testperson_data = readOnePersonsData(current_testperson)
      val testperson_counts = testperson_data._1
      val testperson_genes = testperson_data._2

      val extended_testperson_counts = new Array[Double](allGenes.length)

      for (j <- Range(0, allGenes.length)) {
        if (testperson_genes.contains(allGenes(j))) {
          val index = testperson_genes.indexOf(allGenes(j))
          extended_testperson_counts(j) = testperson_counts(index)
        }
        else {
          extended_testperson_counts(j) = 0
        }
      }

      val product = (weightsList(0).toArray, extended_testperson_counts).zipped.map((c1, c2) => c1 * c2).sum
      scalarproducts(k) = product

      var HealthyOrDiseased = "Healthy"
      if (product < 0) HealthyOrDiseased = "Diseased"
      pw_resultlabels.write(HealthyOrDiseased + " : " + files(k).getName + ", " + product)
      pw_resultlabels.write("\n")
    }
    pw_resultlabels.close()
    // --------------
    for (s <- scalarproducts) {
      println("scalarproduct = " + s.toString)
    }


    val numberOfTopGenes = 10
    // Höchste Gewichte
    val sortedweights = weightsList(0).toArray.sorted
    var topPositiveWeights = List[(String, Double)]()
    var topNegativeWeights = List[(String, Double)]()
    for (k <- Range(0, numberOfTopGenes)) {
      var correspondingGeneIndex = weightsList(0).toArray.indexOf(sortedweights(k))
      topNegativeWeights ::=(allGenes(correspondingGeneIndex), sortedweights(k))
      correspondingGeneIndex = weightsList(0).toArray.indexOf(sortedweights(allGenes.length - 1 - k))
      topPositiveWeights ::=(allGenes(correspondingGeneIndex), sortedweights(allGenes.length - 1 - k))
    }
    println(topNegativeWeights.reverse)
    println(topPositiveWeights.reverse)



    // Netzwerk Vorbereitung: -----------------------------
    // Korrelationswerte berechnen und schreiben
    // Für Gesunde:

    var (matrixaslistHealthy, matrixaslistDiseased) = matrixaslist.splitAt(numberHealthy)
    doNetworkAnalysisAndGDFFIle(matrixaslistHealthy, network_matrix_healthy_path, network_nodes_healthy_path, network_gdf_healthy)
    doNetworkAnalysisAndGDFFIle(matrixaslistDiseased, network_matrix_diseased_path, network_nodes_diseased_path, network_gdf_diseased)

    def doNetworkAnalysisAndGDFFIle(matrixaslist: List[List[Double]], path_matrix: String, path_nodes: String, gdf_path: String) {

      var meansList = List[Double]()
      var varianceList = List[Double]()
      val noGenes = matrixaslist(0).length
      var matrixaslistTranspose = matrixaslist.transpose
      val noPeople = matrixaslistTranspose(0).length

      val pw_matrix = new FileWriter(path_matrix)
      val pw_nodes_file = new FileWriter(path_nodes)

    var network = List[Array[Double]]() // Netzwerk wird gespeichert, um später ein GDF-File zu erzeugen


    for (j <- Range(0, noGenes)) {
      meansList = meanOfArray(matrixaslistTranspose(j)) :: meansList
      varianceList = variance(matrixaslistTranspose(j)) :: varianceList
    }
    val threshold = 0.8
    for (j <- Range(0, noGenes)) {
      pw_nodes_file.write(j.toString)
      pw_nodes_file.write("\n")

      for (h <- Range(j, noGenes)) {
        if (threshold < Math.abs(correlationcoefficient(matrixaslistTranspose(j), matrixaslistTranspose(h)))) {
          pw_matrix.write(j.toString + " " + h.toString)
          pw_matrix.write("\n")
          network = Array[Double](j, h) :: network
        }
      }
    }
      // FileWriter schließen
      pw_matrix.close()
      pw_nodes_file.close()
    val pageranks = PageRankBasicForPipeline.executePRB(nodes_file_path, outputPath_Matrix, "", matrixaslistTranspose(0).length)
    val pageranks_list = pageranks.collect().toList
    //println(pageranks.collect())

    // Ein GDF-File schreiben mit dem Netzwerk und den PageRankBasic-Werten
    //val gdf_path = path + "/Output/gdfFile.gdf"
    writeGDFFile(network, pageranks,allGenes, gdf_path)



  }


    //----------------------------------------------



  }



  def preprocessdata(path: String, label: Int, pw: FileWriter) {


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
    pw.close()
  }

  def preprocessdataFromMatrix(matrix: Array[Array[Double]], numberHealthy: Int, pw: FileWriter) {

    var label = 1

    for (k <- Range(0, matrix.length)) {

      if( k > numberHealthy) label = -1


      val array = matrix(k)

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
    pw.close()
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

    var data = readmiRNA(env, path, Array(nameindex, countindex))
    // Bestimmte Gene ausschließen
    for (gene <- excludesGenes) {
      data = data.filter { c => c.ID.equals(gene) == false }
    }
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


  def matrixCreation(path_list: Array[String], pwmatrix: FileWriter): (Array[Array[Double]],List[String], Array[Double]) =  {

    var allCounts = List[List[Double]]()
    var allGenes = List[List[String]]()
    var uniqueGenes = List[String]()
    var labels = List[Double]()

    var numberOfFiles = 0

    for (path <- path_list) {

      val files = getListOfFiles(path)

      for (i <- Range(0, files.length)) {
        val path_short = path + "/" + files(i).getName
        val temp_lists = readOnePersonsData(path_short)
        uniqueGenes = uniqueGenes ::: temp_lists._2 distinct;
        allGenes = allGenes :+ temp_lists._2
        allCounts = allCounts :+ temp_lists._1.toList
        numberOfFiles += 1
      }
    }
      // 'uniqueGenes'enthält alle Gene, die in mindestens einem File vorkommen, genau ein Mal
    val matrix = Array.ofDim[Double](numberOfFiles, uniqueGenes.length)
    var allstrings = allGenes(0)

    var label = 1
    var rownumber = 0

    for (path <- path_list) {

      val files = getListOfFiles(path)

      if ( label == 1) {
        for (j <- Range(0, allstrings.length)) {
          matrix(rownumber)(j) = allCounts(rownumber)(j)
          pwmatrix.write((rownumber + 1).toString + "," + (j + 1).toString + "," + matrix(rownumber)(j))
          pwmatrix.write("\n")
        }
        rownumber += 1
      }

      for (k <- Range(1, files.length)) {
        allstrings = allstrings ::: allGenes(rownumber) distinct;
        val currentgenes = allGenes(rownumber)
        val currentcounts = allCounts(rownumber)

        for (j <- Range(0, allstrings.length)) {
          if (currentgenes.contains(allstrings(j))) {
            val index = currentgenes.indexOf(allstrings(j))
            matrix(rownumber)(j) = currentcounts(index)

            pwmatrix.write((rownumber+1).toString + "," +  (j+1).toString + "," + matrix(rownumber)(j))
            pwmatrix.write("\n")
          }
          else {
            matrix(rownumber)(j) = 0
          }
        }
        rownumber += 1
        labels = label :: labels
      }
      label = -1
    }

    pwmatrix.close()

    return (matrix, allstrings, labels.toArray)
  }


  def writeMatrixToCSV(matrix: Array[Array[Double]], pwmatrix: FileWriter): Unit = {

    for (i <- Range(0, matrix.length)) {
      for (j <- Range(0, matrix(0).length)) {

        pwmatrix.write(i.toString + "," +  j.toString + "," + matrix(i)(j))
        pwmatrix.write("\n")
      }
    }
  }

// ------------------------------

  private var path = ""
  private var excludesGenes: Array[String] = null
  private var testFiles_path = ""
  private def parseParameters(args: Array[String]): Boolean = {
    if (args.length > 0) {
      path = args(0)
      excludesGenes = args(1).split(",")
      testFiles_path = args(2)
      true
    } else {

      false
    }
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



  def writeGDFFile(network: List[Array[Double]], ranks: DataSet[Double],allGenes: List[String], gdfpath: String) {

    val gdf_file = new FileWriter(new File(gdfpath), true)
    println("bin jetzt hier")
    gdf_file.write("nodedef>name VARCHAR, rank DOUBLE, label VARCHAR")
    gdf_file.write("\n")
    val rankscollect = ranks.collect()
    for (j <- Range(0, rankscollect.length)) {
      gdf_file.write(j.toString + "," +  rankscollect(j).toString + "," + allGenes(j))
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
