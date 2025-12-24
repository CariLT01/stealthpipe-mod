# StealthPipe

StealthPipe is an experimental Minecraft mod that allows **Open to LAN multiplayer to work on restricted networks**. It achieves this by sending Minecraft traffic over WebSockets, making it appear as normal browser traffic.

## Usage

Press "Open to LAN" as usual, and the mod will generate a connection link for other players. **All players must have this mod installed** to join the same session. Multiple clients can connect to a single room simultaneously.

You can configure which relay to use in the mod settings. Use a third-party relay or host your own if needed. The relay backend is open-source and MIT-licensed.

## Hosting a Relay

If you experience high latency or unstable connections, you can host your own relay. Step-by-step instructions are available here:  
https://github.com/CariLT01/stealthpipe-relay/blob/main/HOSTING.md

## Mod Compatibility

StealthPipe is compatible with all mods tested so far. Play alongside your favorite mods without issues.

## Backporting and Ports for Forge/NeoForge

The mod is not officially backported to other Minecraft versions or modloaders. However, you are free to port it to any version or modloader as needed. Updates will be provided for the supported version when necessary.

## License

StealthPipe is licensed under the MIT License.
