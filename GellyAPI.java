package de.fuberlin.de.largedataanalysis;

import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.graph.Edge;
import org.apache.flink.graph.Graph;
import org.apache.flink.graph.Vertex;
import org.apache.flink.graph.example.utils.CommunityDetectionData;
import org.apache.flink.graph.library.CommunityDetectionAlgorithm;
import org.apache.flink.graph.utils.Tuple2ToVertexMap;
import org.apache.flink.graph.utils.Tuple3ToEdgeMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

//import org.apache.flink.api.scala.DataSet;

/**
 * Created by nwulkow on 30.06.15.
 */
public class GellyAPI_clean {

    public static void main(String[] args) throws Exception {



        ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

        boolean fileOutput = true;

        // Lade die Edges
        String edgesInputPath = "/home/mi/nwulkow/ADL/Projekt/Data/BCGSC__IlluminaHiSeq_miRNASeq/Level_3/Output/network_matrix_healthy";
        DataSet<Edge<Long, Double>> edges = getEdgesDataSet(env, edgesInputPath);
        System.out.println("Edges geladen");

        // Lade die Vertices
        String vertexInputPath = "/home/mi/nwulkow/ADL/Projekt/Data/BCGSC__IlluminaHiSeq_miRNASeq/Level_3/Output/network_nodes_healthy";
        DataSet<Vertex<Long, Long>> vertices = getVerticesDataSet(env, vertexInputPath);
        System.out.println("Vertices geladen");


        // Erstelle den Graphen mit dne geladenen Edges und Vertices
        Graph<Long,Long, Double> graph = Graph.fromDataSet(vertices, edges ,env);

        // Lade die Parameter für den Algorithmus
        Integer maxIterations = CommunityDetectionData.MAX_ITERATIONS;
        Double delta = CommunityDetectionData.DELTA;

        // Führe den Algorithmus durch
        DataSet<Vertex<Long, Long>> communityVertices =
                graph.run(new CommunityDetectionAlgorithm(maxIterations, delta )).getVertices();

        // Gibt das Resultat aus
        if (fileOutput) {
            communityVertices.writeAsCsv("/home/mi/nwulkow/ADL/Projekt/Data/BCGSC__IlluminaHiSeq_miRNASeq/Level_3/Output/GellyAPI_output", "\n", ",");

            env.execute("Executing Community Detection Example");
        } else {
            communityVertices.print();
        }



}



    public static DataSet<Vertex<Long,Long>> doClusterDetection(String vertexInputPath, String edgesInputPath, String outputPath) throws Exception {

        boolean fileOutput = true;
        ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
        DataSet<Edge<Long, Double>> edges = getEdgesDataSet(env, edgesInputPath);
        DataSet<Vertex<Long, Long>> vertices = getVerticesDataSet(env, vertexInputPath);

        Graph<Long,Long, Double> graph = Graph.fromDataSet(vertices, edges ,env);

        Integer maxIterations = CommunityDetectionData.MAX_ITERATIONS;
        Double delta = CommunityDetectionData.DELTA;

        DataSet<Vertex<Long, Long>> communityVertices =
                graph.run(new CommunityDetectionAlgorithm(maxIterations, delta )).getVertices();

      /*  if (fileOutput) {
            communityVertices.writeAsCsv(outputPath, "\n", ",");

            env.execute("Executing Community Detection Example");
        } else {
            communityVertices.print();
        }*/

        return communityVertices;

    }



       // Methoden zum Einlesen der DataSets
    public static DataSet<Edge<Long, Double>> getEdgesDataSet(ExecutionEnvironment env,String edgesInputPath) {
            return env.readCsvFile(edgesInputPath)
                    .ignoreComments("#")
                    .fieldDelimiter(" ")
                    .lineDelimiter("\n")
                    .types(Long.class, Long.class, Double.class)
                    .map(new Tuple3ToEdgeMap<Long, Double>());

    }

    private static DataSet<Vertex<Long, Long>> getVerticesDataSet(ExecutionEnvironment env,String vertexInputPath) {
        return env.readCsvFile(vertexInputPath)
                .ignoreComments("#")
                .fieldDelimiter(" ")
                .lineDelimiter("\n")
                .types(Long.class, Long.class)
                .map(new Tuple2ToVertexMap<Long, Long>());

    }



    public static void writeGDFFile_Clusters(String network_path, DataSet<Vertex<Long, Long>> clusters, String[] allGenes, String gdfpath) throws Exception {

        ExecutionEnvironment env = ExecutionEnvironment.createCollectionsEnvironment();
        List<Edge<Long,Double>> network = getEdgesDataSet(env, network_path).collect();


        try {

            File file = new File(gdfpath);
            //if (!file.exists()) {
            //    file.createNewFile();
            //}

            FileWriter fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);

            bw.write("nodedef>name VARCHAR, cluster DOUBLE, label VARCHAR");
            bw.write("\n");
            List<Vertex<Long,Long>> clusterscollect = clusters.collect();

            for (Vertex<Long,Long> node : clusterscollect) {
                int nodeId = node.getId().intValue();
                bw.write(node.getId() + "," + node.getValue() + "," + allGenes[nodeId - 1]);
                bw.write("\n");
            }

            bw.write("edgedef>node1 VARCHAR, node2 VARCHAR");
            bw.write("\n");
            for (int i = 0; i < network.size(); i++){
                Edge<Long,Double> current = network.get(i);
                bw.write(current.getSource().intValue() + "," + current.getTarget().intValue());
                bw.write("\n");
            }


            bw.close();


        } catch (IOException e) {
            e.printStackTrace();
        }

    }



}
