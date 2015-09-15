
package de.fuberlin.de.largedataanalysis


import java.io.{File, FileWriter}

import scala.util.Try


object Tools {



  def writeMatrixToCSV(matrix: Array[Array[Double]], pwmatrix: FileWriter): Unit = {

    for (i <- Range(0, matrix.length)) {
      for (j <- Range(0, matrix(0).length)) {

        pwmatrix.write(i.toString + "," +  j.toString + "," + matrix(i)(j))
        pwmatrix.write("\n")
      }
    }
  }

  def printMatrix(matrix: Array[Array[Double]]){
    for (i <- Range(0,matrix.length)) {
      for (j <- Range(0, matrix(0).length)) {

        print(matrix(i)(j) + "  ")
      }
      println()
    }
  }

  def writeGenesWithWeights(genesWithWeights: List[(String,Double)], filename: String) {

    val writer = new FileWriter(new File(filename))
    for (gww <- genesWithWeights) {
      writer.write(gww._2.toString + " , " + gww._1)
      writer.write("\n")
    }
    writer.close()
  }


  def getListOfFiles(dir: String):List[File] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory) {
      d.listFiles.filter(_.isFile).toList
    } else {
      List[File]()
    }
  }

  def countFiles_Filter(dir: String): Int = {
    val files = getListOfFiles(dir)
    var counter = 0
    for ( f <- files){
      if(f.getName.contains("mirna.qua") || f.getName.contains("rsem.genes.no")){
        counter = counter + 1
      }
    }
    return counter
  }


  def mergeCountLists(counts: List[Double], genes: List[String], allGenes: List[String]): Array[Double] = {

    val extended_testperson_counts = new Array[Double](allGenes.length)

    for (j <- Range(0, allGenes.length)) {
      if (genes.contains(allGenes(j))) {
        val index = genes.indexOf(allGenes(j))
        extended_testperson_counts(j) = counts(index)
      }
      else {
        extended_testperson_counts(j) = 0
      }
    }

    return extended_testperson_counts
  }

  def readDoubleFromSentence(line: String,seperator: String): Double = {

    val words = line.split(seperator)
    val nums = words.map(c => Try(c.toDouble))
    var returnnumber = 0d
    for (l <- nums){
      if (l.isSuccess){
        returnnumber =  l.get
        return returnnumber
      }
    }
    return returnnumber
  }


}
