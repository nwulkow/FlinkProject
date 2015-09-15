package de.fuberlin.de.largedataanalysis

import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.api.scala._

/**
 * Created by nwulkow on 01.07.15.
 */
object GephiConversion {



  def main(args: Array[String]) {




    val env = ExecutionEnvironment.getExecutionEnvironment
    val datapath: String = "/home/mi/nwulkow/ADL/Data/hprd_cleaned"

    val ppi_raw = getPPIData(env, datapath)

    val gephiformat = ppi_raw.map(c => EdgeClass(c.Name1, c.Name2))
    val nodes = gephiformat.map(c => Node(c.Name1))
    val uniques = nodes.distinct

    uniques.writeAsCsv("/home/mi/nwulkow/ADL/Data/unique_nodes", "\n", "")
    // 7932 verschiedene Proteine!!


    //gephiformat.writeAsCsv("/home/mi/nwulkow/ADL/Data/edges_PR", "\n", " ")
    //nodes.writeAsCsv("/home/mi/nwulkow/ADL/Data/nodes_PR", "\n", "")




    env.execute("GephiConversion")




  }

  case class PPIData(Name1: String, ID1: String, NP1: String, Name2: String, ID2: String, NP2: String, invivo: String, value: String)
  case class EdgeClass(Name1: String, Name2: String)

  case class Node(Name1: String)


  private def getPPIData(env: ExecutionEnvironment, path: String): DataSet[PPIData] = {
    env.readCsvFile[PPIData](
      path,
      fieldDelimiter = "\t")
  }


}
