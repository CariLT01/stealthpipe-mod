# StealthPipe

**Play anywhere. Connect anywhere. Host anywhere.**

StealthPipe is an experimental Minecraft mod that allows Open to LAN multiplayer to work on networks that block peer-to-peer connections and non-standard ports.
You can also host your own relay anywhere.

No bloat, no accounts, no tracking.

This mod does not collect any telemetry or user data. Backend source code can be found here: https://github.com/CariLT01/stealthpipe-relay

## Why?

In many network environments, peer-to-peer connections are blocked and most ports other than 443 are restricted, like in airports and hotels. Since Minecraft’s Open to LAN relies on direct connections and non-443 ports, it simply doesn’t work in these conditions.

StealthPipe solves this by tunneling LAN multiplayer traffic through secure WebSockets over port 443, allowing players to connect where traditional LAN hosting would normally fail.

StealthPipe is a protocol-specific relay; it only handles Minecraft game packets and cannot be used as a general-purpose VPN or proxy. It also cannot connect to public servers such as Hypixel.

## How?

It achieves this by sending Minecraft traffic over WebSockets, making it appear as normal browser traffic. This mod is similar to e4mc, but it routes traffic through WebSockets instead of using QUIC.


## Usage

1. **Host:** Press "Open to LAN" and share the generated link or code.
2. **Join:** Guests enter the code to join.
3. **Requirement:** All players must have the mod installed.

You can configure which relay to use in the mod settings. Use a third-party relay or host your own if needed. The relay backend is open-source and MIT-licensed. The default relay in this mod is completely free to use.

## Self-Hosting a Relay
It is highly recommended to host your own relay for control and stability. The relay is designed to be easily portable and can run everywhere, regardless of the environment by using Docker containers. The entire relay consists of three files.
StealthPipe relay is multithreaded, lightweight, and portable. It uses less than 32 MB of memory.

The guide is available here:
https://github.com/CariLT01/stealthpipe-relay/blob/main/HOSTING.md

## Alternative relays

The default relay hosted on Render.com can sometimes be slow and unreliable depending on external factors. It can handle up to 10 players (using the Free tier).
Explore these alternative relays if you have issues with the default one:

- Default (sometimes offline): https://mcpipeservice-go.onrender.com/
- HF Space Relay (backup relay, but maybe offline): https://publicuser22222222-mcpipeservice-go.hf.space


## Mod Compatibility

StealthPipe is compatible with all mods tested so far. Play alongside your favorite mods without issues. If you find any incompatibilities with other mods, please report them in the Issues tab and attach the crash report.

This mod cannot be installed on a dedicated server.

## Important Notes

- **2.1 MB packet size limit**: The public relay enforces a 2.1 MB packet size limit for stability. Since Vanilla Minecraft defaults to a 2 MB limit, this limit is normally fine when playing with a minimal amount of mods. However, this means that mods like Packet Fixer or XXL Packets will not work on the public relay; the relay will disconnect the client before the mod can bypass the limit.

    If your modpack requires larger packets, you must host your own relay and adjust the limit in the source code.

- **Throttling if consuming too much bandwidth**: To protect the server's bandwidth usage, your connection may be throttled or disconnected if you are sending too much data. The threshold depends on the duration of sustained high bandwidth usage and the number of clients connected to the current world You may resolve this by hosting your own relay, see the self-hosting section above.

The public relay may get upgraded if this mod starts becoming too popular.

## Backporting and Ports for Forge/NeoForge

The mod is not officially backported to other Minecraft versions or modloaders. However, you are free to port it to any version or modloader as needed. Updates will be provided for the supported versions when necessary.

## Bugs and Issues

Feel free to report any bugs or issues in the Issues tab on the GitHub repository.

## License

StealthPipe is licensed under the MIT License.
