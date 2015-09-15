import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.graph.Edge;
import org.apache.flink.graph.Graph;
import org.apache.flink.graph.Vertex;
//import org.apache.flink.api.scala.DataSet;
import org.apache.flink.graph.example.utils.CommunityDetectionData;
import org.apache.flink.graph.library.CommunityDetectionAlgorithm;
import org.apache.flink.graph.utils.Tuple2ToVertexMap;
import org.apache.flink.graph.utils.Tuple3ToEdgeMap;

import org.apache.flink.api.java.DataSet;
import scala.Tuple2;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by nwulkow on 30.06.15.
 */
public class GellyAPI {

    public static void main(String[] args) throws Exception {


        Vertex<Long, Long> v = new Vertex<Long, Long>(1L, 2L);

        Edge<Long, Double> e = new Edge<Long, Double>(1L, 2L, 0.5);

        List<Vertex> lv = new ArrayList<Vertex>();
//----------------------------------------------------------------------------------

        ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

        boolean fileOutput = true;

        String edgesInputPath = "/home/mi/nwulkow/ADL/Data/edgespath_bigger2";
        DataSet<Edge<Long, Double>> edges = getEdgesDataSet(env, edgesInputPath);

        System.out.println("Edges geladen");
        String vertexInputPath = "/home/mi/nwulkow/ADL/Data/verticespath_bigger2";
        DataSet<Vertex<Long, Long>> vertices = getVerticesDataSet(env, vertexInputPath);
        System.out.println("Vertices geladen");

        Graph<Long,Long, Double> graph = Graph.fromDataSet(vertices, edges ,env);
        graph.outDegrees();

        //edges.writeAsCsv("/home/mi/nwulkow/ADL/Data/edgeoutput", "\n", ",");
        Integer maxIterations = CommunityDetectionData.MAX_ITERATIONS;
        Double delta = CommunityDetectionData.DELTA;
        DataSet<Vertex<Long, Long>> communityVertices =
                graph.run(new CommunityDetectionAlgorithm(maxIterations, delta )).getVertices();

        // emit result
        if (fileOutput) {
            communityVertices.writeAsCsv("/home/mi/nwulkow/ADL/Data/vertexidsoutput_uw5", "\n", ",");

            // since file sinks are lazy, we trigger the execution explicitly
            env.execute("Executing Community Detection Example");
        } else {
            communityVertices.print();
        }



}

    public static class TwoTuple{
        String first;
        String second;

        public TwoTuple(String first, String second){
            this.first = first;
            this.second = second;
        }
    }


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
