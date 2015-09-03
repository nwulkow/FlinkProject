package de.fuberlin.de.largedataanalysis

import java.io.{PrintWriter, File, FileWriter}

import breeze.linalg.{DenseMatrix, DenseVector}
import org.apache.flink.api.scala.{DataSet, ExecutionEnvironment, _}
import PageRankBasicForPipeline.Page
import org.apache.flink.graph.Vertex
import org.apache.flink.ml.MLUtils
import org.apache.flink.ml.classification.SVM
import org.apache.flink.ml.common.{ParameterMap, WeightVector, LabeledVector}
import com.github.projectflink.als.ALSJoin
import org.apache.flink.ml.math.SparseVector
import org.apache.flink.ml.regression.MultipleLinearRegression

import scala.reflect.io.Path


import scala.util.Random

object Pipeline {


  val env = ExecutionEnvironment.getExecutionEnvironment
  var matrixaslist = List[List[Double]]()

  var matrixBool = false
  var MC_iterations: Int = 100
  var MC_factors: Int = 10
  var SVM_iterations: Int = 100
  var SVM_stepsize: Double = 0.01
  var SVM_regularization: Double = 0.01
  var threshold: Double = 0.8
  var noSelectedGenes: Int = 500
  var corrMatrixCompletionBool = false
  var trainingType : String = "SVM"
  var maxGenes = 1050

