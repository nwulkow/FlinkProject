
package de.fuberlin.de.largedataanalysis


import java.io.{File, FileWriter}
import PageRankBasicForPipeline.Page
import org.apache.flink.api.scala.{DataSet, ExecutionEnvironment}
import org.apache.flink.graph.Vertex


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



  def writeGDFFile_Pagerank(network: List[Array[Double]], ranks: DataSet[Page],allGenes: List[String], gdfpath: String) {

    val gdf_file = new FileWriter(new File(gdfpath))
    gdf_file.write("nodedef>name VARCHAR, rank DOUBLE, label VARCHAR")
    gdf_file.write("\n")
    val rankscollect = ranks.collect()

    for (page <- rankscollect) {
      gdf_file.write(page.pageId + "," +  page.rank + "," + allGenes(page.pageId.toInt - 1))
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

  def writeGDFFile_Cluster(network: List[Array[Double]], clusters: DataSet[Vertex[Long, Long]], allGenes: List[String], gdfpath: String) {

    val gdf_file = new FileWriter(new File(gdfpath))
    gdf_file.write("nodedef>name VARCHAR, cluster DOUBLE, label VARCHAR")
    gdf_file.write("\n")
    val clusterscollect = clusters.collect()

    for (node <- clusterscollect) {
      gdf_file.write(node.getId + "," +  node.getValue + "," + allGenes(node.getId.toInt - 1))
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
