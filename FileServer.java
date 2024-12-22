package serveur;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class FileServer {
    // private static String SERVER_IP = "localhost";
    private static int PORT = 1234;
    private static String SERVER_DIR = "./reception/";
    private static List<SlaveInfo> slaveList = new ArrayList<>();
    private static final int RECONNECT_DELAY = 5000; // 5 secondes entre les tentatives
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    public static void main(String[] args) {
        FileServer server = new FileServer();
        server.loadConfig();
        server.startServer();
    }

    public void handleClientRequest(Socket client) {
        try {
            System.out.println("Client connecté : " + client.getInetAddress());
            DataInputStream requeteClient = new DataInputStream(client.getInputStream());
            DataOutputStream requeteServeur = new DataOutputStream(client.getOutputStream());
            String command = requeteClient.readUTF();

            switch (command) {
                case "CONNECT":
                    handleConnect(requeteServeur, client);
                    break;
                case "UPLOAD":
                    handleUpload(requeteClient);
                    break;
                case "LIST":
                    handleList(requeteClient, requeteServeur);
                    break;
                case "DOWNLOAD_FILE":
                    handleDownloadFile(requeteClient, requeteServeur);
                    break;
                case "DOWNLOAD_DIR":
                    handleDownloadDir(requeteClient, requeteServeur);
                    break;
                case "DELETE":
                    handleDelete(requeteClient);
                    break;
                default:
                    requeteServeur.writeUTF("Commande inconnue");
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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

            PORT = Integer.parseInt(prop.getProperty("PORT", "1234"));
            SERVER_DIR = prop.getProperty("SERVER_DIR", "./reception/");

            // Créer le répertoire s'il n'existe pas
            new File(SERVER_DIR).mkdirs();

            // Charger la configuration des slaves
            int slaveCount = Integer.parseInt(prop.getProperty("SLAVE_SERVERS", "0"));
            for (int i = 1; i <= slaveCount; i++) {
                String slaveAddress = prop.getProperty("SLAVE_" + i + "_ADDRESS");
                int slavePort = Integer.parseInt(prop.getProperty("SLAVE_" + i + "_PORT"));
                slaveList.add(new SlaveInfo(slaveAddress, slavePort)); // mampiditra anle slive anaty list slive
            }

            System.out.println("Configuration chargée avec succès");
            System.out.println("Port du serveur: " + PORT);
            System.out.println("Répertoire de stockage: " + SERVER_DIR);
            System.out.println("Nombre de slaves configurés: " + slaveCount);
        } catch (IOException ex) {
            System.err.println("Erreur lors de la lecture du fichier de configuration.");
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            // System.out.println("FileServer démarré sur " + SERVER_IP + ":" + PORT);

            // Tester la connexion aux slaves avec plusieurs tentatives
            for (int attempt = 1; attempt <= MAX_RECONNECT_ATTEMPTS; attempt++) {
                System.out.println(
                        "\nTentative de connexion aux slaves (" + attempt + "/" + MAX_RECONNECT_ATTEMPTS + ")");
                testSlaveConnections();

                int connectedCount = (int) slaveList.stream().filter(s -> s.isConnected).count();
                if (connectedCount == slaveList.size()) {
                    System.out.println("Tous les slaves sont connectés!");
                    break;
                } else if (attempt < MAX_RECONNECT_ATTEMPTS) {
                    System.out.println("Nouvelle tentative dans " + (RECONNECT_DELAY / 1000) + " secondes...");
                    Thread.sleep(RECONNECT_DELAY);
                }
            }

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("\nNouveau client connecté : " + clientSocket.getInetAddress());
                new Thread(() -> handleClientRequest(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Erreur fatale du serveur principal:");
            e.printStackTrace();
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interruption pendant la tentative de reconnexion");
        }
    }

    private void testSlaveConnections() {
        System.out.println("Test de connexion aux slaves...");
        System.out.println("Nombre de slaves configurés: " + slaveList.size());

        for (SlaveInfo slave : slaveList) {
            System.out.println("Tentative de connexion au slave " + slave.ip + ":" + slave.port);
            try (Socket socket = new Socket(slave.ip, slave.port)) {
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());

                out.writeUTF("CONNECT");
                String response = in.readUTF();

                if ("CONNECTE".equals(response)) {
                    System.out.println("✓ Slave connecté avec succès: " + slave.ip + ":" + slave.port);
                    slave.isConnected = true;
                }
            } catch (IOException e) {
                System.out.println("✗ Échec de connexion au slave: " + slave.ip + ":" + slave.port);
                System.out.println("  Erreur: " + e.getMessage());
                slave.isConnected = false;
            }
        }

        int connectedSlaves = (int) slaveList.stream().filter(s -> s.isConnected).count();
        System.out.println("Résultat final: " + connectedSlaves + "/" + slaveList.size() + " slaves connectés");
    }

    private void handleConnect(DataOutputStream requeteServeur, Socket client) {
        try {
            requeteServeur.writeUTF("CONNECTE");
            System.out.println("Client connecté : " + client.getInetAddress());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleUpload(DataInputStream requeteClient) {
        try {
            boolean isDirectory = requeteClient.readBoolean();
            File destination = new File(SERVER_DIR);
            if (isDirectory) {
                receiveDirectory(requeteClient, destination);
            } else {
                receiveFile(requeteClient, destination);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void receiveFile(DataInputStream data, File destination) throws IOException {
        String fileName = data.readUTF();
        long fileSize = data.readLong();

        System.out.println("Réception du fichier: " + fileName + " (Taille: " + fileSize + " bytes)");

        // Lire tout le fichier en mémoire
        byte[] fileData = new byte[(int) fileSize];
        data.readFully(fileData);
        System.out.println("Fichier reçu avec succès.");

        if (slaveList.isEmpty()) {
            System.out.println("ERREUR: Aucun slave serveur n'est configuré");
            return;
        }

        // Diviser le fichier en parties égales
        int slaveCount = slaveList.size();
        int partSize = (int) (fileSize / slaveCount);
        int lastPartSize = (int) (fileSize - (long) partSize * (slaveCount - 1));

        // Envoyer chaque partie à un slave
        for (int i = 0; i < slaveCount; i++) {
            SlaveInfo slave = slaveList.get(i);
            int currentPartSize = (i == slaveCount - 1) ? lastPartSize : partSize;

            if (!slave.isConnected) {
                System.out.println("ERREUR: Slave " + slave.ip + ":" + slave.port + " non connecté");
                continue;
            }

            byte[] partData = new byte[currentPartSize];
            System.arraycopy(fileData, i * partSize, partData, 0, currentPartSize);

            sendPartToSlave(slave, fileName, partData, i + 1);
        }

        System.out.println("Distribution du fichier terminée.");
    }

    private void sendPartToSlave(SlaveInfo slave, String fileName, byte[] partData, int partNumber) {
        try (Socket slaveSocket = new Socket(slave.ip, slave.port);
                DataOutputStream slaveOut = new DataOutputStream(slaveSocket.getOutputStream())) {

            slaveOut.writeUTF("UPLOAD_PART");
            slaveOut.writeUTF(fileName);
            slaveOut.writeInt(partNumber); // Numéro de la partie
            slaveOut.writeLong(partData.length);
            slaveOut.write(partData);

            System.out.println("Partie " + partNumber + " envoyée avec succès au slave " + slave.ip + ":" + slave.port);
        } catch (IOException e) {
            System.out
                    .println("ERREUR lors de l'envoi au slave " + slave.ip + ":" + slave.port + ": " + e.getMessage());
        }
    }

    private void receiveDirectory(DataInputStream data, File destination) throws IOException {
        String dirName = data.readUTF();
        File currentDir = new File(destination, dirName);
        currentDir.mkdirs();

        int itemCount = data.readInt();
        for (int i = 0; i < itemCount; i++) {
            boolean isFile = data.readBoolean();
            if (isFile) {
                receiveFile(data, currentDir);
            } else {
                receiveDirectory(data, currentDir);
            }
        }
    }

    private void handleList(DataInputStream requeteClient, DataOutputStream requeteServeur) throws IOException {
        // Initialiser le compteur total de fichiers
        int totalFiles = 0;
        List<String> allFiles = new ArrayList<>();

        // Collecter les fichiers de chaque slave
        for (SlaveInfo slave : slaveList) {
            if (!slave.isConnected)
                continue;

            try (Socket slaveSocket = new Socket(slave.ip, slave.port)) {
                DataOutputStream slaveOut = new DataOutputStream(slaveSocket.getOutputStream());
                DataInputStream slaveIn = new DataInputStream(slaveSocket.getInputStream());

                // Demander la liste au slave
                slaveOut.writeUTF("LIST");

                // Lire le nombre de fichiers du slave
                int slaveFileCount = slaveIn.readInt();

                // Lire chaque nom de fichier
                for (int i = 0; i < slaveFileCount; i++) {
                    String fileName = slaveIn.readUTF();
                    // Ne garder que le nom de base du fichier (sans .partX)
                    String baseFileName = fileName.replaceFirst("\\.part[0-9]+$", "");
                    if (!allFiles.contains(baseFileName)) {
                        allFiles.add(baseFileName);
                    }
                }
            } catch (IOException e) {
                System.err.println("Erreur de connexion au slave " + slave.ip + ":" + slave.port);
            }
        }

        // Envoyer le nombre total de fichiers uniques au client
        requeteServeur.writeInt(allFiles.size());

        // Envoyer la liste des fichiers uniques au client
        for (String fileName : allFiles) {
            requeteServeur.writeUTF(fileName);
            requeteServeur.writeBoolean(true); // Tous sont des fichiers
        }
    }

    private void handleDownloadFile(DataInputStream requeteClient, DataOutputStream requeteServeur) throws IOException {
        String fileName = requeteClient.readUTF();
        // Récupérer les parties du fichier depuis les slaves et les combiner
        assembleFileFromSlaves(fileName, requeteServeur);
    }

    private void assembleFileFromSlaves(String fileName, DataOutputStream clientOutput) throws IOException {
        // Première étape : vérifier la taille totale
        long totalSize = 0;
        List<File> partFiles = new ArrayList<>();

        for (int i = 0; i < slaveList.size(); i++) {
            SlaveInfo slave = slaveList.get(i);
            if (!slave.isConnected)
                continue;

            try (Socket slaveSocket = new Socket(slave.ip, slave.port)) {
                DataOutputStream out = new DataOutputStream(slaveSocket.getOutputStream());
                DataInputStream in = new DataInputStream(slaveSocket.getInputStream());

                out.writeUTF("DOWNLOAD_PART");
                out.writeUTF(fileName);
                out.writeInt(i + 1);

                String response = in.readUTF();
                if ("OK".equals(response)) {
                    long partSize = in.readLong();
                    totalSize += partSize;

                    // Sauvegarder temporairement la partie
                    File tempPart = new File(SERVER_DIR, fileName + ".part" + (i + 1));
                    try (FileOutputStream fos = new FileOutputStream(tempPart)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    partFiles.add(tempPart);
                }
            }
        }

        // Envoyer les informations du fichier au client
        clientOutput.writeUTF("OK");
        clientOutput.writeUTF(fileName);
        clientOutput.writeLong(totalSize);

        // Assembler et envoyer le fichier
        for (File partFile : partFiles) {
            try (FileInputStream fis = new FileInputStream(partFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    clientOutput.write(buffer, 0, bytesRead);
                }
            }
            partFile.delete();
        }
    }

    private void handleDownloadDir(DataInputStream requeteClient, DataOutputStream requeteServeur) throws IOException {
        String dirName = requeteClient.readUTF();
        File directory = new File(SERVER_DIR, dirName);
        sendDirectory(requeteServeur, directory);
    }

    private void sendDirectory(DataOutputStream response, File directory) throws IOException {
        if (!directory.exists() || !directory.isDirectory()) {
            response.writeUTF("ERREUR: Dossier introuvable.");
            return;
        }

        response.writeUTF("OK");
        response.writeUTF(directory.getName());

        File[] files = directory.listFiles();
        response.writeInt(files != null ? files.length : 0);

        if (files != null) {
            for (File file : files) {
                response.writeBoolean(file.isFile());
                if (file.isFile()) {
                    sendFile(response, file);
                } else {
                    sendDirectory(response, file);
                }
            }
        }
    }

    private void sendFile(DataOutputStream response, File file) throws IOException {
        if (!file.exists() || !file.isFile()) {
            response.writeUTF("ERREUR: Fichier introuvable.");
            return;
        }

        response.writeUTF("OK");
        response.writeUTF(file.getName());
        response.writeLong(file.length());

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                response.write(buffer, 0, bytesRead);
            }
        }
    }

    private void handleDelete(DataInputStream requeteClient) {
        try {
            String filename = requeteClient.readUTF(); // fichier a supprimer ozy client
            System.out.println(filename + " est " + filename);
            // for(SlaveInfo s : slaveList) {
            for (int i = 1; i < slaveList.size() + 1; i++) {
                SlaveInfo s = slaveList.get(i - 1);
                if (!s.isConnected) {
                    continue;
                }
                try {
                    Socket slave = new Socket(s.ip, s.port);
                    DataOutputStream requeteServeur = new DataOutputStream(slave.getOutputStream());
                    DataInputStream reponseSlave = new DataInputStream(slave.getInputStream());

                    requeteServeur.writeUTF("DELETE");
                    requeteServeur.writeUTF(filename + ".part" + i);

                    String reponse = reponseSlave.readUTF();
                    if (reponse.equals("OK")) {
                        System.out.println("fichier supprimer sur le slave : " + s.ip);
                    } else if (reponse.equals("ERREUR")) {
                        System.out.println("Erreur lors de la suppression sur le slave : " + s.ip);
                    }

                } catch (Exception e) {
                    System.err.println("Échec de la connexion au slave : " + s.ip);
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteFileOrDir(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteFileOrDir(f);
                }
            }
        }
        file.delete();
    }

    static class SlaveInfo {
        String ip;
        int port;
        boolean isConnected;
        int reconnectAttempts;

        SlaveInfo(String ip, int port) {
            this.ip = ip;
            this.port = port;
            this.isConnected = false;
            this.reconnectAttempts = 0;
        }
    }
}