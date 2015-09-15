package com.github.projectflink.als

import java.io.FileWriter

import com.github.projectflink.common.als.{ALSUtils, Factors, Rating}
import com.github.projectflink.util.FlinkTools
import de.fuberlin.de.largedataanalysis.{Tools, Pipeline}
import org.apache.flink.api.scala._
import org.apache.flink.util.Collector
import org.jblas.{Solve, SimpleBlas, FloatMatrix}


class ALSJoin(factors: Int, lambda: Double, iterations: Int, seed: Long, persistencePath:
Option[String]) extends ALSFlinkAlgorithm with Serializable {

  def factorize(ratings: DS[RatingType]): Factorization = {
    null
  }

  def factorize(ratings: DS[RatingType], ratings2: DS[RatingType],
                ratings3: DS[RatingType], ratings4: DS[RatingType]): Factorization = {

    val transposedRatings = ratings2 map { x => Rating(x.item, x.user, x
      .rating)
    }

    val initialItemMatrix = {
      val itemIDs = ratings.map { x => Tuple1(x.item)} distinct

      val initialItemMatrix = generateRandomMatrix(itemIDs map {
        _._1
      }, factors, seed)

      persistencePath match {
        case Some(path) =>
          FlinkTools.persist(initialItemMatrix, path + "initialItemMatrix")
        case None => (initialItemMatrix)
      }
    }

    val iMatrix = initialItemMatrix.iterate(iterations){
      itemMatrix => {
        val userMatrix = updateMatrix(ratings4, itemMatrix, lambda)
        updateMatrix(transposedRatings, userMatrix, lambda)
      }
    }

    val itemMatrix = persistencePath match {
      case Some(path) =>
        FlinkTools.persist(iMatrix, path + "items")
      case None =>
        iMatrix
    }

    val userMatrix = updateMatrix(ratings3, itemMatrix, lambda)
    Factorization(userMatrix, itemMatrix)
  }

  def updateMatrix(ratings: DataSet[RatingType], items: DataSet[FactorType],
                   lambda: Double): DataSet[FactorType] = {
    val uVA = items.join(ratings).where(0).equalTo(1) {
      (item, ratingEntry) => {
        val Rating(uID, _, rating) = ratingEntry

        (uID, rating, item.factors)
      }
    }

    uVA.groupBy(0).reduceGroup{
      (vectors, col: Collector[FactorType]) => {

        var uID = -1
        val triangleSize = (factors*factors - factors)/2 + factors
        val xtx = FloatMatrix.zeros(triangleSize)

        val vector = FloatMatrix.zeros(factors)
        var n = 0

        for((id, rating, vectorData) <- vectors){
          uID = id

          val v = new FloatMatrix(vectorData)

          SimpleBlas.axpy(rating, v, vector)
          ALSUtils.outerProductInPlace(v, xtx, factors)

          n += 1
        }

        val fullMatrix = FloatMatrix.zeros(factors, factors)

        ALSUtils.generateFullMatrix(xtx, fullMatrix, factors)

        var counter = 0

        while(counter < factors){
          fullMatrix.data(counter*factors + counter) += lambda.asInstanceOf[ElementType] * n
          counter += 1
        }
        col.collect(new Factors(uID, Solve.solvePositive(fullMatrix, vector).data))
      }
    }//.withConstantSet("0")
  }
}

object ALSJoin extends ALSFlinkRunner with ALSFlinkToyRatings {


