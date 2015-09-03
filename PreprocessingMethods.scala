
package de.fuberlin.de.largedataanalysis

import java.io.{File, FileWriter}

import org.apache.flink.api.scala.{ExecutionEnvironment, DataSet}
import org.apache.flink.ml.common.LabeledVector
import org.apache.flink.ml.math.DenseVector

import scala.io.Source

object PreprocessingMethods {


  val env = ExecutionEnvironment.getExecutionEnvironment



  def preprocessdata(path: String, label: Int, pw: FileWriter, matrixaslist_copy: List[List[Double]], excludesGenes: Array[String]):List[List[Double]]=  {

    var matrixaslist = matrixaslist_copy
    val files = Tools.getListOfFiles(path)
    for (k <- Range(0, files.length)) {


      // Aktuelles File
      val path_short = path + "/" + files(k).getName

      val array = readOnePersonsData(path_short, excludesGenes)._1


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
    return matrixaslist
  }



  def preprocessdataFromMatrix(matrix: Array[Array[Double]], numberHealthy: Int, pw: FileWriter, matrixaslist_copy: List[List[Double]]): List[List[Double]] = {

    var label = 1
    var matrixaslist = matrixaslist_copy
    for (k <- Range(0, matrix.length)) {

      if( k >= numberHealthy) label = -1


      val array = matrix(k)

      //val dv = DenseVector(array.toArray)

      //val lv = LabeledVector(label, dv)
      // geneMatrix füllen
      matrixaslist = array.toList :: matrixaslist
      pw.write(label.toInt + " ")
      for (j <- Range(0, matrix(k).size)) {
        pw.write((j + 1) + ":" + matrix(k)(j) + " ") //lv.vector(j) + " ")
      }
      pw.write("\n")

    }
    pw.close()
    return matrixaslist
  }


  def readOnePersonsData(path: String, excludesGenes: Array[String], maxGenes: Int = 10000000): (List[Double],List[String]) = {

    val text = Source.fromFile(path).getLines().toList
    val firstline = text(0)
    val columns = firstline.split("\t")

    val nameindex = 0
    var countindex = 1
    for (i <- Range(0, columns.length)) {
      if (columns(i) == "reads_per_million_miRNA_mapped" || columns(i).contains("count"))
        countindex = i
    }

    var data = Pipeline.readmiRNA(env, path, Array(nameindex, countindex))
    data = data.filter{ c => c.ID.contains("?") == false}
    data = data.filter{c =>  c.count.contains("count") == false}
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
      //if (tuples(j).count.contains("reads") == false && tuples(j).count.contains("count") == false) {
        genelist = tuples(j).ID :: genelist
        countslist = tuples(j).count.toDouble :: countslist
     // }
      j = j + 1
    }

    return (countslist , genelist)
  }




  def matrixCreation(path_list: Array[String], pwmatrix: FileWriter, excludesGenes: Array[String], maxGenes: Int = 10000000): (Array[Array[Double]],List[String], Array[Double]) = {

    var allCounts = List[List[Double]]()
    var allGenes = List[List[String]]()
    var uniqueGenes = List[String]()
    var labels = List[Double]()

    var numberOfFiles = 0
    for (path <- path_list) {

      val files = Tools.getListOfFiles(path)

      for (i <- Range(0, files.length)) {
        val path_short = path + "/" + files(i).getName
        val temp_lists = readOnePersonsData(path_short, excludesGenes)
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

      val files = Tools.getListOfFiles(path)
      if (label == 1) {
        for (j <- Range(0, allstrings.length)) {
          matrix(rownumber)(j) = allCounts(rownumber)(j)
          //pwmatrix.write((rownumber + 1).toString + "," + (j + 1).toString + "," + matrix(rownumber)(j))
          //pwmatrix.write("\n")
        }
        rownumber += 1
      }

      for (k <- Range(1, files.length + 1)) {

        if (k == files.length && label == -1) {

        }
        else {
          allstrings = allstrings ::: allGenes(rownumber) distinct;
          val currentgenes = allGenes(rownumber)
          val currentcounts = allCounts(rownumber)

          for (j <- Range(0, allstrings.length)) {
            if (currentgenes.contains(allstrings(j))) {
              val index = currentgenes.indexOf(allstrings(j))
              matrix(rownumber)(j) = currentcounts(index)
              //pwmatrix.write((rownumber + 1).toString + "," + (j + 1).toString + "," + matrix(rownumber)(j))
              //pwmatrix.write("\n")
            }
            else {
              matrix(rownumber)(j) = 0
            }
          }
          rownumber += 1
          labels = label :: labels
        }
      }
      label = -1
    }


    val matrix_slice = Array.ofDim[Double](matrix.length, Math.min(maxGenes,matrix(0).length))
      for (i <- Range(0, matrix.length)) {
        matrix_slice(i) = matrix(i).slice(0, Math.min(maxGenes,matrix(0).length))
      }

    for (l <- Range(0, matrix.length)) {
      for (k <- Range(0, Math.min(maxGenes, allstrings.length))) {
        pwmatrix.write((l + 1).toString + "," + (k + 1).toString + "," + matrix(l)(k))
        pwmatrix.write("\n")
      }
    }
    pwmatrix.close()

    return (matrix_slice, allstrings.slice(0,Math.min(maxGenes,allstrings.length)), labels.toArray)
  }





}
