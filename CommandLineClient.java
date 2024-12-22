package client;

import java.io.File;
import java.util.Scanner;
import java.io.*;
import java.net.*;
import java.util.List;
import java.util.ArrayList;

public class CommandLineClient {
    private FileClient client;
    private boolean isConnected;
    private Scanner scanner;

    public CommandLineClient() {
        this.scanner = new Scanner(System.in);
        this.isConnected = false;
    }

    public void start() {
        System.out.println("Client DNT - Tapez 'help' pour voir les commandes disponibles");

        while (true) {
            System.out.print("DNT> ");
            String input = scanner.nextLine().trim();
            String[] parts = input.split("\\s+", 2);
            String command = parts[0].toLowerCase();
            String args = parts.length > 1 ? parts[1] : "";

            try {
                switch (command) {
                    case "connect":
                        handleConnect(args);
                        break;
                    case "put":
                        if (checkConnection()) {
                            handlePut(args);
                        }
                        break;
                    case "get":
                        if (checkConnection()) {
                            handleGet(args);
                        }
                        break;
                    case "delete":
                        if (checkConnection()) {
                            handleDelete(args);
                        }
                        break;
                    case "ls":
                        if (checkConnection()) {
                            handleList();
                        }
                        break;
                    case "help":
                        showHelp();
                        break;
                    case "exit":
                        System.out.println("Au revoir!");
                        return;
                    default:
                        System.out.println("Commande inconnue. Tapez 'help' pour voir les commandes disponibles.");
                }
            } catch (Exception e) {
                System.out.println("Erreur: " + e.getMessage());
            }
        }
    }

    private void handleList() {
        List<String> files = client.listFiles();
        System.out.println("Contenu du serveur (" + files.size() + " éléments):");
        for (String file : files) {
            System.out.println("  " + file);
        }
    }

    private void handleDelete(String fileName) {
        if (fileName.isEmpty()) {
            System.out.println("Usage: delete <nom_fichier>");
            return;
        }

        System.out.println("Suppression de : " + fileName);
        client.deleteFromServer(fileName);
    }

    private void handleConnect(String args) {
        try {
            String[] connectionArgs = args.split("\\s+");
            if (connectionArgs.length != 2) {
                System.out.println("Usage: connect <address> <port>");
                return;
            }
            String address = connectionArgs[0];
            int port = Integer.parseInt(connectionArgs[1]);

            FileClient testClient = new FileClient(address, port); // Crée une instance temporaire pour tester la
                                                                   // connexion
            this.client = testClient;
            if (this.client.connectToServer()) {
                this.isConnected = true;
            } else {
                throw new Exception("impossible de se connecter");
            }

            System.out.println("Connecté avec succès au serveur " + address + ":" + port);
        } catch (NumberFormatException e) {
            System.out.println("Le port doit être un nombre valide");
        } catch (Exception e) { // Capture toutes les autres exceptions
            System.out.println("Erreur de connexion : " + e.getMessage());
        }
    }

    private void handlePut(String path) {
        if (path.isEmpty()) {
            System.out.println("Usage: upload <chemin_fichier>");
            return;
        }

        File file = new File(path);
        if (!file.exists()) {
            System.out.println("Le fichier ou dossier n'existe pas: " + path);
            return;
        }

        System.out.println("Upload en cours de: " + path);
        client.uploadFileOrDir(file);
    }

    private void handleGet(String args) {
        String[] downloadArgs = args.split("\\s+");
        if (downloadArgs.length != 2) {
            System.out.println("Usage: download <nom_fichier> <destination>");
            return;
        }

        String fileName = downloadArgs[0];
        String destination = downloadArgs[1];
        File destinationDir = new File(destination);

        if (!destinationDir.exists()) {
            destinationDir.mkdirs();
        }

        System.out.println("Téléchargement de: " + fileName);
        client.downloadFromServer(fileName, destinationDir);
    }

    private boolean checkConnection() {
        if (!isConnected) {
            System.out.println("Vous devez d'abord vous connecter. Utilisez 'connect <address> <port>'");
            return false;
        }
        return true;
    }

    private void showHelp() {
        System.out.println("Commandes disponibles:");
        System.out.println("  connect <address> <port>  - Se connecter au serveur");
        System.out.println("  put <chemin>          - Uploader un fichier ou dossier");
        System.out.println("  get <nom> <dest>    - Télécharger un fichier ou dossier");
        System.out.println("  delete <nom>            - Supprimer un fichier ou dossier");
        System.out.println("  ls                       - Lister les fichiers sur le serveur");
        System.out.println("  help                     - Afficher cette aide");
        System.out.println("  exit                     - Quitter le programme");
    }

    public static void main(String[] args) {
        CommandLineClient comClient = new CommandLineClient();
        comClient.start();

    }
}
