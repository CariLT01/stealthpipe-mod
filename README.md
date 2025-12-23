# Stealth Relay Mod

A specialized Minecraft connectivity tool designed to bypass restrictive network environments by bridging game traffic through a secure WebSocket tunnel.

## General Information

### Overview
This mod allows users to host or join Minecraft LAN worlds over standard web protocols. By converting raw game packets into WebSocket traffic, it disguises Minecraft data as normal web activity, making it possible to play on networks that typically block direct game server connections.

### Key Features
* LAN over Web: Connect to friends' local worlds without a VPN or port forwarding.
* Traffic Camouflage: Disguises game packets as standard web traffic to prevent detection by firewalls.
* Seamless Integration: Works directly within the Minecraft multiplayer menu.

### How to Use
1. Host: When opening a world to LAN, the mod generates a unique 6-digit session code. Share this code with your friends.
2. Join: Enter the 6-digit code in the Stealth Relay menu to establish a connection to the host.
3. Play: The mod handles the translation of data in the background.

### Mod Compatibility
This mod should be compatible with most mods as it only pipes serialized binary data. It does not modify packet data or manipulate them.

---

## Technical Details

### Networking Architecture
The mod implements a packet-piping system that intercepts the standard Minecraft Netty pipeline. Instead of sending packets via TCP/IP to a local address, the mod serializes the data and transmits it over a secure WebSocket (WSS) connection.



### Session Management
Identification and routing are handled via a transient 6-digit code system:
* Code Generation: A unique numeric identifier is assigned to each hosting session.
* Resolution: The client uses this code to locate the specific relay path required to reach the host.
* Session Isolation: Each code represents a distinct tunnel; no persistent user identifiers or account UUIDs are used for session routing to maintain privacy.

### Virtual Connection Handling
On the server side (the host), the mod creates a virtual network interface. When a remote player connects via the relay, the mod injects a virtual channel into the game server's connection listener. To the Minecraft engine, the remote player appears as if they are connected from the local machine (127.0.0.1), even though the data is being piped from a remote web source.



### Traffic Encoding
All data sent through the tunnel is framed for WebSocket compatibility. This ensures that the stream remains synchronized and that packet boundaries are preserved during the translation between the game's native protocol and the web-based transport layer.
