/*
 * Brian Lamb and Travis Alpers
 * CSCI466 Lab 5
 * UDP Vector Routing
 * **NOTE** ALL DATA IS STORED IN BIG-ENDIAN IN BYTE ARRAYS **NOTE**
 */
package udpvectorrouting;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UDPVectorRouting {

    //We are using 4 bytes per value with 4 values - instance number and distance vector
    private static final int BUFFER_SIZE = 16;
    
    //Java bytes aren't unsigned......wtf
    private static final byte ACK = -1;
    
    //Instance port
    private static int port;
    
    //Ports from file
    private static int[] ports;
    
    //Inidicates have received alive from other instances
    private static boolean[] routersOnline;
    
    //Init instanceNum to show uninitialized
    private static int instanceNum = -1;
    
    //Initial routing table - not necessary
    private static int[][] routingTable;
    
    //Current distance vector
    private static int[] distanceVector;
    
    //Routes for distance vector
    private static int[] distanceVectorRoutes;
    
    //Instances socket
    private static DatagramSocket socket = null;
    
    /*
     * Main control loop 
     */
    public static void main(String args[]) throws IOException {
        //Try start - read from file and open socket
        try {
            init();
        } catch (IOException ex) {
            Logger.getLogger(UDPVectorRouting.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("Error");
            return;
        }
        
        //Debug msg
        System.out.println("Router " + ((char)(instanceNum + 0x58)) + " running on port " + port);
        System.out.println("Distance vector on Router " + ((char)(instanceNum + 0x58)) + " is: ");
        System.out.println(buildDistanceVectorString(routingTable[instanceNum]));
        
        //Wait for all instances loop
        while (true) {
            //Send alive data
            sendDistanceVector();
            
            //If all instances checked in, break
            if (allRoutersOnline()) {
                break;
            }
            
            //Build response packet
            byte[] data = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(data, data.length);
            try {
                //Get response
                socket.receive(packet);
            } catch (IOException e) {
                //Continue on timeout
                continue;
            }
            //Unpacket data from packet
            int[] initData = unpackData(packet.getData());
            
            //Check for ACK and Router not marked alive
            if (!routersOnline[initData[0]]) {
                
                //Mark alive
                routersOnline[initData[0]] = true;
                
                //Print debug msg
                System.out.println("Router " + ((char)(initData[0] + 0x58)) + " Online.");
                
                //Update Routing Table           
                setRoutingTable(initData);
            }
        }
        
        //Clear buffer of alive msg's
        while (true) {            
            //Create response packet
            byte[] data = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(data, data.length);
            try {
                //Receive 
                socket.receive(packet);
            } catch (IOException e) {
                //Timeout means buffer is clear, break
                break;
            }
        }

        //Looping twice, once for initial distance vector run, then again after changing to value of 60 for X - Y connection
        for (int j = 0; j < 2; j++) {     
            
            //If second run, set distance between X and Y
            if (j == 1 && (instanceNum == 0 || instanceNum == 1)) {
                int update = 60;
                routingTable[instanceNum][1 - instanceNum] = update;
                
                //Reset distanceVector
                for (int i = 0; i < 3; i++) {
                    distanceVector[i] = routingTable[instanceNum][i];
                }
                
                System.out.println("Connection between " + ((char)(instanceNum + 0x58)) + " and " + ((char)(1 - instanceNum + 0x58)) + " is updated to " + update);                
                
                                //Run bellman-ford algorithm with received data - return value signals update
                if (bellmanFord()) {

                    //Value updated - debug msgs
                    System.out.println("Distance vector on router " + ((char)(instanceNum + 0x58)) + " is updated to: ");
                    System.out.println(buildDistanceVectorString(distanceVector));

                    //Send new distance vector
                    sendDistanceVector();

                } else {
                    //No update - debug msg
                    System.out.println("Distance vector on router " + ((char)(instanceNum + 0x58)) + " is not updated.");
                }  
                
            }
        
            //Send distanceVector to other routers
            sendDistanceVector();

            while (true) {
                //Create response packet
                byte[] data = new byte[BUFFER_SIZE];
                DatagramPacket packet = new DatagramPacket(data, data.length);
                try {
                    //Receive
                    socket.receive(packet);
                } catch (IOException e) {
                    //Timeout means we are done
                    System.out.println("Timeout");
                    break;
                }
                //Unpack data
                int[] receivedDistanceVectorData = unpackData(packet.getData());
                //Build received distance vector
                int[] receivedDistanceVector = new int[] {
                    receivedDistanceVectorData[1],
                    receivedDistanceVectorData[2],
                    receivedDistanceVectorData[3]                
                };
                
                //Set updated routes for other routers
                setRoutingTable(receivedDistanceVectorData);

                //Debug msg
                System.out.println("Received distance vector from router " + ((char)(receivedDistanceVectorData[0] + 0x58)) + ": " + buildDistanceVectorString(receivedDistanceVector));
                
                //Run bellman-ford algorithm with received data - return value signals update
                if (bellmanFord()) {

                    //Value updated - debug msgs
                    System.out.println("Distance vector on router " + ((char)(instanceNum + 0x58)) + " is updated to: ");
                    System.out.println(buildDistanceVectorString(distanceVector));

                    //Send new distance vector
                    sendDistanceVector();

                } else {
                    //No update - debug msg
                    System.out.println("Distance vector on router " + ((char)(instanceNum + 0x58)) + " is not updated.");
                }            
            }

        }
        
    }  
    
    /*
     * Updates Routing Table from received data, flags routes which have been updated
     *
     */
    private static void setRoutingTable(int[] receivedDistanceVectorData) {
        for (int i = 0; i < 3; i++) {
            if (distanceVectorRoutes[i] == receivedDistanceVectorData[0] &&  routingTable[receivedDistanceVectorData[0]][i] != receivedDistanceVectorData[i+1]) {
                distanceVectorRoutes[i] = -1;
            }
            routingTable[receivedDistanceVectorData[0]][i] = receivedDistanceVectorData[i+1];
        }
    }
    
    /*
     * Bellman-Ford algorithm 
     * Returns boolean indicated distance vector update
     */
    private static boolean bellmanFord() {
        //Init to no update
        boolean hasUpdated = false;
        
        for (int i = 0; i < 3; i++) {
            if (distanceVectorRoutes[i] == -1) {
                distanceVector[i] = routingTable[instanceNum][i];
                hasUpdated = true;
                distanceVectorRoutes[i] = i;
            }
            for (int j = 0; j < 3; j++) {
                if (distanceVector[i] > routingTable[j][i] + routingTable[instanceNum][j]) {
                    hasUpdated = true;
                    distanceVector[i] = routingTable[j][i] + routingTable[instanceNum][j];
                    distanceVectorRoutes[i] = j;
                }
            }
        }
        
        return hasUpdated;
    }
    
    /*
     * Helper to check if all routers are online 
     */
    private static boolean allRoutersOnline() {
        for (int i = 0; i < 3; i++) {
            if (!routersOnline[i]) {
                return false;
            }
        }
        return true;
    }
    
    /*
     * Helper to build vector string from int array 
     */
    private static String buildDistanceVectorString(int[] distanceVector) {
        String out = "<";
        String delimiter = "";
        for (int i = 0; i < 3; i++) {
            out += delimiter + distanceVector[i];
            delimiter = ", ";
        }
        out += ">";
        return out;
    }
    
    /*
     * Sends passed in data to rest of ports
     */
    private static void sendData(byte[] data) throws IOException {     
        //Iterate over ports
        for (int i = 0; i < 3; i++) {
            //Don't send to self
            if (ports[i] == port) {
                continue;
            }
            
            //Localhost
            InetAddress address = InetAddress.getByName("127.0.0.1");
            
            //Build packet with proper port
            DatagramPacket packet = new DatagramPacket(data, data.length, address, ports[i]);
            
            //Send
            socket.send(packet);            
        }
    }
    
    /*
     * Helper to send distance vector for current instance
     */
    private static void sendDistanceVector() throws UnknownHostException, IOException {
        //Build data array
        byte[] data = new byte[BUFFER_SIZE];
        
        //Packet distance vector
        packDistanceVector(data);
        
        //Send
        sendData(data);
    }
    
    /*
     * Helper to unpack byte array into int array 
     */
    private static int[] unpackData(byte[] data) {
        int[] distanceVector = new int[4];
        //Iterate over each integer
        for (int i = 0; i < 4; i++) {
            //Bitwise join bytes to integer
            distanceVector[i] = data[i * 4 + 0] << 24 | data[i * 4 + 1] << 16 | data[i * 4 + 2] << 8 | data[i * 4 + 3];            
        }
        //Return built array
        return distanceVector;
    }
    
    /*
     * Helper to pack distance vector
     */
    private static void packDistanceVector(byte[] data) {
        //Instance number sending is first int (pos 0)
        data[0] = (byte)(instanceNum >> 24);
        data[1] = (byte)(instanceNum >> 16);
        data[2] = (byte)(instanceNum >> 8);
        data[3] = (byte)(instanceNum >> 0);
        
        //Pack distance vector in pos's 1-3
        for (int i = 0; i < 3; i++) {
            data[4 + i*4 + 0] = (byte)(distanceVector[i] >> 24);
            data[4 + i*4 + 1] = (byte)(distanceVector[i] >> 16);
            data[4 + i*4 + 2] = (byte)(distanceVector[i] >> 8);
            data[4 + i*4 + 3] = (byte)(distanceVector[i] >> 0);
        }
    }
    
    /*
     * Attempt to open socket on port
     * Returns success or failure indicating port in use
     */
    private static boolean trySetPort(int port) {
        //Try open socket
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException ex) {
            //Open failure - Debug msg and return false
            System.out.println("Port " + port + " in use.");
            return false;
        }
        
        //Open success - set timeout
        try {
            socket.setSoTimeout(2000);
        } catch (SocketException ex) {
        }
        
        //Return success
        return true;
    }
    
    /*
     * init()
     * Reads from file and attempts to open correct port
     * Instance number is based on which port is able to be opened
     * If first port in file can be opened, first instance, etc...
     */
    private static void init() throws IOException {
        //Open file
        BufferedReader reader = new BufferedReader(new FileReader("configuration.txt"));
        
        //Read ports
        String[] portsString = reader.readLine().split("\\t");
        
        //Verify ports format
        if (portsString.length < 3) {
            throw new IOException("Error reading ports from configuration.txt");
        }
        
        //Init ports
        ports = new int[3];
        
        //Parse port strings to ints and try to open
        for (int i = 0; i < 3; i++) {
            ports[i] = Integer.parseInt(portsString[i]);
            //If set succeeds, port is not in use
            if (instanceNum == ACK && trySetPort(ports[i])) {
                //Set port num
                port = ports[i];
                
                //Set instance num
                instanceNum = i;  
                
            }
        }
        
        //Check if invalid instance (no port was successfully opened)
        if (instanceNum == -1) {
            throw new IOException("Error creating instance.");
        }
        
        //Init routers alive to false
        routersOnline = new boolean[3];
        
        //Mark self alive
        routersOnline[instanceNum] = true;
        
        //Skip lines for other instances - dont' read other distance vectors
        for (int i = 0; i < instanceNum; i++) {
            String trash = reader.readLine();
        }
        
        //Read table for correct line
        String[] line = reader.readLine().split("\\t");
        
        //Init routing table
        routingTable = new int[3][3];
        
        //Parse initial routing table
        for (int j = 0; j < 3; j++) {
            routingTable[instanceNum][j] = Integer.parseInt(line[j]);
        }       
        
        distanceVector = new int[3];
        
        //Deep copy instance's routing vector
        for (int i = 0; i < 3; i++) {
            distanceVector[i] = routingTable[instanceNum][i];
        }
        
        distanceVectorRoutes = new int[3];
        
        for (int i = 0; i < 3; i++) {
            distanceVectorRoutes[i] = i;
        }
    }
    
}