  def main(args: Array[String]) {

    // -------------------------------------------------------
    // Parameter einlesen
    if (!parseParameters(args)) {
      return
    }
    // -------------------------------------------------------

    applyAdditionalInput(additional_input)



    // -------------------------------------------------------


    //Pfade für temporäre Dateien
    val outputpath: Path = Path(path + "/Output")
    outputpath.createDirectory()
    val temp_outputpath: Path = Path(outputpath + "/Temp")
    temp_outputpath.createDirectory()
    val final_outputpath : Path = Path(outputpath + "/Final")
    final_outputpath.createDirectory()

    val outputPath_SVM = temp_outputpath + "/svmvectors"
    val pw = new FileWriter(new File(outputPath_SVM))

    // PFAD zu den Dateien, (also nicht zu einem einzelnen File!)
    val path_healthy = path + "/Healthy"
    val path_diseased = path + "/Diseased"


    // Pfade und FileWriter für einige temporäre Dateien:
    // SVM-File, Netzwerkmatrix,Knoten des Netzwerkes, unvollständige Matrix,
    //val outputPath_Matrix: String = path + "/Output/matrix"
    //val nodes_file_path = path + "/Output/PRBNodes"
    val incompletematrix_path = temp_outputpath + "/matrix_tocomplete"
    val resultlabels_path = final_outputpath + "/labels"
    val genesWithWeights_path = final_outputpath + "/classifier"

    val network_matrix_healthy_path = temp_outputpath + "/network_matrix_healthy"
    val network_matrix_diseased_path = temp_outputpath + "/network_matrix_diseased"
    val network_nodes_healthy_path = temp_outputpath + "/network_nodes_healthy"
    val network_nodes_diseased_path = temp_outputpath + "/network_nodes_diseased"
    val network_gdf_healthy = final_outputpath + "/gdf_pageranks_healthy"
    val network_gdf_diseased = final_outputpath + "/gdf_pageranks_diseased"

    val cluster_file_path = temp_outputpath + "/cluster"
    val cluster_gdf_healthy = final_outputpath + "/gdf_clusters_healthy"
    val cluster_gdf_diseased = final_outputpath + "/gdf_clusters_diseased"

    val ranks_diffs_path = final_outputpath + "/rank_differences"

    val pw_incompletematrix = new FileWriter(new File(incompletematrix_path))
    val pw_resultlabels = new FileWriter(new File(resultlabels_path))

    val corrMatrix_path_healthy = temp_outputpath + "/corrMatrix_healthy"
    val corrMatrix_path_diseased = temp_outputpath + "/corrMatrix_diseased"

    // -------------------------------------------------------
    // Vorbereitung der Matrix Completion. Reihen sind die verschiedenen Personen, Spalten die Genecounts
    val numberHealthy = Tools.getListOfFiles(path_healthy).length
    val numberDiseased = Tools.getListOfFiles(path_diseased).length
    println("-------------- Reading In The Data ----------------")
    val matrixAndGenes = PreprocessingMethods.matrixCreation(Array[String](path_healthy, path_diseased), pw_incompletematrix, excludesGenes, maxGenes)
    var incompleteMatrix = matrixAndGenes._1
    val allGenes = matrixAndGenes._2
    val numberPeople = numberHealthy + numberDiseased
    val numberGenes = allGenes.length
    println("numberGenes = " + numberGenes)
    // allGenes als Array[String]
    val allGenes_string = new Array[String](numberGenes)
    for (i <- Range(0, numberGenes)){
      allGenes_string(i) = allGenes(i)
    }

    // -------------------------------------------------------
    /*val densematrix = DenseMatrix.zeros[Double](numberPeople,numberGenes)
    for (i<- Range(0,numberPeople)) {
      densematrix(i, ::) := DenseVector(incompleteMatrix(i)).t
    }
    val eigs = breeze.linalg.eig(densematrix)
    //println(eigs.eigenvalues + "EIGENVALUES")
    */
    if (matrixBool == true) {
      println("-------------- Doing Matrix Completion On The Data ----------------")
      incompleteMatrix = ALSJoin.doMatrixCompletion(incompletematrix_path, MC_factors, MC_iterations, 42, Some("dummy string"), path, numberPeople, numberGenes)
    }
    // Die Zeilen der vervollständigten Matrix umwandeln in 'LabeledVectors'
    matrixaslist = PreprocessingMethods.preprocessdataFromMatrix(incompleteMatrix, numberHealthy, pw, matrixaslist)

    // -------------------------------------------------------
    // -------------------------------------------------------
    // -------------------------------------------------------


    // SVM
    val svmData: DataSet[LabeledVector] = MLUtils.readLibSVM(env, outputPath_SVM)

    var weightsList = Array[Double]()
    if ( trainingType == "SVM") {
      val svm = SVM()
        .setBlocks(env.getParallelism)
        .setIterations(SVM_iterations)
        .setRegularization(0.001)
        .setStepsize(SVM_stepsize)
        .setSeed(42)

      println("-------------- SVM Fitting ----------------")

      svm.fit(svmData)
      val f = svm.weightsOption
      val weights = f.get.collect()
      weightsList = weights.toList(0).toArray
    }

    else {

      /*  val mlr = MultipleLinearRegression()
          .setStepsize(1.0)
          .setIterations(100)
          .setConvergenceThreshold(0.001)
        mlr.fit(svmData)
        val weightList_collect = mlr.weightsOption.get.collect()
        println("jetzt hier")
        val WeightVector(weightsList, intercept) = weightList_collect(0)
        println("HABE DIE GEWICHTE")

  */
      //val svmData2 = svmData.map(c => c.vector)

      val regression = MultipleLinearRegression()
      // set parameter for the regression
      val parameters = ParameterMap()
        .add(MultipleLinearRegression.Stepsize, 0.05)
        .add(MultipleLinearRegression.Iterations, 50)
      regression.fit(svmData, parameters)

      val weightList_WO = regression.weightsOption
      val WL = weightList_WO.get
      val col: Seq[WeightVector] = WL.collect()
      val WeightVector(weightsList, intercept) = WL.collect()(0)
    }
    // -------------------------------------------------------
    // Testen des classifiers

    println("-------------- Applying The Classifier To The Test Data----------------")

    val files = Tools.getListOfFiles(testFiles_path)
    val scalarproducts = new Array[Double](files.length)
    for (k <- Range(0, files.length)) {

      val current_testperson = testFiles_path + "/" + files(k).getName
      val testperson_data = PreprocessingMethods.readOnePersonsData(current_testperson, excludesGenes)
      val testperson_counts = testperson_data._1
      val testperson_genes = testperson_data._2

      // Vervollständigen, wenn GeneCounts bei einer Testperson fehlen. Hier wird natürlich der Wert 0 angenommen
      val extended_testperson_counts = Tools.mergeCountLists(testperson_counts, testperson_genes, allGenes)

      // Skalarprodukt des classifiers ('weightList(0)') mit den Werten einer Testperson
      val product = (weightsList, extended_testperson_counts).zipped.map((c1, c2) => c1 * c2).sum
      scalarproducts(k) = product

      // Erstelltes Label (Vorzeichen des Skalarproduktes) in eine .txt-File schreiben
      var HealthyOrDiseased = "Healthy"
      if (product < 0) HealthyOrDiseased = "Diseased"
      pw_resultlabels.write(HealthyOrDiseased + " : " + files(k).getName + ", " + product)
      pw_resultlabels.write("\n")
    }
    pw_resultlabels.close()

    for (s <- scalarproducts) {
      println("scalarproduct = " + s.toString)
    }

    // -------------------------------------------------------

    // Die wichtigesten Gene ausgeben
    val numberOfTopGenes = 10
    // Höchste Gewichte
    val sortedweights = weightsList.sorted
    var topPositiveWeights = List[(String, Double)]()
    var topNegativeWeights = List[(String, Double)]()

    var genesWithWeights = List[(String,Double)]()
    val weightsListCopy = weightsList
    for (k <- Range(0,numberGenes)){
      val correspondingGeneIndex = weightsListCopy.indexOf(sortedweights(allGenes.length - 1 - k))
      genesWithWeights ::= (allGenes(correspondingGeneIndex), sortedweights(allGenes.length - 1 - k))
      weightsListCopy(correspondingGeneIndex) = Math.exp(1000) // Sicherstellen, dass kein Gen doppelt in der Liste auftaucht (wenn mehrere Gene das gleiche Gewicht haben)
    }
    Tools.writeGenesWithWeights(genesWithWeights, genesWithWeights_path)

/*    for (k <- Range(0, numberOfTopGenes)) {
      var correspondingGeneIndex = weightsList(0).toArray.indexOf(sortedweights(k))
      topNegativeWeights ::=(allGenes(correspondingGeneIndex), sortedweights(k))
      correspondingGeneIndex = weightsList(0).toArray.indexOf(sortedweights(allGenes.length - 1 - k))
      topPositiveWeights ::=(allGenes(correspondingGeneIndex), sortedweights(allGenes.length - 1 - k))
    }

    println("top negative weights : " + topNegativeWeights.reverse)
    println("top positive weights : " + topPositiveWeights.reverse)
*/


    // -------------------------------------------------------
    // -------------------------------------------------------
    // -------------------------------------------------------


    // Netzwerk Vorbereitung:
    // Korrelationswerte berechnen und schreiben


    def doNetworkAnalysisAndGDFFIle(matrixaslist: List[List[Double]], path_matrix: String, path_nodes: String, gdf_path_ranks: String, gdf_path_cluster: String, corrMatrix_path: String): DataSet[Page] = {


      var meansList = List[Double]()
      var varianceList = List[Double]()
      val noGenes = matrixaslist(0).length
      println("noGenes = " + noGenes)
      var matrixaslistTranspose = matrixaslist.transpose
      val noPeople = matrixaslistTranspose(0).length

      val pw_matrix = new FileWriter(path_matrix)
      val pw_nodes_file = new FileWriter(path_nodes)
      val pw_corrMatrix = new FileWriter(corrMatrix_path)

      var network = List[Array[Double]]() // Netzwerk wird gespeichert, um später ein GDF-File zu erzeugen

      // Netzwerk-Matrix wird erzeugt und in ein .txt-File geschrieben. Das Netzwerk ist ungewichtet, der threshold muss angegeben werden
      for (j <- Range(0, noGenes)) {
        meansList = StatisticsMethods.meanOfArray(matrixaslistTranspose(j)) :: meansList
        varianceList = StatisticsMethods.variance(matrixaslistTranspose(j), meansList(0)) :: varianceList
      }

      var indices = new Array[Int](noGenes)
      for (i <- Range(0, noGenes)) {
        indices(i) = i
      }

      if (corrMatrixCompletionBool == false) noSelectedGenes = noGenes

      val selectedGeneIndices = Random.shuffle(indices.toList).take(noSelectedGenes)

      //var corrMatrix = Array.ofDim[Double](noGenes, noGenes)
     /* var matrixInXY = List[(Int, Int, Double)]()

      for (j <- Range(0, noGenes)) {
        //corrMatrix(j)(j) = 1d
        matrixInXY = (j, j, 1d) :: matrixInXY

        pw_corrMatrix.write(j + "," + j + "," + 1)
        pw_corrMatrix.write("\n")
        for (h <- Range(j, noSelectedGenes)) {
          val corrValue = StatisticsMethods.correlationcoefficient(matrixaslistTranspose(noGenes - 1 - j), matrixaslistTranspose(noGenes - 1 - selectedGeneIndices(h)), varianceList(noGenes - 1 - j), varianceList(noGenes - 1 - selectedGeneIndices(h)))
          //corrMatrix(j)(selectedGeneIndices(h)) = corrValue
          if (corrValue.isInfinity == false && corrValue.isNaN == false) {
            matrixInXY = (j, selectedGeneIndices(h), corrValue) :: matrixInXY
            pw_corrMatrix.write(j + "," + selectedGeneIndices(h) + "," + corrValue)
            pw_corrMatrix.write("\n")
          }
        }
      }
      pw_corrMatrix.close()

      if (corrMatrixCompletionBool == true) {
        matrixInXY = List[(Int, Int, Double)]()
        val corrMatrix = ALSJoin.doMatrixCompletion(corrMatrix_path, 10, 100, 42, Some("dummy string"), path, noGenes, noGenes)
        for (i <- Range(0, noGenes)) {
          for (j <- Range(0, noGenes)) {
            matrixInXY = (i, j, corrMatrix(i)(j)) :: matrixInXY
          }
        }
      }

      for (i <- Range(0, matrixInXY.length)) {
        val current = matrixInXY(i)
        if (current._3 > threshold) {
          pw_matrix.write((current._1 + 1).toString + " " + (current._2 + 1).toString + " " + 1.toString)
          pw_matrix.write("\n")
          pw_matrix.write((current._2 + 1).toString + " " + (current._2 + 1).toString + " " + 1.toString)
          pw_matrix.write("\n")
          network = Array[Double](current._1, current._2) :: network
          network = Array[Double](current._2, current._1) :: network
        }
      }
*/
    /*    for (j <- Range(0, noGenes)) {
          println(" j = " + j)
          pw_nodes_file.write((j + 1).toString + " " + ((j + 1)).toString) //Knoten-Nummer mit Cluster-Nummer (beide gleich)
          pw_nodes_file.write("\n")

          for (h <- Range(j, noGenes)) {
            if (corrMatrix(j)(h) > threshold) {
              pw_matrix.write((j + 1).toString + " " + (h + 1).toString + " " + 1.toString)
              pw_matrix.write("\n")
              pw_matrix.write((h + 1).toString + " " + (j + 1).toString + " " + 1.toString)
              pw_matrix.write("\n")
              network = Array[Double](j, h) :: network
              network = Array[Double](h, j) :: network
            }
          }

        }*/
      var network_string_list = List[String]()
      var network_string = ""
      val t1 = System.currentTimeMillis()
         for (j <- Range(0, noGenes)) {
            println(" j = " + j)
            pw_nodes_file.write((j+1).toString + " " +  ((j+1)).toString) //Knoten-Nummer mit Cluster-Nummer (beide gleich)
            pw_nodes_file.write("\n")
            val j_line = matrixaslistTranspose(noGenes - 1 - j)
            for (h <- Range(j, noSelectedGenes)) {

              if (threshold < Math.abs(StatisticsMethods.correlationcoefficient(j_line, matrixaslistTranspose(noGenes - 1 - h),varianceList(j), varianceList(h)))) {
                pw_matrix.write((j+1).toString + " " + (h+1).toString + " " + 1.toString)
                pw_matrix.write("\n")
                pw_matrix.write((h+1).toString + " " + (j+1).toString + " " + 1.toString)
                pw_matrix.write("\n")
                network = Array[Double](j, h) :: network
                network = Array[Double](h, j) :: network

                network_string_list =  (((j+1).toString + "," + (h+1).toString) + "\n" + ((h+1).toString + "," + (j+1).toString) + "\n") :: network_string_list
                /*pw_matrix_temp.write("\n")
                pw_matrix_temp.write((h+1).toString + "," + (j+1).toString)
                pw_matrix_temp.write("\n")*/
            }
          }
      }
    val t2 = System.currentTimeMillis()

      println("time needed = " + (t2 -t1))

        // FileWriter schließen
        pw_matrix.close()
        pw_nodes_file.close()
        val pageranks = PageRankBasicForPipeline.executePRB(path_nodes, path_matrix, "", numberGenes)
        // Ein GDF-File schreiben mit dem Netzwerk und den PageRankBasic-Werten
        StatisticsMethods.writeGDFFile_Pagerank(network, pageranks, allGenes, gdf_path_ranks, network_string_list)

        // Community Detection Algorithm. Ordnet die einzelnen Gene Clustern zu
        val clusters = GellyAPI_clean.doClusterDetection(network_nodes_healthy_path, network_matrix_healthy_path, cluster_file_path)
        GellyAPI_clean.writeGDFFile_Clusters(path_matrix, clusters, allGenes_string, gdf_path_cluster, network_string)

        return pageranks
      }

    println("-------------- Doing Network Analysis ----------------")

    var (matrixaslistDiseased, matrixaslistHealthy) = matrixaslist.splitAt(numberDiseased)
    val pageranks_healthy =  doNetworkAnalysisAndGDFFIle(matrixaslistHealthy, network_matrix_healthy_path, network_nodes_healthy_path, network_gdf_healthy, cluster_gdf_healthy, corrMatrix_path_healthy)
    val pageranks_diseased =  doNetworkAnalysisAndGDFFIle(matrixaslistDiseased, network_matrix_diseased_path, network_nodes_diseased_path, network_gdf_diseased, cluster_gdf_diseased, corrMatrix_path_diseased)

    StatisticsMethods.rank_differences(pageranks_healthy,pageranks_diseased, allGenes, ranks_diffs_path)



    println("-------------- Finished With The Pipeline ----------------")


  } //End of MAIN



