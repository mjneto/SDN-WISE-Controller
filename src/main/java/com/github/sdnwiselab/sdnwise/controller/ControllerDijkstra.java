/* 
 * Copyright (C) 2015 SDN-WISE
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.sdnwiselab.sdnwise.controller;

import com.github.sdnwiselab.sdnwise.adapter.Adapter;
import com.github.sdnwiselab.sdnwise.packet.NetworkPacket;
import com.github.sdnwiselab.sdnwise.topology.NetworkGraph;
import com.github.sdnwiselab.sdnwise.util.NodeAddress;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
//import org.graphstream.algorithm.Dijkstra;
import org.graphstream.graph.Node;
import org.graphstream.graph.Path;

/**
 * This class implements the Controller class using the Dijkstra routing
 * algorithm in order to find the shortest path between nodes. When a request
 * from the network is sent, this class sends a SDN_WISE_OPEN_PATH message with
 * the shortest path. No action is taken if the topology of the network changes.
 *
 * @author Sebastiano Milardo
 * @version 0.1
 */
public class ControllerDijkstra extends Controller {

    private final Dijkstra dijkstra;
    private String lastSource = "";
    private long lastModification = -1;

    /**
     * Constructor method fo ControllerDijkstra.
     * 
     * @param id ControllerId object.
     * @param lower Lower Adpater object.
     * @param networkGraph NetworkGraph object.
     */
    public ControllerDijkstra(Adapter lower, NetworkGraph networkGraph) {
        super(lower, networkGraph);
        this.dijkstra = new Dijkstra(Dijkstra.Element.EDGE, null, "length");
    }

    @Override
    public final void graphUpdate() {

    }

    @Override
    public final void manageRoutingRequest(NetworkPacket data) {

        String destination = data.getNetId() + "." + data.getDst();
        String source = data.getNetId() + "." + data.getSrc();

        if (!source.equals(destination)) { 

            Node sourceNode = networkGraph.getNode(source);
            Node destinationNode = networkGraph.getNode(destination);
            LinkedList<NodeAddress> path = null;
            Path chosenPath = null;

            if (sourceNode != null && destinationNode != null) {
                if (!lastSource.equals(source) || lastModification != networkGraph.getLastModification()) {
                    results.clear();
                    dijkstra.init(networkGraph.getGraph());
                    dijkstra.setSource(networkGraph.getNode(source));
                    dijkstra.compute();
                    lastSource = source;
                    lastModification = networkGraph.getLastModification();
                } else {
                    path = results.get(data.getDst());
                }
                if (path == null) {
                    path = new LinkedList<>();
                    chosenPath = new Path();
                    chosenPath = chosePathBetweenAll(chosenPath, destination, source);

                    //put the chosen path in the path variable
                    for (Node node : chosenPath.getNodePath()) {
                        path.add((NodeAddress) node.getAttribute("nodeAddress"));
                    }
                                        
                    System.out.println("[CTRL]: src: " + source + " / dst: " + destination + " / path: " + path.toString());
                    results.put(data.getDst(), path);
                }
                if (path.size() > 1) {
                    sendPath((byte) data.getNetId(), path.getFirst(), path);

                    data.unsetRequestFlag();
                    data.setSrc(getSinkAddress());
                    sendNetworkPacket(data);

                } else {
                	//System.out.println("eu não sei se passa");
                    // TODO send a rule in order to say "wait I dont have a path"
                    //sendMessage(data.getNetId(), data.getDst(),(byte) 4, new byte[10]);
                }
            }
        }
    }

    /**
        * This method choose the path which has the node with higher battery level between the lowests.
        * First it call getAllPaths() method from Dijkstra class, which returns all paths between two nodes (node - sink, sink - node).
        * Then it goes through all paths calculated, checking all nodes for the one with the lowest battery level in the that path.
        * After go through a path, it check if the node with the lowest battery level is the highest between the lowests.
        * If it is, it will be the chosen path.
        * 
        * Ex: 
        *  P1: [10 - 5 - 20]
        *  P2: [10 - 2 - 25]
        * The path choosen will be P2, because the node 2 in P2
        * has the highest battery level between the lowests [5, 2]
        * 
        * @author mjneto
    */
    private Path chosePathBetweenAll(Path chosenPath, String destination, String source) {
        /*
         * create two hasmaps, one for the lowest battery in the path and one for
         * keep tracking of the node who has the highest battery level between the lowests
         */
        HashMap<String, String> lowBattNode = new HashMap<>();
        HashMap<String, String> highLowBattNode = new HashMap<>();

        lowBattNode.put("id", "0");
        lowBattNode.put("battery", "999");
        highLowBattNode.put("id", "0");
        highLowBattNode.put("battery", "0");
    
        for (Path allPath : dijkstra.getAllPaths(networkGraph.getNode(destination))) {
            //System.out.println("1 - " + source + " " + destination + " = " + allPath.toString());
            for(Node node : allPath.getNodePath()) {
                //System.out.println("2 - Node: " + node.getId() + " Battery: " + node.getAttribute("battery"));
                if((int) node.getAttribute("battery") < Integer.parseInt(lowBattNode.get("battery"))) {
                    lowBattNode.put("battery", node.getAttribute("battery").toString());
                    lowBattNode.put("id", node.getId().toString());
                    //System.out.println("Lowest Battery: " + lowBattNode.get("battery") + " Node: " + lowBattNode.get("id"));
                }
            }
            if(Integer.parseInt(lowBattNode.get("battery")) > Integer.parseInt(highLowBattNode.get("battery"))) {
                highLowBattNode.put("battery", lowBattNode.get("battery"));
                highLowBattNode.put("id", lowBattNode.get("id"));
                //System.out.println("Highest Lowest Battery: " + highLowBattNode.get("battery") + " Node: " + highLowBattNode.get("id"));
                chosenPath = allPath;
            }
            //reset for the next path
            lowBattNode.put("battery", "999");
            lowBattNode.put("id", "0");
        }
        PathInfo(destination, source, chosenPath, highLowBattNode.get("battery"), highLowBattNode.get("id"));
        return chosenPath;
    }

    /**
        * Method to write the path information in a file. It will read the file searching for strings of the
        * the source and destination nodes. If finds it, it will overwrite the line with the new path information.
        * This read all lines and rewrite the file as a whole, so maybe it will be slower.
        * 
        * @param destination Destination node
        * @param source Source node
        * @param path Path to be written
        * @param lowBattNodeValue Battery level of the node with the lowest battery level in the path
        * @param lowBattNodeId Node ID of the node with the lowest battery level in the path
        *
        * @author mjneto
        */
    private void PathInfo(String destination, String source, Path path, String lowBattNodeValue, String lowBattNodeId) {
        File modifyFile = new File("pathsFile.txt");
        BufferedReader readerFile = null;
        FileWriter fw = null;
        String newLine = source + ":" + destination + ":" + path.toString() + ":" + lowBattNodeId + ":" + lowBattNodeValue;
        String modifiedInfo = "";

        if(path.size() > 1) {
            try {
                readerFile = new BufferedReader(new FileReader(modifyFile));
                String line = readerFile.readLine();

                while(line != null) {
                    if(source.equals(line.split(":")[0]) && destination.equals(line.split(":")[1])) {
                        modifiedInfo += newLine + System.getProperty("line.separator");
                    } else {
                        modifiedInfo += line + System.getProperty("line.separator");
                    }
                    line = readerFile.readLine();
                }
                readerFile.close();
                fw = new FileWriter(modifyFile);
                fw.write(modifiedInfo);
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void setupNetwork() {

    }
}
