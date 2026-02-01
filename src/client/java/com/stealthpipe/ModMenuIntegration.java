package com.stealthpipe;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.network.chat.Component;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {

            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Component.literal("StealthPipe Configuration"));

            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

            general.addEntry(
                    entryBuilder.startStrField(
                            Component.literal("Relay IP"), StealthPipe.config.RELAY_IP
                    ).setDefaultValue(DefaultConfigValues.RELAY_IP)
                            .setSaveConsumer(newValue -> StealthPipe.config.RELAY_IP = newValue)
                            .setTooltip(Component.literal(
                                    "Controls which server StealthPipe will connect to in order to forward your game traffic. Must be a StealthPipe relay server."
                            ))
                            .build()
            );

            general.addEntry(
                    entryBuilder.startBooleanToggle(
                            Component.literal("Use Online Mode"), StealthPipe.config.ONLINE_MODE
                    ).setDefaultValue(DefaultConfigValues.ONLINE_MODE)
                            .setSaveConsumer(newValue -> StealthPipe.config.ONLINE_MODE = newValue)
                            .setTooltip(Component.literal(
                                    "Controls if online mode is enabled for singleplayer worlds. Solves issues with invalid session in development."
                            ))
                            .build()
            );



            general.addEntry(
                    entryBuilder.startIntField(Component.literal("Number of attempts to reach relay"), StealthPipe.config.RELAY_PING_ATTEMPTS)
                            .setDefaultValue(DefaultConfigValues.RELAY_PING_ATTEMPTS)
                            .setSaveConsumer(newValue -> StealthPipe.config.RELAY_PING_ATTEMPTS = newValue)
                            .setTooltip(
                                    Component.literal("Controls the number of attempts to reach the relay before giving up")
                            )
                            .build()
            );

            // Add section "Optimization"
            var optimization = entryBuilder.startSubCategory(Component.literal("Optimization"));

            optimization.add(
                    entryBuilder.startBooleanToggle(Component.literal("Enable Packets Batching"), StealthPipe.config.ENABLE_BATCHED_PACKETS)
                            .setDefaultValue(DefaultConfigValues.ENABLE_BATCHED_PACKETS)
                            .setSaveConsumer(newValue -> StealthPipe.config.ENABLE_BATCHED_PACKETS = newValue)
                            .setTooltip(
                                    Component.literal("Groups of packets that are sent at roughly the same time will be batched. Decreases overhead, but slightly increases ping.")
                            )
                            .build()
            );

            optimization.add(
                    entryBuilder.startIntField(Component.literal("Packet Batching Interval"), StealthPipe.config.PACKET_BATCHING_INTERVAL_MS)
                            .setDefaultValue(DefaultConfigValues.PACKET_BATCHING_INTERVAL_MS)
                            .setSaveConsumer(newValue -> StealthPipe.config.PACKET_BATCHING_INTERVAL_MS = newValue)
                            .setTooltip(
                                    Component.literal("Controls the number of milliseconds to hold on to these packets until it is sent altogether. Higher number will increases batching efficiency but will also increase ping.")
                            )
                            .build()
            );

            general.addEntry(optimization.build());

            builder.setSavingRunnable(() -> {
                StealthPipe.config.save();
            });

            return builder.build();
        };
    }
}
