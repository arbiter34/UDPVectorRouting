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
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author alperst
 */
public class UDPVectorRouting {

  
    private static final int BUFFER_LENGTH = 1024;
    
    private static int port;
    
    private static int[] ports;
    
    private static int instanceNum = -1;
    
    private static int[] routingTable;
    
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
        
        System.out.println("Instance " + ((char)(instanceNum + 0x58)) + " started.");
        
        byte[] data = new byte[BUFFER_LENGTH];
        DatagramPacket packet = new DatagramPacket(data, data.length);
        
        socket.receive(packet);
        
    }  
    
    private static boolean trySetPort(int port) {
        try {
            socket = new DatagramSocket(port);
        } catch (SocketException ex) {
            Logger.getLogger(UDPVectorRouting.class.getName()).log(Level.SEVERE, null, ex);
            return false;
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
            if (trySetPort(ports[i])) {
                //Set port num
                port = ports[i];
                
                //Set instance num
                instanceNum = i;  
            }
        }
        
        if (instanceNum == -1) {
            throw new IOException("Error creating instance.");
        }
        
        //Skip lines for other instances
        for (int i = 0; i < instanceNum; i++) {
            String trash = reader.readLine();
        }
        
        //Read table for correct line
        String[] line = reader.readLine().split("\\t");
        
        //Init routing table
        routingTable = new int[3];
        
        //Parse routing table
        for (int j = 0; j < 3; j++) {
            routingTable[j] = Integer.parseInt(line[j]);
        }               
    }
    
}