  def main(args: Array[String]): Unit = {
    //val inputRatings = "/home/mi/nwulkow/ADL/Projekt/Data/miRNASeq/BCGSC__IlluminaHiSeq_miRNASeq/Level_3/Test/testmatrix"
    //val outputPath = "\"/home/mi/nwulkow/ADL/Projekt/Data/miRNASeq/BCGSC__IlluminaHiSeq_miRNASeq/Level_3/Test"
    //doMatrixCompletion(inputRatings, 4, 100, 42, Some("dummy"), outputPath, 3, 3)

    parseCL(args) map {
      config => {
        import config._

        val env = ExecutionEnvironment.getExecutionEnvironment
        val ratings = readRatings(inputRatings, env)
        val ratings2 = readRatings(inputRatings, env)
        val ratings3 = readRatings(inputRatings, env)
        val ratings4 = readRatings(inputRatings, env)

        val als = new ALSJoin(factors, 0.1, iterations, seed, persistencePath)
        val factorization = als.factorize(ratings, ratings2, ratings3, ratings4)

        outputFactorization(factorization, outputPath)

        env.execute("ALS benchmark")


        val output_user_path = outputPath + "/userFactorsFile"
        val output_item_path = outputPath + "/itemFactorsFile"
        var itemmatrix = Array.ofDim[Double](3,factors)
        var usermatrix = Array.ofDim[Double](3,factors)

        for(j <- Range(1,5)) {
          var items = scala.io.Source.fromFile(output_item_path + "/" + j.toString).getLines()
          var users = scala.io.Source.fromFile(output_user_path + "/" + j.toString).getLines()

          for (l1 <- items){
            val l1sep = l1.split(",")
            val row_item = l1sep(0).filterNot("(".toSet).toInt - 1
            for (i <- Range(0, factors)) {
              itemmatrix(row_item)(i) = l1sep(i + 1).filterNot(")".toSet).filterNot("(".toSet).toDouble
            }
          }
          for (u1 <- users){
            val u1sep = u1.split(",")
            val row_user = u1sep(0).filterNot("(".toSet).toInt - 1
            for (i <- Range(0, factors)) {
              usermatrix(row_user)(i) = u1sep(i + 1).filterNot(")".toSet).filterNot("(".toSet).toDouble
            }
          }
        }



        val resultmatrix = mult(usermatrix,itemmatrix)

        Tools.printMatrix(itemmatrix)
        Tools.printMatrix(usermatrix)
        Tools.printMatrix(resultmatrix)


      }
    } getOrElse{
      println("Could not parse command line parameters.")
    }
  }


  def doMatrixCompletion(inputRatings: String, factors: Int, iterations: Int,
                         seed: Long, persistencePath: Option[String], outputPath: String, norows : Int, nocols: Int): Array[Array[Double]] = {



    val env = ExecutionEnvironment.getExecutionEnvironment
    val ratings = readRatings(inputRatings, env)
    val ratings2 = readRatings(inputRatings, env)
    val ratings3 = readRatings(inputRatings, env)
    val ratings4 = readRatings(inputRatings, env)
    val als = new ALSJoin(factors, 0.1, iterations, seed, persistencePath)
    val factorization = als.factorize(ratings, ratings2, ratings3, ratings4)

    outputFactorization(factorization, outputPath)
    env.execute("ALS benchmark")


    val output_user_path = outputPath + "/userFactorsFile"
    val output_item_path = outputPath + "/itemFactorsFile"
    var itemmatrix = Array.ofDim[Double](nocols,factors)
    var usermatrix = Array.ofDim[Double](norows,factors)

    for(j <- Range(1,5)) {
      var items = scala.io.Source.fromFile(output_item_path + "/" + j.toString).getLines()
      var users = scala.io.Source.fromFile(output_user_path + "/" + j.toString).getLines()

      for (l1 <- items){
        val l1sep = l1.split(",")
        val row_item = l1sep(0).filterNot("(".toSet).toInt - 1
        for (i <- Range(0, factors)) {
          itemmatrix(row_item)(i) = l1sep(i + 1).filterNot(")".toSet).filterNot("(".toSet).toDouble
        }
      }
      for (u1 <- users){
        val u1sep = u1.split(",")
        val row_user = u1sep(0).filterNot("(".toSet).toInt - 1
        for (i <- Range(0, factors)) {
          usermatrix(row_user)(i) = u1sep(i + 1).filterNot(")".toSet).filterNot("(".toSet).toDouble
        }
      }
    }



    val resultmatrix = mult(usermatrix,itemmatrix)

    //Tools.printMatrix(itemmatrix)
    //Tools.printMatrix(usermatrix)
    //Tools.printMatrix(resultmatrix)
    val file = "/home/mi/nwulkow/ADL/Projekt/Data/miRNASeq/BCGSC__IlluminaHiSeq_miRNASeq/Level_3/completed"
    //Tools.writeMatrixToCSV(resultmatrix, new FileWriter((file)))
    return resultmatrix

  }






  // Kopiert von http://rosettacode.org/wiki/Matrix_multiplication
  def mult[A](a: Array[Array[Double]], b: Array[Array[Double]]): Array[Array[Double]] = {
    for (row <- a)
      yield for(col <- b)
        yield row zip col map Function.tupled(_*_) reduceLeft (_+_)
  }

}
