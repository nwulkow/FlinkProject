
package de.fuberlin.de.largedataanalysis


import java.io.{File, FileWriter}


object Tools {



  def writeMatrixToCSV(matrix: Array[Array[Double]], pwmatrix: FileWriter): Unit = {

    for (i <- Range(0, matrix.length)) {
      for (j <- Range(0, matrix(0).length)) {

        pwmatrix.write(i.toString + "," +  j.toString + "," + matrix(i)(j))
        pwmatrix.write("\n")
      }
    }
  }


  def getListOfFiles(dir: String):List[File] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory) {
      d.listFiles.filter(_.isFile).toList
    } else {
      List[File]()
    }
  }


  def mergeCountLists(counts: List[Double], genes: List[String], allGenes: List[String]): Array[Double] ={

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




}