  // -------------------------------------------------------


  private var path = ""
  private var excludesGenes: Array[String] = null
  private var testFiles_path = ""
  private var additional_input = ""
  private def parseParameters(args: Array[String]): Boolean = {
    if (args.length > 0) {
      path = args(0)
      excludesGenes = args(1).split(",")
      testFiles_path = args(2)
      additional_input = args(3)
      true
    } else {
      false
    }
  }




  def readmiRNA(env: ExecutionEnvironment, path: String, columns: Array[Int]): DataSet[GeneData] = {
    env.readCsvFile[GeneData](
      path,
      fieldDelimiter = "\t",
      includedFields = columns)
  }



  def applyAdditionalInput(input: String): Unit ={

    val numPattern = "[0-9]+".r
    val lines = input.split("STOP")

    for (i <- lines){
      if ( i.contains("factors")) {
        MC_factors = numPattern.findFirstIn(i).get.toString.toInt
      }
      else if (i.contains("networkpath")){
        val networkpath = i.split(" = ")(1)
      }
      else if (i.contains("completion")){
        val matrixAnswer = i.split("=")(1)
        if(matrixAnswer.contains("yes")) matrixBool = true
        else matrixBool = false
      }
      else if (i.contains("completion") && i.contains("regularization")){
        MC_iterations = numPattern.findFirstIn(i).get.toString.toInt
      }
      else if (i.contains("SVM") && i.contains("iterations")){
        SVM_iterations = numPattern.findFirstIn(i).get.toString.toInt
      }
      else if (i.contains("SVM") && i.contains("regularization")){
        SVM_regularization = numPattern.findFirstIn(i).get.toString.toDouble
      }
      else if (i.contains("SVM") && i.contains("stepsize")){
        SVM_stepsize = numPattern.findFirstIn(i).get.toString.toDouble
      }
      else if (i.contains("threshold") || i.contains("thresshold")){
        threshold = numPattern.findFirstIn(i).get.toString.toDouble
      }
      else if (i.contains("selected") && (i.contains("genes") || i.contains("Genes"))){
        noSelectedGenes = numPattern.findFirstIn(i).get.toString.toInt
      }
      else if (i.contains("train") && i.contains("linear") && i.contains("regression")){
        trainingType = "LR"
      }
      else if (i.contains("maxGenes") || i.contains("Genanzahl")){
        maxGenes = numPattern.findFirstIn(i).get.toString.toInt
      }
    }


  }



  // -------------------------------------------------------

}
