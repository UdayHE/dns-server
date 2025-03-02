import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class Main {
  public static void main(String[] args){
    System.out.println("Logs from your program will appear here!");

     try(DatagramSocket serverSocket = new DatagramSocket(2053)) {
       while(true) {
         final byte[] buf = new byte[512];
         final DatagramPacket packet = new DatagramPacket(buf, buf.length);
         serverSocket.receive(packet);

         System.out.println("Received data");

         DNSMessage request = new DNSMessage(packet.getData());
         byte[] responseData = DNSMessage.createResponse(request);
         final DatagramPacket packetResponse = new DatagramPacket(responseData, responseData.length, packet.getSocketAddress());
         serverSocket.send(packetResponse);
       }
     } catch (IOException e) {
         System.out.println("IOException: " + e.getMessage());
     }
  }
}
