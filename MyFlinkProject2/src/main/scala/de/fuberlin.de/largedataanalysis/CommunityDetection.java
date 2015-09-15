package de.fuberlin.de.largedataanalysis; /**
 * Created by nwulkow on 30.06.15.
 */
//package org.apache.flink.graph.example;

import org.apache.flink.api.common.ProgramDescription;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.graph.Edge;
import org.apache.flink.graph.Graph;
import org.apache.flink.graph.Vertex;
import org.apache.flink.graph.example.utils.CommunityDetectionData;
import org.apache.flink.graph.library.CommunityDetectionAlgorithm;
import org.apache.flink.graph.utils.Tuple3ToEdgeMap;


public class CommunityDetection implements ProgramDescription {


    @SuppressWarnings("serial")
    public void main(String [] args) throws Exception {

        if(!parseParameters(args)) {
            return;
        }

        // set up the execution environment
        ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();

        // set up the graph
        DataSet<Edge<Long, Double>> edges = getEdgesDataSet(env);
        Graph<Long, Long, Double> graph = Graph.fromDataSet(edges,
                new MapFunction<Long, Long>() {

                    public Long map(Long label) {
                        return label;
                    }
                }, env);

        // the result is in the form of <vertexId, communityId>, where the communityId is the label
        // which the vertex converged to
        DataSet<Vertex<Long, Long>> communityVertices =
                graph.run(new CommunityDetectionAlgorithm(maxIterations, delta)).getVertices();

        // emit result
        if (fileOutput) {
            communityVertices.writeAsCsv(outputPath, "\n", ",");

            // since file sinks are lazy, we trigger the execution explicitly
            env.execute("Executing Community Detection Example");
        } else {
            communityVertices.print();
        }

    }


    public String getDescription() {
        return "Community Detection";
    }

    // *************************************************************************
    // UTIL METHODS
    // *************************************************************************

    private  boolean fileOutput = false;
    private  String edgeInputPath = null;
    private  String outputPath = null;
    private  Integer maxIterations = CommunityDetectionData.MAX_ITERATIONS;
    private  Double delta = CommunityDetectionData.DELTA;

    private boolean parseParameters(String [] args) {
        if(args.length > 0) {
            if(args.length != 4) {
                System.err.println("Usage CommunityDetection <edge path> <output path> " +
                        "<num iterations> <delta>");
                return false;
            }

            fileOutput = true;
            edgeInputPath = args[0];
            outputPath = args[1];
            maxIterations = Integer.parseInt(args[2]);
            delta = Double.parseDouble(args[3]);

        } else {
            System.out.println("Executing SimpleCommunityDetection example with default parameters and built-in default data.");
            System.out.println("Provide parameters to read input data from files.");
            System.out.println("Usage CommunityDetection <edge path> <output path> " +
                    "<num iterations> <delta>");
        }

        return true;
    }

    private DataSet<Edge<Long, Double>> getEdgesDataSet(ExecutionEnvironment env) {

        if(fileOutput) {
            return env.readCsvFile(edgeInputPath)
                    .ignoreComments("#")
                    .fieldDelimiter("\t")
                    .lineDelimiter("\n")
                    .types(Long.class, Long.class, Double.class)
                    .map(new Tuple3ToEdgeMap<Long, Double>());
        } else {
            return CommunityDetectionData.getDefaultEdgeDataSet(env);
        }
    }
}

