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
                            .build()
            );

            general.addEntry(
                    entryBuilder.startStrField(Component.literal("Relay IP WebSocket"), StealthPipe.config.RELAY_IP_WS)
                            .setDefaultValue(DefaultConfigValues.RELAY_IP_WS)
                            .setSaveConsumer(newValue -> StealthPipe.config.RELAY_IP_WS = newValue)
                            .build()
            );

            builder.setSavingRunnable(() -> {
                StealthPipe.config.save();
            });

            return builder.build();
        };
    }
}
