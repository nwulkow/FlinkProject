
package de.fuberlin.de.largedataanalysis


import java.io.{File, FileWriter}

import org.apache.flink.api.scala.{DataSet, ExecutionEnvironment}


object StatisticsMethods {


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
