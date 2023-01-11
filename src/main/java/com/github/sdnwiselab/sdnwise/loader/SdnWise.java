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
package com.github.sdnwiselab.sdnwise.loader;

import com.github.sdnwiselab.sdnwise.configuration.Configurator;
import com.github.sdnwiselab.sdnwise.controller.Controller;
import com.github.sdnwiselab.sdnwise.controller.ControllerFactory;
import com.github.sdnwiselab.sdnwise.packet.DataPacket;
import com.github.sdnwiselab.sdnwise.util.NodeAddress;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SdnWise class of the SDN-WISE project. It loads the configuration file and
 * starts the the Controller.
 *
 * @author Sebastiano Milardo
 * @version 0.1
 */
public class SdnWise {

    /**
     * Starts the components of the SDN-WISE Controller.
     *
     * @param args the command line arguments
     * @throws java.lang.Exception
     */
    public static void main(String[] args) throws Exception {
        new SdnWise().startExample();
    }

    private Controller controller;

    /**
     * Starts the Controller layer of the SDN-WISE network. The path to the
     * configurations are specified in the configFilePath String. The options to
     * be specified in this file are: a "lower" Adapter, in order to communicate
     * with the flowVisor (See the Adapter javadoc for more info), an
     * "algorithm" for calculating the shortest path in the network. The only
     * supported at the moment is "DIJKSTRA". A "map" which contains
     * informations regarding the "TIMEOUT" in order to remove a non responding
     * node from the topology, a "RSSI_RESOLUTION" value that triggers an event
     * when a link rssi value changes more than the set threshold.
     *
     * @param configFilePath a String that specifies the path to the
     * configuration file.
     * @return the Controller layer of the current SDN-WISE network.
     */
    public Controller startController(String configFilePath) {
        InputStream configFileURI = null;
        if (configFilePath == null || configFilePath.isEmpty()) {
            configFileURI = this.getClass().getResourceAsStream("/config.ini");
        } else {
            try {
                configFileURI = new FileInputStream(configFilePath);
            } catch (FileNotFoundException ex) {
                Logger.getLogger(SdnWise.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        Configurator conf = Configurator.load(configFileURI);
        controller = new ControllerFactory().getController(conf.getController());
        new Thread(controller).start();
        return controller;
    }


    public void startExample() {
        controller = startController("");

        initPathsFile();

        System.out.println("SDN-WISE Controller running....");
        
        // We wait for the network to start 
        try {
            Thread.sleep(60000);
            
            NodeAddress dst; 
            NodeAddress src;

            // Then we query the nodes
            while (true){    
                for (int i = 1; i < 12; i++){
                    System.out.println("- quering node " + i);
                    int netId = 1;
                    /*NodeAddress dst = new NodeAddress(i);
                    NodeAddress src = new NodeAddress(1);*/
                    
                    dst = new NodeAddress(i);
                    src = new NodeAddress(1);

                    //System.out.println("src: " + src + " dst: " + dst);
                    
                    DataPacket p = new DataPacket(netId,src,dst);
                    p.setNxhop(src);
                    setAgg(dst, src, netId, p);
                    controller.sendNetworkPacket(p);
                    Thread.sleep(2000);
                }
            }
        
        } catch (InterruptedException ex) {
            Logger.getLogger(SdnWise.class.getName()).log(Level.SEVERE, null, ex);
        }
            
    }

    /**
     * This method read the pathsFile.txt and set the aggregation rate by the node with the lowest battery of the path.
     * The aggregation rate is set in the DataPacket p.
     * 
     * The battery level (hex 0-255) is converted to percentage (0-100). 
     * Then the aggregation rate is set proportional to the battery level (batteryLevel/100) because in the node it will
     * be subtracted from the value (Ex: batteryLevel = 93, divide by 100 = 0.93, rate = 1 - 0.93 = 0.07 => 7%).
     * If the battery level is 100% the aggregation rate is 0. If the battery level is 0% the aggregation rate is 100%.
     * 
     * @param dst destination
     * @param src source
     * @param netId network id
     * @param p DataPacket with the aggregation info to be sent in the network
     * 
     * @author mjneto
     */
    private void setAgg(NodeAddress dst, NodeAddress src, int netId, DataPacket p) {
        BufferedReader reader;

        try {
            reader = new BufferedReader(new FileReader("pathsFile.txt"));
            String line = reader.readLine();
            while (line != null) {
                //condition to check if the line has any information about the path already, and if it is the path we want
                if(line.split(":").length > 2 
                && (line.split(":")[1].equals(String.valueOf(netId + "." + src)) 
                && line.split(":")[0].equals(String.valueOf(netId + "." + dst)))) {
                    //System.out.println(line);
                    //System.out.println("lowest battery node: " + line.split(":")[3] + " with battery level: " + line.split(":")[4]);
                    float batteryLevel = ((Integer.parseInt(line.split(":")[4]) * 100) / 255);
                    //System.out.println("battery level: " + batteryLevel);
                    if(batteryLevel == 100) {
                        p.setPayload(("Agg:0.0").getBytes(Charset.forName("UTF-8")));
                    } else {
                        p.setPayload(("Agg:" + (batteryLevel/100)).getBytes(Charset.forName("UTF-8")));
                        //System.out.println("BL" + (batteryLevel/100));
                    }
                }
                line = reader.readLine();
            }
            reader.close(); 
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * This method creates and initializate the 'pathsFile.txt' file.
     * It will be used to keep tracking of nodes, their paths and lowest battery node id and level.
     * The file will be created in the same folder as the project.
     * The structure of the file is: 
     *      Source (Node Adress format):Destination (Node Adress format):Path (Path format):Node ID with lowest battery level in the path (Node Adress format):The battery level of that node (Hex 0-255)
     *      Ex: 1.0.1:1.0.2:[1.0.1, 1.0.3, 1.0.6, 1.0.2]:1.0.3:252
     * 
     * @author: mjneto
     */
    private void initPathsFile() {
        File f = new File("pathsFile.txt");
        try {
            FileWriter fw = new FileWriter(f, false);
            for(int i = 1; i <= 9; i++){
                fw.write("1.0." + String.valueOf(i+1) + ":" + "1.0.1" + System.getProperty("line.separator"));
                fw.write("1.0.1" + ":" + "1.0." + String.valueOf(i+1) + System.getProperty("line.separator"));
            }
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
