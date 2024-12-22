package client;

import java.io.*;
import java.net.*;
import java.util.List;
import java.util.ArrayList;

public class FileClient {
    private String serverAddress;
    private int serverPort;
    private File file;

    public FileClient(String address, int port) {
        this.serverAddress = address;
        this.serverPort = port;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public void uploadFileOrDir(File fileToUpload) {
        try (Socket socket = new Socket(serverAddress, serverPort);
             DataOutputStream request = new DataOutputStream(socket.getOutputStream());) // mandefa flux
        {

            request.writeUTF("UPLOAD");
            request.writeBoolean(fileToUpload.isDirectory());

            if (fileToUpload.isDirectory()) {
                uploadDirectory(request, fileToUpload);
            } else {
                uploadFile(request, fileToUpload);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void uploadDirectory(DataOutputStream request, File directory) throws IOException {
        request.writeUTF(directory.getName());
        File[] files = directory.listFiles();
        request.writeInt(files != null ? files.length : 0);

        if (files != null) {
            for (File file : files) {
                request.writeBoolean(file.isFile());
                if (file.isFile()) {
                    uploadFile(request, file);
                } else {
                    uploadDirectory(request, file);
                }
            }
        }
    }

    private void uploadFile(DataOutputStream request, File file) throws IOException {
        request.writeUTF(file.getName());
        request.writeLong(file.length());

        try (FileInputStream fis = new FileInputStream(file)) { // mamaky anle donnees
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                request.write(buffer, 0, bytesRead);
            }
        }
    }

    public boolean connectToServer() throws IOException {
        try {
            Socket socket = new Socket(serverAddress, serverPort);
            DataOutputStream request = new DataOutputStream(socket.getOutputStream());
            DataInputStream reponse = new DataInputStream(socket.getInputStream());
            request.writeUTF("CONNECT");
            String status = reponse.readUTF();
            if (status.equals("CONNECTE")) {
                System.out.println("Bien connecter au serveur");
                return true;
            } else {
                throw new IOException("Impossible de se connecter");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void downloadFromServer(String fileName, File downloadDirectory) {
        try (Socket socket = new Socket(serverAddress, serverPort);
                DataOutputStream request = new DataOutputStream(socket.getOutputStream());
                DataInputStream response = new DataInputStream(socket.getInputStream())) {

            // Vérifier si c'est un dossier ou un fichier sur le serveur
            request.writeUTF("LIST");
            int count = response.readInt();
            boolean isDirectory = false;

            // Parcourir la liste des fichiers pour vérifier si c'est un dossier
            for (int i = 0; i < count; i++) {
                String name = response.readUTF();
                boolean isFile = response.readBoolean();
                if (name.equals(fileName)) {
                    isDirectory = !isFile;
                    break;
                }
            }

            // Nouvelle connexion pour le téléchargement
            try (Socket downloadSocket = new Socket(serverAddress, serverPort);
                    DataOutputStream downloadRequest = new DataOutputStream(downloadSocket.getOutputStream()); // madenfa
                    DataInputStream downloadResponse = new DataInputStream(downloadSocket.getInputStream())) { // mamaky

                if (isDirectory) {
                    downloadRequest.writeUTF("DOWNLOAD_DIR");
                    downloadRequest.writeUTF(fileName);
                    downloadDirectory(downloadResponse, downloadDirectory);
                } else {
                    downloadRequest.writeUTF("DOWNLOAD_FILE");
                    downloadRequest.writeUTF(fileName);
                    downloadFile(downloadResponse, downloadDirectory);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void downloadDirectory(DataInputStream response, File parentDir) throws IOException {
        String status = response.readUTF();
        if (status.startsWith("ERREUR")) {
            throw new IOException(status);
        }

        String dirName = response.readUTF();
        File newDir = new File(parentDir, dirName);
        newDir.mkdirs();

        int fileCount = response.readInt();
        for (int i = 0; i < fileCount; i++) {
            boolean isFile = response.readBoolean();
            if (isFile) {
                downloadFile(response, newDir);
            } else {
                downloadDirectory(response, newDir);
            }
        }
        System.out.println("Fichier reçu : " + parentDir);
    }

    private void downloadFile(DataInputStream response, File downloadDir) throws IOException {
        String status = response.readUTF();
        if (status.startsWith("ERREUR")) {
            throw new IOException(status);
        }

        String name = response.readUTF();
        long size = response.readLong();

        File outputFile = new File(downloadDir, name);

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[4096];
            long remaining = size;
            int read;

            while (remaining > 0 && (read = response.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                fos.write(buffer, 0, read);
                remaining -= read;
            }
        }
        System.out.println("Fichier reçu : " + downloadDir);

    }

    public void deleteFromServer(String fileName) {
        try (Socket socket = new Socket(serverAddress, serverPort);
                DataOutputStream request = new DataOutputStream(socket.getOutputStream())) {

            request.writeUTF("DELETE");
            request.writeUTF(fileName);

            // System.out.println("Suppression effectuée : " + fileName);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<String> listFiles() {
        List<String> list = new ArrayList<>();
        try (Socket socket = new Socket(serverAddress, serverPort);
                DataOutputStream request = new DataOutputStream(socket.getOutputStream());
                DataInputStream response = new DataInputStream(socket.getInputStream())) {

            request.writeUTF("LIST");
            int count = response.readInt();

            for (int i = 0; i < count; i++) {
                String fileName = response.readUTF();
                boolean isFile = response.readBoolean();
                list.add((isFile ? "[F] " : "[D] ") + fileName);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }

}