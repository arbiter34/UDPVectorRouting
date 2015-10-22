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
        
        sendRoutingTable();
        
        while (true) {
            byte[] data = new byte[BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(data, data.length);
            socket.receive(packet);
            int[] distanceVector = unpackData(packet.getData());
        }
        
    }  
    
    private static boolean allRoutersOnline() {
        for (int i = 0; i < 3; i++) {
            if (!routersOnline[i]) {
                return false;
            }
        }
        return true;
    }
    
    private static void printDistanceVector() {
        String out = "<";
        String delimiter = "";
        for (int i = 0; i < 3; i++) {
            out += delimiter + routingTable[instanceNum][i];
            delimiter = ", ";
        }
        out += ">";
        System.out.println("Distance vector on Router " + ((char)(instanceNum + 0x58)) + " is: ");
        System.out.println(out);
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
    
    private static void sendRoutingTable() throws UnknownHostException, IOException {
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
            data[4 + i*4 + 0] = (byte)(routingTable[instanceNum][i] >> 24);
            data[4 + i*4 + 1] = (byte)(routingTable[instanceNum][i] >> 16);
            data[4 + i*4 + 2] = (byte)(routingTable[instanceNum][i] >> 8);
            data[4 + i*4 + 3] = (byte)(routingTable[instanceNum][i] >> 0);
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
    }
    
}
