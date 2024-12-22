package serveur;

import java.io.*;
import java.net.*;
import java.util.Properties;
import java.util.*;

public class SlaveServer {
    private static int PORT;
    private static String SERVER_DIR;
    private static int slaveNumber;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java SlaveServer <slave-number>"); 
            System.err.println("Example: java SlaveServer 1");
            System.exit(1);
        }
        try {
            slaveNumber = Integer.parseInt(args[0]);
            if (slaveNumber < 1) {
                throw new NumberFormatException();
            }    
        } 
        catch (NumberFormatException e) {
            System.err.println("Le numéro du slave doit être un entier positif");
            System.exit(1);
        }

        SlaveServer server = new SlaveServer();
        server.loadConfig();
        server.startServer();
    }

    private void loadConfig() {
        File configFile = new File("config.txt");
        if (!configFile.exists()) {
            System.err.println("Erreur: Le fichier config.txt n'existe pas");
            System.exit(1);
        }

        try (InputStream input = new FileInputStream(configFile)) {
            Properties prop = new Properties();
            prop.load(input);

            // Vérifier que le slave est configuré
            int totalSlaves = Integer.parseInt(prop.getProperty("SLAVE_SERVERS", "0"));
            if (slaveNumber > totalSlaves) {
                System.err.println("Erreur: Le slave " + slaveNumber + " n'est pas configuré");
                System.err.println("Nombre total de slaves configurés: " + totalSlaves);
                System.exit(1);
            }

            PORT = Integer.parseInt(prop.getProperty("SLAVE_" + slaveNumber + "_PORT"));
            SERVER_DIR = prop.getProperty("SLAVE_" + slaveNumber + "_DIR", 
                                        "./slave_reception_" + slaveNumber + "/");

            // Créer le répertoire s'il n'existe pas
            new File(SERVER_DIR).mkdirs();

            System.out.println("Configuration du Slave " + slaveNumber + " chargée avec succès");
            System.out.println("Port d'écoute: " + PORT);
            System.out.println("Répertoire de stockage: " + SERVER_DIR);
        } catch (IOException ex) {
            System.err.println("Erreur lors de la lecture du fichier de configuration.");
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("\nSlaveServer " + slaveNumber + " démarré sur le port " + PORT);

            while (true) {
                Socket client = serverSocket.accept();
                System.out.println("\nClient connecté: " + client.getInetAddress());
                new Thread(() -> handleClientRequest(client)).start();
            }
        } catch (IOException e) {
            System.err.println("Erreur fatale du serveur slave " + slaveNumber + ":");
            e.printStackTrace();
            System.exit(1);
        }
    }

private void handleClientRequest(Socket client) {
        try {
            System.out.println("Client connecté : " + client.getInetAddress());
            DataInputStream requeteClient = new DataInputStream(client.getInputStream());
            DataOutputStream requeteServeur = new DataOutputStream(client.getOutputStream());
            String command = requeteClient.readUTF();
            
            switch(command) {
                case "CONNECT":
                    handleConnect(requeteServeur, client);
                    break;
                case "UPLOAD_PART":
                    handleUploadPart(requeteClient);
                    break;
                case "DOWNLOAD_PART":
                    handleDownloadPart(requeteClient, requeteServeur);
                    break;
                case "LIST":
                    handleList(requeteServeur);
                    break;
                case "DELETE":
                    handleDelete(requeteClient,requeteServeur);
                    break;
                default:
                    requeteServeur.writeUTF("Commande inconnue");
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleConnect(DataOutputStream requeteServeur, Socket client) {
        try {
            requeteServeur.writeUTF("CONNECTE");
            System.out.println("Client connecté : " + client.getInetAddress());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleUploadPart(DataInputStream data) throws IOException {
        String fileName = data.readUTF();
        int partNumber = data.readInt();
        long partSize = data.readLong();
        
        File partFile = new File(SERVER_DIR, fileName + ".part" + partNumber);
        try (FileOutputStream fos = new FileOutputStream(partFile)) {
            byte[] buffer = new byte[4096];
            long remaining = partSize;
            while (remaining > 0) {
                int read = data.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read == -1) break;
                fos.write(buffer, 0, read);
                remaining -= read;
            }
        }
        System.out.println("Partie " + partNumber + " du fichier " + fileName + " reçue");
    }

    private void handleDownloadPart(DataInputStream requeteClient, DataOutputStream requeteServeur) throws IOException {
        String fileName = requeteClient.readUTF();
        int partNumber = requeteClient.readInt();
        File partFile = new File(SERVER_DIR, fileName + ".part" + partNumber);
        
        if (!partFile.exists()) {
            requeteServeur.writeUTF("ERROR");
            return;
        }
        
        requeteServeur.writeUTF("OK");
        requeteServeur.writeLong(partFile.length());
        
        try (FileInputStream fis = new FileInputStream(partFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                requeteServeur.write(buffer, 0, bytesRead);
            }
        }
    }

    private void handleDelete(DataInputStream requeteServeur,DataOutputStream requeteSlave){
        try{
            String filename = requeteServeur.readUTF();
            File fileToDelete = new File(SERVER_DIR,filename);
            if(fileToDelete.exists() && fileToDelete.delete()){
                requeteSlave.writeUTF("OK");
            }else{
                requeteSlave.writeUTF("ERREUR");
            }
        }
        catch (IOException e) {
             e.printStackTrace();
        }
    }

private void handleList(DataOutputStream requeteServeur) throws IOException {
    File directory = new File(SERVER_DIR);
    File[] files = directory.listFiles();
    
    // Filtrer pour ne garder que les fichiers .partX
    List<File> partFiles = new ArrayList<>();
    if (files != null) {
        for (File file : files) {
            if (file.getName().matches(".*\\.part[0-9]+$")) {
                partFiles.add(file);
            }
        }
    }
    
    // Envoyer le nombre de fichiers
    requeteServeur.writeInt(partFiles.size());
    
    // Envoyer chaque nom de fichier
    for (File file : partFiles) {
        requeteServeur.writeUTF(file.getName());
    }
}
}