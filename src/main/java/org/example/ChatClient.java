package org.example;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import javax.imageio.ImageIO;

import org.eclipse.paho.client.mqttv3.*;

public class ChatClient {
    private static final String SERVER_IP = "localhost";
    private static final int SERVER_PORT = 12345;

    private JFrame loginFrame, chatFrame;
    private JTextField usernameField, passwordField, inputField;
    private JTextPane messageArea;
    private JComboBox<String> protocolComboBox;

    private static final String MQTT_BROKER = "tcp://localhost:1883";
    private static final String BROADCAST_TOPIC = "chat/broadcast";
    private MqttClient mqttClient;

    private PrintWriter out;
    private BufferedReader in;
    private Socket socket;
    private String jwtToken;
    private String loggedInUsername;
    private String selectedProtocol;

    private boolean isChatOpen = false;

    public ChatClient() {
        createLoginUI();
    }

    private void createLoginUI() {
        loginFrame = new JFrame("Login");
        usernameField = new JTextField(15);
        passwordField = new JTextField(15);
        JButton loginButton = new JButton("Login");

        String[] protocols = {"Socket", "MQTT", "RCS"};
        protocolComboBox = new JComboBox<>(protocols);

        usernameField.setText("user1");
        passwordField.setText("password1");
        loginButton.addActionListener(e -> login());

        JPanel panel = new JPanel();
        panel.add(new JLabel("Username:"));
        panel.add(usernameField);
        panel.add(new JLabel("Password:"));
        panel.add(passwordField);
        panel.add(new JLabel("Protocol:"));
        panel.add(protocolComboBox);
        panel.add(loginButton);

        loginFrame.getContentPane().add(panel);
        loginFrame.pack();
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setVisible(true);
    }

    private void createChatUI() {
        chatFrame = new JFrame("Hello, " + loggedInUsername);

        messageArea = new JTextPane();
        messageArea.setEditable(false);
        messageArea.setPreferredSize(new Dimension(600, 400));
        inputField = new JTextField(50);
        JButton sendImageButton = new JButton("Send Image");

        inputField.addActionListener(e -> {
            String message = inputField.getText();
            if (!message.isEmpty()) {
                if (selectedProtocol == "Socket") {
                    out.println(jwtToken + " " + message);
                    inputField.setText("");
                }
                if (selectedProtocol == "MQTT") {
                    sendBroadcastMessage(jwtToken + " " + loggedInUsername + " " + message);
                    inputField.setText("");
                }
            }
        });

        sendImageButton.addActionListener(e -> sendImage());

        chatFrame.getContentPane().add(new JScrollPane(messageArea), BorderLayout.CENTER);
        chatFrame.getContentPane().add(inputField, BorderLayout.SOUTH);
        if (selectedProtocol == "Socket") {
            chatFrame.getContentPane().add(sendImageButton, BorderLayout.EAST);
        }
        chatFrame.pack();

        chatFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        chatFrame.setVisible(true);

        new Thread(this::receiveMessages).start();
    }

