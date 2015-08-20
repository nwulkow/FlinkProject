package de.fuberlin.de.largedataanalysis

import java.io.{File, FileWriter}

import breeze.linalg.{DenseMatrix, DenseVector}
import org.apache.flink.api.scala.{DataSet, ExecutionEnvironment, _}
import org.apache.flink.graph.Vertex
import org.apache.flink.ml.MLUtils
import org.apache.flink.ml.classification.SVM
import org.apache.flink.ml.common.LabeledVector
import com.github.projectflink.als.ALSJoin

import scala.reflect.io.Path

import breeze._

object Pipeline {


  val env = ExecutionEnvironment.getExecutionEnvironment
  var matrixaslist = List[List[Double]]()

  def main(args: Array[String]) {

    // -------------------------------------------------------
    // Parameter einlesen
    if (!parseParameters(args)) {
      return
    }
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

    val pw_incompletematrix = new FileWriter(new File(incompletematrix_path))
    val pw_resultlabels = new FileWriter(new File(resultlabels_path))

    // -------------------------------------------------------

    // Vorbereitung der Matrix Completion. Reihen sind die verschiedenen Personen, Spalten die Genecounts
    val numberHealthy = Tools.getListOfFiles(path_healthy).length
    val numberDiseased = Tools.getListOfFiles(path_diseased).length
    val matrixAndGenes = PreprocessingMethods.matrixCreation(Array[String](path_healthy, path_diseased), pw_incompletematrix, excludesGenes)
    var incompleteMatrix = matrixAndGenes._1
    val allGenes = matrixAndGenes._2

    val numberPeople = numberHealthy + numberDiseased
    val numberGenes = allGenes.length

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
    incompleteMatrix = ALSJoin.doMatrixCompletion(incompletematrix_path, 10, 100, 42, Some("dummy string"), path, numberPeople, numberGenes)

    // Die Zeilen der vervollständigten Matrix umwandeln in 'LabeledVectors'
    matrixaslist = PreprocessingMethods.preprocessdataFromMatrix(incompleteMatrix, numberHealthy, pw, matrixaslist)

    // -------------------------------------------------------
    // -------------------------------------------------------
    // -------------------------------------------------------


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
    // -------------------------------------------------------
    // Testen des classifiers

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
      val product = (weightsList(0).toArray, extended_testperson_counts).zipped.map((c1, c2) => c1 * c2).sum
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
    val sortedweights = weightsList(0).toArray.sorted
    var topPositiveWeights = List[(String, Double)]()
    var topNegativeWeights = List[(String, Double)]()

    var genesWithWeights = List[(String,Double)]()
    val weightsListCopy = weightsList
    for (k <- Range(0,numberGenes)){
      val correspondingGeneIndex = weightsListCopy(0).toArray.indexOf(sortedweights(allGenes.length - 1 - k))
      genesWithWeights ::= (allGenes(correspondingGeneIndex), sortedweights(allGenes.length - 1 - k))
      weightsListCopy(0)(correspondingGeneIndex) = Math.exp(1000) // Sicherstellen, dass kein Gen doppelt in der Liste auftaucht (wenn mehrere Gene das gleiche Gewicht haben)
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

    var (matrixaslistHealthy, matrixaslistDiseased) = matrixaslist.splitAt(numberHealthy)
    doNetworkAnalysisAndGDFFIle(matrixaslistHealthy, network_matrix_healthy_path, network_nodes_healthy_path, network_gdf_healthy, cluster_gdf_healthy)
    doNetworkAnalysisAndGDFFIle(matrixaslistDiseased, network_matrix_diseased_path, network_nodes_diseased_path, network_gdf_diseased, cluster_gdf_diseased)

    def doNetworkAnalysisAndGDFFIle(matrixaslist: List[List[Double]], path_matrix: String, path_nodes: String, gdf_path_ranks: String, gdf_path_cluster: String) {

      var meansList = List[Double]()
      var varianceList = List[Double]()
      val noGenes = matrixaslist(0).length
      var matrixaslistTranspose = matrixaslist.transpose
      val noPeople = matrixaslistTranspose(0).length

      val pw_matrix = new FileWriter(path_matrix)
      val pw_nodes_file = new FileWriter(path_nodes)

      var network = List[Array[Double]]() // Netzwerk wird gespeichert, um später ein GDF-File zu erzeugen

      // Netzwerk-Matrix wird erzeugt und in ein .txt-File geschrieben. Das Netzwerk ist ungewichtet, der threshold muss angegeben werden
      for (j <- Range(0, noGenes)) {
        meansList = StatisticsMethods.meanOfArray(matrixaslistTranspose(j)) :: meansList
        varianceList = StatisticsMethods.variance(matrixaslistTranspose(j)) :: varianceList
      }

      val threshold = 0.8 // Threshold

      for (j <- Range(0, noGenes)) {
        pw_nodes_file.write((j+1).toString + " " +  ((j+1)*1000000).toString)
        pw_nodes_file.write("\n")

        for (h <- Range(j, noGenes)) {
          if (threshold < Math.abs(StatisticsMethods.correlationcoefficient(matrixaslistTranspose(j), matrixaslistTranspose(h)))) {
            pw_matrix.write((j+1).toString + " " + (h+1).toString + " " + 1.toString)
            pw_matrix.write("\n")
            pw_matrix.write((h+1).toString + " " + (j+1).toString + " " + 1.toString)
            pw_matrix.write("\n")
            network = Array[Double](j, h) :: network
            network = Array[Double](h, j) :: network
          }
        }
      }
      // FileWriter schließen
      pw_matrix.close()
      pw_nodes_file.close()
      val pageranks = PageRankBasicForPipeline.executePRB(path_nodes, path_matrix, "", numberGenes)
     // Ein GDF-File schreiben mit dem Netzwerk und den PageRankBasic-Werten
      StatisticsMethods.writeGDFFile_Pagerank(network, pageranks, allGenes, gdf_path_ranks)


      // Community Detection Algorithm. Ordnet die einzelnen Gene Clustern zu
      val clusters =  GellyAPI_clean.doClusterDetection(network_nodes_healthy_path, network_matrix_healthy_path, cluster_file_path)
      GellyAPI_clean.writeGDFFile_Clusters(path_matrix,clusters, allGenes_string, gdf_path_cluster)

    }




  } //End of MAIN



  // -------------------------------------------------------


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




  def readmiRNA(env: ExecutionEnvironment, path: String, columns: Array[Int]): DataSet[GeneData] = {
    env.readCsvFile[GeneData](
      path,
      fieldDelimiter = "\t",
      includedFields = columns)
  }



  // -------------------------------------------------------

}
