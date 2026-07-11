package org.example;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;

public class ChatClient1 {
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

    public ChatClient1() {
        createLoginUI();
    }

    private void createLoginUI() {
        loginFrame = new JFrame("Login");
        usernameField = new JTextField(15);
        passwordField = new JTextField(15);
        JButton loginButton = new JButton("Login");

        String[] protocols = {"Socket", "MQTT","RCS"};
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
        JButton sendImageButton = new JButton("Send Image"); // Button for sending images

        // Action listener for sending text messages
        inputField.addActionListener(e -> {
            String message = inputField.getText();
            if (!message.isEmpty()) {
                if(selectedProtocol == "Socket")
                {
                    out.println(jwtToken + " " + message); // Send message with JWT token
                    inputField.setText("");
                }
                if(selectedProtocol == "MQTT"){
                    sendBroadcastMessage(jwtToken+" " + loggedInUsername + " " + message);
                    inputField.setText("");
                }
            }
        });

        // Action listener for sending images
        sendImageButton.addActionListener(e -> sendImage());

        chatFrame.getContentPane().add(new JScrollPane(messageArea), BorderLayout.CENTER);
        chatFrame.getContentPane().add(inputField, BorderLayout.SOUTH);
        if(selectedProtocol == "Socket"){
            chatFrame.getContentPane().add(sendImageButton, BorderLayout.EAST);
            // Add image button to the chat UI
        }
        chatFrame.pack();

        chatFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        chatFrame.setVisible(true);

        // Start a background thread to receive messages
        new Thread(this::receiveMessages).start();
    }

    private void login() {
        selectedProtocol=(String) protocolComboBox.getSelectedItem();

        if(selectedProtocol == "Socket"){
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String username = usernameField.getText();
                String password = passwordField.getText();

                out.println("LOGIN " + username + " " + password); // Send login request

                String response = in.readLine(); // Read response from server
                if (response.startsWith("LOGIN_SUCCESS")) {
                    jwtToken = response.split(" ")[1]; // Extract the JWT token
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
        if(selectedProtocol == "MQTT"){
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
        System.out.println("Received: " + message); // Log for debugging
        String finalMessage = ""+message;
        System.out.println(finalMessage);

        if (finalMessage.startsWith("LOGIN_SUCCESS")) {
            jwtToken = finalMessage.split(" ")[2]; // Extract the JWT token
            loggedInUsername = finalMessage.split(" ")[1];

            loginFrame.dispose();
            JOptionPane.showMessageDialog(null, "Login Successful!\nToken: " + jwtToken);
            createChatUI();
            isChatOpen=true;
        }
        else if(finalMessage.startsWith("LOGIN_FAILED")) {
            JOptionPane.showMessageDialog(loginFrame, "Login Failed! Please try again.");
        }
        else if(isChatOpen){
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



    // Method to send an image
    private void sendImage() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int returnValue = fileChooser.showOpenDialog(chatFrame);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String filePath = selectedFile.getAbsolutePath();
            out.println(jwtToken + " SEND_IMAGE " + filePath); // Send image command with JWT token
            appendMessage("You sent an image: " + filePath); // Optionally append a message in the chat area
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) { // Listen for incoming messages
                System.out.println("Received: " + message); // Log for debugging
                String finalMessage = message;
                SwingUtilities.invokeLater(() -> appendMessage(finalMessage)); // Update UI safely
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(chatFrame, "Connection lost: " + e.getMessage());
            closeConnection();
        }
    }

    private void appendMessage(String message) {
        try {
            StyledDocument doc = messageArea.getStyledDocument();

            // Check if the message is an image path
            if (message.startsWith("SEND_IMAGE ")) {
                String imagePath = message.substring("SEND_IMAGE ".length());
                displayThumbnail(imagePath);
                return;
            }

            // Check if the message starts with the logged-in username
            boolean isCurrentUser = message.startsWith(loggedInUsername + " :");
            String[] msgParts = message.split(" ", 2);
            if(isCurrentUser){
                message = "You " + msgParts[1];

            }

            // Create a new style for the message
            Style style = messageArea.addStyle(isCurrentUser ? "RightAligned" : "LeftAligned", null);
            StyleConstants.setAlignment(style, isCurrentUser ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT); // Align based on user

            // Insert the message with the appropriate alignment
            doc.insertString(doc.getLength(), message + "\n", style);

            // Apply paragraph attributes for alignment
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setAlignment(attrs, isCurrentUser ? StyleConstants.ALIGN_RIGHT : StyleConstants.ALIGN_LEFT);
            doc.setParagraphAttributes(doc.getLength() - (message.length() + 1), message.length() + 1, attrs, false);

        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void displayThumbnail(String imagePath) {
        try {
            // Load the image and create a thumbnail
            BufferedImage originalImage = ImageIO.read(new File(imagePath));
            Image thumbnailImage = originalImage.getScaledInstance(100, 100, Image.SCALE_SMOOTH); // Create a thumbnail
            ImageIcon thumbnailIcon = new ImageIcon(thumbnailImage);

            // Create a label for the thumbnail
            JLabel thumbnailLabel = new JLabel(thumbnailIcon);
            thumbnailLabel.setPreferredSize(new Dimension(100, 100)); // Set size for the thumbnail
            thumbnailLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); // Change cursor to hand when hovering

            // Add a mouse listener to handle clicks for downloading
            thumbnailLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    downloadImage(imagePath); // Call the download method on click
                }
            });

            // Add the thumbnail label to the message area
            StyledDocument doc = messageArea.getStyledDocument();
            doc.insertString(doc.getLength(), "\n", null); // Add a line break before the image
            messageArea.setCaretPosition(doc.getLength()); // Move caret to the end
            messageArea.insertComponent(thumbnailLabel); // Insert the image label into the JTextPane
            messageArea.revalidate(); // Revalidate the text pane to refresh the layout
            messageArea.repaint(); // Repaint the text pane to show the image

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private void downloadImage(String imagePath) {
        // Extract the file name and extension from the imagePath
        File originalFile = new File(imagePath);
        String fileName = originalFile.getName(); // Get the file name with extension
        String fileExtension = ""; // Initialize to hold the extension

        // Find the last dot to extract the extension
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            fileExtension = fileName.substring(dotIndex); // Get the file extension
        }

        // Prompt user for download location
        JFileChooser fileChooser = new JFileChooser();
        // Set the selected file to the name of the image
        fileChooser.setSelectedFile(new File(fileName)); // Use the original file name

        int returnValue = fileChooser.showSaveDialog(chatFrame);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File destinationFile = fileChooser.getSelectedFile();

            // If the selected file does not have an extension, add the original file's extension
            if (!destinationFile.getName().contains(".")) {
                destinationFile = new File(destinationFile.getAbsolutePath() + fileExtension);
            }

            // Ensure you are using the correct InputStream and OutputStream
            try (InputStream in = new FileInputStream(originalFile); // Read from the original image path
                 OutputStream out = new FileOutputStream(destinationFile)) { // Write to the chosen location
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
        SwingUtilities.invokeLater(ChatClient1::new); // Run the client on the Event Dispatch Thread
    }
}