    private void login() {
        selectedProtocol = (String) protocolComboBox.getSelectedItem();

        if (selectedProtocol == "Socket") {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String username = usernameField.getText();
                String password = passwordField.getText();

                out.println("LOGIN " + username + " " + password);

                String response = in.readLine();
                if (response.startsWith("LOGIN_SUCCESS")) {
                    jwtToken = response.split(" ")[1];
                    loggedInUsername = username;
                    loginFrame.dispose();
                    JOptionPane.showMessageDialog(null, "Login Successful!\nToken: " + jwtToken);
                    createChatUI();
                } else {
                    JOptionPane.showMessageDialog(loginFrame, "Login Failed! Please try again.");
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(loginFrame, "Error connecting to server: " + e.getMessage());
            }
        }
        if (selectedProtocol == "MQTT") {
            try {
                mqttClient = new MqttClient(MQTT_BROKER, MqttClient.generateClientId());
                mqttClient.connect();
                mqttClient.subscribe(BROADCAST_TOPIC, this::onMessageReceived);
                System.out.println("Connected to MQTT broker and subscribed to broadcast topic.");

                String username = usernameField.getText();
                String password = passwordField.getText();
                sendBroadcastMessage("LOGIN " + username + " " + password);

            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }

    private void onMessageReceived(String topic, MqttMessage message) {
        System.out.println("Received Broadcast: " + new String(message.getPayload()));
        System.out.println("Received: " + message);
        String finalMessage = "" + message;
        System.out.println(finalMessage);

        if (finalMessage.startsWith("LOGIN_SUCCESS")) {
            jwtToken = finalMessage.split(" ")[2];
            loggedInUsername = finalMessage.split(" ")[1];

            loginFrame.dispose();
            JOptionPane.showMessageDialog(null, "Login Successful!\nToken: " + jwtToken);
            createChatUI();
            isChatOpen = true;
        } else if (finalMessage.startsWith("LOGIN_FAILED")) {
            JOptionPane.showMessageDialog(loginFrame, "Login Failed! Please try again.");
        } else if (isChatOpen) {
            String[] parts = finalMessage.split(" ", 2);
            SwingUtilities.invokeLater(() -> appendMessage(parts[1]));
        }
    }

    public void sendBroadcastMessage(String message) {
        try {
            mqttClient.publish(BROADCAST_TOPIC, new MqttMessage(message.getBytes()));
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void sendImage() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int returnValue = fileChooser.showOpenDialog(chatFrame);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String filePath = selectedFile.getAbsolutePath();
            out.println(jwtToken + " SEND_IMAGE " + filePath);
            appendMessage("You sent an image: " + filePath);
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("Received: " + message);
                String finalMessage = message;
                SwingUtilities.invokeLater(() -> appendMessage(finalMessage));
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(chatFrame, "Connection lost: " + e.getMessage());
            closeConnection();
        }
    }

    private void appendMessage(String message) {
        try {
            StyledDocument doc = messageArea.getStyledDocument();

            if (message.startsWith("SEND_IMAGE ")) {
                String imagePath = message.substring("SEND_IMAGE ".length());
                displayThumbnail(imagePath);
                return;
            }

            boolean isCurrentUser = message.startsWith(loggedInUsername + " :");
            String[] msgParts = message.split(" ", 2);
            if (isCurrentUser) {
                message = "You " + msgParts[1];
            }

            Style style = messageArea.addStyle(isCurrentUser ? "RightAligned" : "LeftAligned", null);
            StyleConstants.setAlignment(style, isCurrentUser ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);

            doc.insertString(doc.getLength(), message + "\n", style);

            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setAlignment(attrs, isCurrentUser ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);
            doc.setParagraphAttributes(doc.getLength() - (message.length() + 1), message.length() + 1, attrs, false);

        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void displayThumbnail(String imagePath) {
        try {
            BufferedImage originalImage = ImageIO.read(new File(imagePath));
            Image thumbnailImage = originalImage.getScaledInstance(100, 100, Image.SCALE_SMOOTH);
            ImageIcon thumbnailIcon = new ImageIcon(thumbnailImage);

            JLabel thumbnailLabel = new JLabel(thumbnailIcon);
            thumbnailLabel.setPreferredSize(new Dimension(100, 100));
            thumbnailLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            thumbnailLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    downloadImage(imagePath);
                }
            });

            StyledDocument doc = messageArea.getStyledDocument();
            doc.insertString(doc.getLength(), "\n", null);
            messageArea.setCaretPosition(doc.getLength());
            messageArea.insertComponent(thumbnailLabel);
            messageArea.revalidate();
            messageArea.repaint();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void downloadImage(String imagePath) {
        File originalFile = new File(imagePath);
        String fileName = originalFile.getName();
        String fileExtension = "";

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            fileExtension = fileName.substring(dotIndex);
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(fileName));

        int returnValue = fileChooser.showSaveDialog(chatFrame);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File destinationFile = fileChooser.getSelectedFile();

            if (!destinationFile.getName().contains(".")) {
                destinationFile = new File(destinationFile.getAbsolutePath() + fileExtension);
            }

            try (InputStream in = new FileInputStream(originalFile);
                 OutputStream out = new FileOutputStream(destinationFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                JOptionPane.showMessageDialog(chatFrame, "Image downloaded successfully!");
            } catch (IOException e) {
                JOptionPane.showMessageDialog(chatFrame, "Error downloading image: " + e.getMessage());
            }
        }
    }

    private void closeConnection() {
        try {
            if (socket != null) socket.close();
            if (out != null) out.close();
            if (in != null) in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClient::new);
    }
}
