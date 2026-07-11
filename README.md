
# Java Swing Chat Application

A multi-protocol desktop chat application built with Java Swing, supporting real-time text and image messaging between a central server and multiple clients over **Socket (TCP)** and **MQTT** protocols.

## Features

- **Dual Protocol Support** — Clients can connect via raw TCP sockets or MQTT (with an "RCS" option listed in the UI for future extension).
- **JWT Authentication** — The server issues HMAC-SHA256 signed JWT tokens on successful login; all subsequent client messages are validated against this token.
- **User Management (Server GUI)** — Add or remove chat users at runtime through a Swing-based server console.
- **Image Sharing** — Clients can send image files; recipients see inline thumbnails and can click to download the full image.
- **Logging** — Separate log files track login attempts (`login.log`), chat messages (`messages.log`), and admin actions (`actions.log`).
- **Broadcast Messaging** — Messages sent by one client are broadcast to all connected clients, with left/right message alignment based on sender identity.

## Project Structure

| File                 | Description                                                                                                                                                                                                         |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ChatServer.java`  | Main server application. Hosts a Swing GUI to start/stop the server, manage users, and send broadcast images. Listens on TCP port`12345` and also bridges messages via an MQTT broker (`tcp://localhost:1883`). |
| `ChatClient.java`  | Primary client application with login UI (protocol selector: Socket/MQTT/RCS), chat window, message styling, and image thumbnail/download support.                                                                  |
| `ChatClient1.java` | A near-duplicate/alternate version of the client, useful for testing multiple simultaneous client instances.                                                                                                        |
| `Main.java`        | Placeholder entry point (prints "Hello world!"); not part of the core chat logic.                                                                                                                                   |
| `Manifest.txt`     | JAR manifest specifying`ChatServer` as the executable main class.                                                                                                                                                 |
| `logo.jpg`         | Project/application logo image.                                                                                                                                                                                     |

## Architecture Overview

```
┌─────────────┐        TCP Socket (port 12345)        ┌─────────────┐
│ ChatClient  │ ─────────────────────────────────────► │             │
└─────────────┘                                        │  ChatServer │
┌─────────────┐        MQTT (chat/broadcast topic)     │             │
│ ChatClient1 │ ◄────────────────────────────────────► │             │
└─────────────┘        via broker tcp://localhost:1883 └─────────────┘
```

- **Socket mode**: Each client opens a dedicated `Socket` connection; the server spawns a `ClientHandler` thread per client and rebroadcasts messages/images to all connected `PrintWriter` clients.
- **MQTT mode**: Both server and clients connect to a local MQTT broker and publish/subscribe to a shared `chat/broadcast` topic, using message prefixes (`LOGIN`, `LOGIN_SUCCESS`, `SEND_IMAGE`, etc.) as a lightweight protocol.

## Prerequisites

- Java JDK 8 or higher
- [Eclipse Paho MQTT Client](https://www.eclipse.org/paho/) library (`org.eclipse.paho.client.mqttv3`)
- [JJWT](https://github.com/jwtk/jjwt) library (`io.jsonwebtoken`)
- A running MQTT broker (e.g., [Mosquitto](https://mosquitto.org/)) on `localhost:1883` if using MQTT mode

## Build & Run

1. Compile all source files, ensuring the Paho MQTT and JJWT JARs are on the classpath:

   ```
   javac -cp ".:paho-mqtt-client.jar:jjwt-api.jar:jjwt-impl.jar:jjwt-jackson.jar" *.java
   ```
2. Start the server:

   ```
   java -cp ".:paho-mqtt-client.jar:jjwt-api.jar:jjwt-impl.jar:jjwt-jackson.jar" org.example.ChatServer
   ```

   Click **Start Server** in the GUI to begin listening on port 12345.
3. Start one or more clients:

   ```
   java -cp ".:paho-mqtt-client.jar:jjwt-api.jar:jjwt-impl.jar:jjwt-jackson.jar" org.example.ChatClient
   ```
4. Log in with a default credential (e.g., `user1` / `password1`, or `a` / `a`), select a protocol (Socket or MQTT), and start chatting.

### Building a Runnable JAR

Use the provided `Manifest.txt` (Main-Class: `ChatServer`) to package the server as an executable JAR:

```
jar cfm ChatServer.jar Manifest.txt *.class
java -jar ChatServer.jar
```

## Default Users

The server initializes with these built-in accounts (defined in `ChatServer.java`):

| Username | Password  |
| -------- | --------- |
| user1    | password1 |
| user2    | password2 |
| a        | a         |
| b        | b         |

Additional users can be added or removed live via the server GUI.

## Notes & Known Limitations

- Credentials and messages are transmitted in plaintext over sockets (aside from the JWT token); this is a demo/learning project, not production-ready security.
- The MQTT broker address (`tcp://localhost:1883`) is hardcoded — update `MQTT_BROKER` in both client and server source if using a remote broker.
- `ChatClient.java` and `ChatClient1.java` are nearly identical; consider consolidating them if extending the project.
