import dns.Server;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger log = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        log.log(Level.INFO, "Starting DNS-Server...");
        Server server = new Server();
        server.start(args);
    }

}