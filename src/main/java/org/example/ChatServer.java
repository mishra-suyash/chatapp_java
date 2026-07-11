package org.example;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.eclipse.paho.client.mqttv3.*;

import java.util.concurrent.CompletableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {

    private static final int PORT = 12345;
    private static Key SECRET_KEY;
    private static final Map<String, String> users = new HashMap<>();
    private static Set<PrintWriter> clientWriters = new HashSet<>();
    private static final Logger logger = Logger.getLogger(ChatServer.class.getName());
    private static final Logger messageLogger = Logger.getLogger("MessageLogger");
    private static final Logger actionLogger = Logger.getLogger("ActionLogger");

    private static final String MQTT_BROKER = "tcp://localhost:1883";
    private static final String BROADCAST_TOPIC = "chat/broadcast";
    private MqttClient mqttClient;

    private JFrame frame;
    private JButton startButton;
    private JButton stopButton;
    private JButton addUserButton;
    private JButton removeUserButton;
    private JButton sendImageButton;
    private JTextArea logArea;
    private JTextField userField;
    private JPasswordField passwordField;
    private boolean isRunning = false;

    public ChatServer() {
        setupLogger();
        setupMessageLogger();
        setupActionLogger();
        setupGUI();
        initUsers();
        new Thread(() -> {
            try {
                mqttClient = new MqttClient(MQTT_BROKER, MqttClient.generateClientId());
                mqttClient.connect();
                mqttClient.subscribe(BROADCAST_TOPIC, this::onMessageReceived);
                System.out.println("Connected to MQTT broker and subscribed to broadcast topic.");
            } catch (MqttException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    private void setupGUI() {
        frame = new JFrame("Chat Server");
        frame.setSize(1200, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        frame.add(scrollPane, BorderLayout.CENTER);

        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout());

        userField = new JTextField(15);
        passwordField = new JPasswordField(15);

        startButton = new JButton("Start Server");
        stopButton = new JButton("Stop Server");
        addUserButton = new JButton("Add User");
        removeUserButton = new JButton("Remove User");
        sendImageButton = new JButton("Send Image");

        panel.add(new JLabel("Username:"));
        panel.add(userField);
        panel.add(new JLabel("Password:"));
        panel.add(passwordField);
        panel.add(addUserButton);
        panel.add(removeUserButton);
        panel.add(startButton);
        panel.add(stopButton);
        panel.add(sendImageButton);

        frame.add(panel, BorderLayout.SOUTH);

        startButton.addActionListener(new StartServerAction());
        stopButton.addActionListener(new StopServerAction());
        addUserButton.addActionListener(new AddUserAction());
        removeUserButton.addActionListener(new RemoveUserAction());
        sendImageButton.addActionListener(new SendImageAction());

        frame.setVisible(true);
    }

    private void onMessageReceived(String topic, MqttMessage message) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> handleMessage(topic, message, executor));
    }

    private void handleMessage(String topic, MqttMessage message, ExecutorService executor) {
        System.out.println("Received Broadcast: " + new String(message.getPayload()));
        System.out.println("Received: " + message);
        String finalMessage = "" + message;
        System.out.println(finalMessage);
        if (finalMessage.startsWith("LOGIN ")) {
            String[] credentials = finalMessage.split(" ");
            if (credentials.length == 3 && authenticate(credentials[1], credentials[2])) {
                String username = credentials[1];
                System.out.println(username);
                String token = generateToken(username);
                System.out.println("hello" + token);
                logger.info("MQTT: User '" + username + "' logged in successfully.");
                sendBroadcastMessage("LOGIN_SUCCESS " + username + " " + token);
                logger.info("MQTT: User '" + username + "' logged in successfully.");
            } else {
                sendBroadcastMessage("LOGIN_FAILED");
                logger.warning("MQTT: Failed login attempt for username: '" + credentials[1] + "'.");
            }
            executor.shutdown();
        } else {
            String[] parts = finalMessage.split(" ", 3);
            String username = parts[1];
            if (parts.length == 2 && validateToken(parts[0])) {
                if (parts[1].startsWith("SEND_IMAGE")) {
                } else {
                    sendBroadcastMessage(username + " " + parts[2]);
                }
            }
        }
    }

    public void sendBroadcastMessage(String message) {
        try {
            mqttClient.publish(BROADCAST_TOPIC, new MqttMessage(message.getBytes()));
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private boolean authenticate(String username, String password) {
        return users.containsKey(username) && users.get(username).equals(password);
    }

    private String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .signWith(SECRET_KEY)
                .compact();
    }

    private boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(SECRET_KEY)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    private void initUsers() {
        users.put("user1", "password1");
        users.put("user2", "password2");
        users.put("a", "a");
        users.put("b", "b");
    }

    private void setupLogger() {
        try {
            FileHandler fileHandler = new FileHandler("login.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
            logger.setUseParentHandlers(true);
        } catch (IOException e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
    }

    private void setupMessageLogger() {
        try {
            FileHandler messageHandler = new FileHandler("messages.log", true);
            messageHandler.setFormatter(new SimpleFormatter());
            messageLogger.addHandler(messageHandler);
            messageLogger.setUseParentHandlers(false);
        } catch (IOException e) {
            System.err.println("Failed to initialize message logger: " + e.getMessage());
        }
    }

    private void setupActionLogger() {
        try {
            FileHandler actionHandler = new FileHandler("actions.log", true);
            actionHandler.setFormatter(new SimpleFormatter());
            actionLogger.addHandler(actionHandler);
            actionLogger.setUseParentHandlers(false);
        } catch (IOException e) {
            System.err.println("Failed to initialize action logger: " + e.getMessage());
        }
    }

    private SecretKey generateSecretKey() throws NoSuchAlgorithmException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA256");
        keyGenerator.init(256);
        return keyGenerator.generateKey();
    }

    private void startServer() {
        try {
            if (!isRunning) {
                SECRET_KEY = generateSecretKey();
                logArea.append("Chat server started...\n");
                actionLogger.info("Chat server started.");

                new Thread(() -> {
                    try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                        isRunning = true;
                        logArea.append("Generated Secret Key: " + Base64.getEncoder().encodeToString(SECRET_KEY.getEncoded()) + "\n");

                        while (isRunning) {
                            new ClientHandler(serverSocket.accept()).start();
                        }
                    } catch (IOException e) {
                        logArea.append("Server error: " + e.getMessage() + "\n");
                        actionLogger.warning("Server error: " + e.getMessage());
                    }
                }).start();
            }
        } catch (NoSuchAlgorithmException e) {
            logArea.append("Failed to generate secret key: " + e.getMessage() + "\n");
            actionLogger.severe("Failed to generate secret key: " + e.getMessage());
        }
    }

    private void stopServer() {
        isRunning = false;
        logArea.append("Chat server stopped.\n");
        actionLogger.info("Chat server stopped.");
    }

    private void addUser(String username, String password) {
        if (!users.containsKey(username)) {
            users.put(username, password);
            logArea.append("User '" + username + "' added.\n");
            actionLogger.info("User '" + username + "' added.");
        } else {
            logArea.append("User '" + username + "' already exists.\n");
            actionLogger.warning("Failed to add user '" + username + "' (already exists).");
        }
    }

    private void removeUser(String username) {
        if (users.remove(username) != null) {
            logArea.append("User '" + username + "' removed.\n");
            actionLogger.info("User '" + username + "' removed.");
        } else {
            logArea.append("User '" + username + "' does not exist.\n");
            actionLogger.warning("Failed to remove user '" + username + "' (does not exist).");
        }
    }

    private class StartServerAction implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            startServer();
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
        }
    }

    private class StopServerAction implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            stopServer();
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        }
    }

    private class AddUserAction implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            String username = userField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();

            if (!username.isEmpty() && !password.isEmpty()) {
                addUser(username, password);
                userField.setText("");
                passwordField.setText("");
            } else {
                logArea.append("Please enter both username and password.\n");
                actionLogger.warning("Failed to add user (username/password empty).");
            }
        }
    }

    private class RemoveUserAction implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            String username = userField.getText().trim();
            if (!username.isEmpty()) {
                removeUser(username);
                userField.setText("");
            } else {
                logArea.append("Please enter a username.\n");
                actionLogger.warning("Failed to remove user (username empty).");
            }
        }
    }

    private class SendImageAction implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int returnValue = fileChooser.showOpenDialog(frame);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                String filePath = selectedFile.getAbsolutePath();
                broadcastImage(filePath);
            }
        }
    }

    private void broadcastImage(String filePath) {
        synchronized (clientWriters) {
            for (PrintWriter writer : clientWriters) {
                writer.println("SEND_IMAGE " + filePath);
                logArea.append("Image sent: " + filePath + "\n");
                actionLogger.info("Image sent: " + filePath);
            }
        }
    }

    private class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            ) {
                out = new PrintWriter(socket.getOutputStream(), true);
                String loginRequest = in.readLine();

                if (loginRequest != null && loginRequest.startsWith("LOGIN")) {
                    String[] credentials = loginRequest.split(" ");
                    if (credentials.length == 3 && authenticate(credentials[1], credentials[2])) {
                        username = credentials[1];
                        String token = generateToken(username);
                        out.println("LOGIN_SUCCESS " + token);
                        logger.info("User '" + username + "' logged in successfully.");
                        synchronized (clientWriters) {
                            clientWriters.add(out);
                        }
                    } else {
                        out.println("LOGIN_FAILED");
                        logger.warning("Failed login attempt for username: '" + credentials[1] + "'.");
                        socket.close();
                        return;
                    }
                }

                String message;
                while ((message = in.readLine()) != null) {
                    String[] parts = message.split(" ", 2);
                    if (parts.length == 2 && validateToken(parts[0])) {
                        if (parts[1].startsWith("SEND_IMAGE")) {
                            String imagePath = parts[1].substring(parts[1].indexOf(" ") + 1);
                            broadcast("Sent an Image");
                            broadcastImage(imagePath);
                            messageLogger.info(username + " sent an image: " + imagePath);
                        } else {
                            broadcast(parts[1]);
                            messageLogger.info(username + " sent a message: " + parts[1]);
                        }
                    } else {
                        out.println("INVALID_TOKEN");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (out != null) {
                    synchronized (clientWriters) {
                        clientWriters.remove(out);
                    }
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void broadcast(String message) {
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters) {
                    writer.println(username + " : " + message);
                }
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatServer::new);
    }
}
