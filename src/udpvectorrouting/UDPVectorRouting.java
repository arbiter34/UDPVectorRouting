/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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

/**
 *
 * @author alperst
 */
public class UDPVectorRouting {

  
    private static final int BUFFER_SIZE = 16;
    
    private static int port;
    
    private static int[] ports;
    
    private static boolean[] routersOnline;
    
    private static int instanceNum = -1;
    
    private static int[][] routingTable;
    
    private static int[] distanceVector;
    
    private static int[] directConnections;
    
    private static DatagramSocket socket = null;
    
    public static void main(String args[]) throws IOException {
        try {
            startInstance();
        } catch (IOException ex) {
            Logger.getLogger(UDPVectorRouting.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("Error");
            return;
        }
        
        System.out.println("Router " + ((char)(instanceNum + 0x58)) + " running on port " + port);
        printDistanceVector();
        
        while (true) {
            sendInitData();
            
            if (allRoutersOnline()) {
                break;
            }
            
            byte[] data = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(data, data.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                continue;
            }
            int[] initData = unpackData(packet.getData());
            if (initData[0] == -1 && !routersOnline[initData[1]]) {
                routersOnline[initData[1]] = true;
                System.out.println("Router " + ((char)(initData[1] + 0x58)) + " Online.");
            }
        }
        
        //Clear buffer
        while (true) {            
            byte[] data = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(data, data.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                break;
            }
        }
        
        sendDistanceVector();
        
        int timeoutCount = 0;
        
        while (true) {
            byte[] data = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(data, data.length);
            try {
                socket.receive(packet);
            } catch (IOException e) {
                System.out.println("Timeout recieving data.");
                timeoutCount++;
                if (timeoutCount == 1) {
                    //System.out.println("Ten consecutive timeouts, Exiting...");
                    System.out.println("Exiting...");
                    break;
                }
                continue;
            }
            timeoutCount = 0;
            int[] receivedDistanceVectorData = unpackData(packet.getData());
            int[] receivedDistanceVector = new int[] {
                receivedDistanceVectorData[1],
                receivedDistanceVectorData[2],
                receivedDistanceVectorData[3]                
            };
            
            System.out.println("Received distance vector from router " + ((char)(receivedDistanceVectorData[0] + 0x58)) + ": " + buildDistanceVectorString(receivedDistanceVector));
            if (bellmanFord(receivedDistanceVectorData[0], receivedDistanceVector)) {
                System.out.println("Distance vector on router " + ((char)(instanceNum + 0x58)) + " is updated to: ");
                System.out.println(buildDistanceVectorString(distanceVector));
                sendDistanceVector();
            } else {
                System.out.println("Distance vector on router " + ((char)(instanceNum + 0x58)) + " is not updated.");
            }
        }
        
    }  
    
    private static boolean bellmanFord(int receivedInstanceNum, int[] receivedDistanceVector) {
        boolean hasUpdated = false;
        for (int i = 0; i < 3; i++) {
            if (distanceVector[receivedInstanceNum] + receivedDistanceVector[i] < distanceVector[i]) {
                distanceVector[i] = distanceVector[receivedInstanceNum] + receivedDistanceVector[i];
                hasUpdated = true;
            }
        }
        return hasUpdated;
    }
    
    private static boolean allRoutersOnline() {
        for (int i = 0; i < 3; i++) {
            if (!routersOnline[i]) {
                return false;
            }
        }
        return true;
    }
    
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
    
    private static void printDistanceVector() {        
        System.out.println("Distance vector on Router " + ((char)(instanceNum + 0x58)) + " is: ");
        System.out.println(buildDistanceVectorString(distanceVector));
    }
    
    private static void sendData(byte[] data) throws IOException {        
        for (int i = 0; i < 3; i++) {
            if (ports[i] == port) {
                continue;
            }
            
            InetAddress address = InetAddress.getByName("127.0.0.1");
            
            DatagramPacket packet = new DatagramPacket(data, data.length, address, ports[i]);
            
            socket.send(packet);            
        }
    }
    
    private static void sendInitData() throws IOException {
        byte[] data = new byte[BUFFER_SIZE];
        packInitData(data);
        sendData(data);    
        
    }
    
    private static void sendDistanceVector() throws UnknownHostException, IOException {
        byte[] data = new byte[BUFFER_SIZE];
        packDistanceVector(data);
        sendData(data);
    }
    
    private static int[] unpackData(byte[] data) {
        int[] distanceVector = new int[4];
        for (int i = 0; i < 4; i++) {
            distanceVector[i] = data[i * 4 + 0] << 24 | data[i * 4 + 1] << 16 | data[i * 4 + 2] << 8 | data[i * 4 + 3];            
        }
        return distanceVector;
    }
    
    private static void packInitData(byte[] data) {
        byte ack = (byte)255;
        data[0] = (byte) 0;
        data[1] = (byte) 0;
        data[2] = (byte) 0;
        data[3] = ack;
        data[4] = (byte)(instanceNum >> 24);
        data[5] = (byte)(instanceNum >> 16);
        data[6] = (byte)(instanceNum >> 8);
        data[7] = (byte)(instanceNum >> 0);
        
    }
    
    private static void packDistanceVector(byte[] data) {
        data[0] = (byte)(instanceNum >> 24);
        data[1] = (byte)(instanceNum >> 16);
        data[2] = (byte)(instanceNum >> 8);
        data[3] = (byte)(instanceNum >> 0);
        
        for (int i = 0; i < 3; i++) {
            data[4 + i*4 + 0] = (byte)(distanceVector[i] >> 24);
            data[4 + i*4 + 1] = (byte)(distanceVector[i] >> 16);
            data[4 + i*4 + 2] = (byte)(distanceVector[i] >> 8);
            data[4 + i*4 + 3] = (byte)(distanceVector[i] >> 0);
        }
    }
    
    private static boolean trySetPort(int port) {
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException ex) {
            //Logger.getLogger(UDPVectorRouting.class.getName()).log(Level.SEVERE, null, ex);
            System.out.println("Port " + port + " in use.");
            return false;
        }
        try {
            socket.setSoTimeout(2000);
        } catch (SocketException ex) {
            //Logger.getLogger(UDPVectorRouting.class.getName()).log(Level.SEVERE, null, ex);
        }
        return true;
    }
    
    private static void startInstance() throws IOException {
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
            if (instanceNum == -1 && trySetPort(ports[i])) {
                //Set port num
                port = ports[i];
                
                //Set instance num
                instanceNum = i;  
                
            }
        }
        
        if (instanceNum == -1) {
            throw new IOException("Error creating instance.");
        }
        routersOnline = new boolean[3];
        routersOnline[instanceNum] = true;
        
        //Skip lines for other instances
        for (int i = 0; i < instanceNum; i++) {
            String trash = reader.readLine();
        }
        
        //Read table for correct line
        String[] line = reader.readLine().split("\\t");
        
        //Init routing table
        routingTable = new int[3][3];
        
        //Parse routing table
        for (int j = 0; j < 3; j++) {
            routingTable[instanceNum][j] = Integer.parseInt(line[j]);
        }       
        
        distanceVector = new int[3];
        //Deep copy instance row from routing table
        for (int i = 0; i < 3; i++) {
            distanceVector[i] = routingTable[instanceNum][i];
        }
    }
    
}
