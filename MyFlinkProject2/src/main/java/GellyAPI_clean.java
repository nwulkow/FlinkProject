import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.graph.Edge;
import org.apache.flink.graph.Graph;
import org.apache.flink.graph.Vertex;
import org.apache.flink.graph.example.utils.CommunityDetectionData;
import org.apache.flink.graph.library.CommunityDetectionAlgorithm;
import org.apache.flink.graph.utils.Tuple2ToVertexMap;
import org.apache.flink.graph.utils.Tuple3ToEdgeMap;

import java.util.ArrayList;
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
        String edgesInputPath = "/home/mi/nwulkow/ADL/Data/short_edges_cda";
        DataSet<Edge<Long, Double>> edges = getEdgesDataSet(env, edgesInputPath);
        System.out.println("Edges geladen");

        // Lade die Vertices
        String vertexInputPath = "/home/mi/nwulkow/ADL/Data/short_cda_nodes";
        DataSet<Vertex<Long, Long>> vertices = getVerticesDataSet(env, vertexInputPath);
        System.out.println("Vertices geladen");

        // Erstelle den Graphen mit dne geladenen Edges und Vertices
        Graph<Long,Long, Double> graph = Graph.fromDataSet(vertices, edges ,env);

        // Lade die Parameter für den Algorithmus
        Integer maxIterations = CommunityDetectionData.MAX_ITERATIONS;
        Double delta = CommunityDetectionData.DELTA;

        // Führe den Algorthmus durch
        DataSet<Vertex<Long, Long>> communityVertices =
                graph.run(new CommunityDetectionAlgorithm(maxIterations, delta )).getVertices();

        // Gibt das Resultat aus
        if (fileOutput) {
            communityVertices.writeAsCsv("/home/mi/nwulkow/ADL/Data/short_cda_output", "\n", ",");

            env.execute("Executing Community Detection Example");
        } else {
            communityVertices.print();
        }



}


       // Methoden zum Einlesen der DataSets
    private static DataSet<Edge<Long, Double>> getEdgesDataSet(ExecutionEnvironment env,String edgesInputPath) {
            return env.readCsvFile(edgesInputPath)
                    .ignoreComments("#")
                    .fieldDelimiter(",")
                    .lineDelimiter("\n")
                    .types(Long.class, Long.class, Double.class)
                    .map(new Tuple3ToEdgeMap<Long, Double>());

    }

    private static DataSet<Vertex<Long, Long>> getVerticesDataSet(ExecutionEnvironment env,String vertexInputPath) {
        return env.readCsvFile(vertexInputPath)
                .ignoreComments("#")
                .fieldDelimiter(",")
                .lineDelimiter("\n")
                .types(Long.class, Long.class)
                .map(new Tuple2ToVertexMap<Long, Long>());

    }

}
