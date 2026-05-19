package com.stealthpipe;

import com.stealthpipe.config.DefaultConfigValues;
import com.stealthpipe.connection.debug.LatencySpikeDirection;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
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
                    .setTitle(Component.translatable("config.stealthpipe.title"));

            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            ConfigCategory general = builder.getOrCreateCategory(Component.translatable("config.stealthpipe.general"));

            general.addEntry(
                    entryBuilder.startStrField(
                            Component.translatable("config.stealthpipe.relayAddress"), StealthPipe.config.RELAY_IP
                    ).setDefaultValue(DefaultConfigValues.RELAY_IP)
                            .setSaveConsumer(newValue -> StealthPipe.config.RELAY_IP = newValue)
                            .setTooltip(Component.translatable("config.stealthpipe.relayAddress.description"))
                            .build()
            );

            general.addEntry(
                    entryBuilder.startBooleanToggle(
                            Component.translatable("config.stealthpipe.onlineMode"), StealthPipe.config.ONLINE_MODE
                    ).setDefaultValue(DefaultConfigValues.ONLINE_MODE)
                            .setSaveConsumer(newValue -> StealthPipe.config.ONLINE_MODE = newValue)
                            .setTooltip(Component.translatable("config.stealthpipe.onlineMode.description"))
                            .build()
            );



            general.addEntry(
                    entryBuilder.startIntField(Component.translatable("config.stealthpipe.connectionAttempts"), StealthPipe.config.RELAY_PING_ATTEMPTS)
                            .setDefaultValue(DefaultConfigValues.RELAY_PING_ATTEMPTS)
                            .setSaveConsumer(newValue -> StealthPipe.config.RELAY_PING_ATTEMPTS = newValue)
                            .setTooltip(
                                    Component.translatable("config.stealthpipe.connectionAttempts.description")
                            )
                            .build()
            );

            general.addEntry(
                    entryBuilder.startBooleanToggle(Component.translatable("config.stealthpipe.webrtcClientEnabled"), StealthPipe.config.CLIENT_ATTEMPT_WEBRTC)
                            .setDefaultValue(DefaultConfigValues.CLIENT_ATTEMPT_WEBRTC)
                            .setSaveConsumer(newValue -> StealthPipe.config.CLIENT_ATTEMPT_WEBRTC = newValue)
                            .setTooltip(
                                    Component.translatable("config.stealthpipe.webrtcClientEnabled.description")
                            )
                            .build()
            );

            general.addEntry(
                    entryBuilder.startBooleanToggle(Component.translatable("config.stealthpipe.webrtcHostEnabled"), StealthPipe.config.HOST_ALLOW_WEBRTC_INBOUND)
                            .setDefaultValue(DefaultConfigValues.HOST_ALLOW_WEBRTC_INBOUND)
                            .setSaveConsumer(newValue -> StealthPipe.config.HOST_ALLOW_WEBRTC_INBOUND = newValue)
                            .setTooltip(
                                    Component.translatable("config.stealthpipe.webrtcHostEnabled.description")
                            )
                            .build()
            );

            general.addEntry(
                    entryBuilder.startBooleanToggle(Component.literal("Show Connect Info"), StealthPipe.config.SHOW_CONNECT_INFO)
                            .setDefaultValue(DefaultConfigValues.SHOW_CONNECT_INFO)
                            .setSaveConsumer(newValue -> StealthPipe.config.SHOW_CONNECT_INFO = newValue)
                            .setTooltip(
                                    Component.literal("Toggles the rendering of the connect info & progress")
                            )
                            .build()
            );

            general.addEntry(
                    entryBuilder.startBooleanToggle(Component.translatable("config.stealthpipe.safeInject"), StealthPipe.config.USE_SAFE_INJECT)
                            .setDefaultValue(DefaultConfigValues.USE_SAFE_INJECT)
                            .setSaveConsumer(v -> StealthPipe.config.USE_SAFE_INJECT = v)
                            .setTooltip(
                                    Component.translatable("config.stealthpipe.safeInject.description")
                            )
                            .build()
            );

            general.addEntry(
                    entryBuilder.startBooleanToggle(Component.translatable("config.stealthpipe.warnCorruptedData"), StealthPipe.config.WARN_CORRUPTED_DATA)
                            .setDefaultValue(DefaultConfigValues.WARN_CORRUPTED_DATA)
                            .setSaveConsumer(v -> StealthPipe.config.WARN_CORRUPTED_DATA = v)
                            .setTooltip(
                                    Component.translatable("config.stealthpipe.warnCorruptedData.description")
                            )
                            .build()
            );

            // Add section "Optimization"
            var optimization = entryBuilder.startSubCategory(Component.translatable("config.stealthpipe.optimization"));

            optimization.add(
                    entryBuilder.startBooleanToggle(Component.translatable("config.stealthpipe.packetBatching"), StealthPipe.config.ENABLE_BATCHED_PACKETS)
                            .setDefaultValue(DefaultConfigValues.ENABLE_BATCHED_PACKETS)
                            .setSaveConsumer(newValue -> StealthPipe.config.ENABLE_BATCHED_PACKETS = newValue)
                            .setTooltip(
                                    Component.translatable("config.stealthpipe.packetBatching.description")
                            )
                            .build()
            );

            optimization.add(
                    entryBuilder.startIntField(Component.translatable("config.stealthpipe.packetBatchingInterval"), StealthPipe.config.PACKET_BATCHING_INTERVAL_MS)
                            .setDefaultValue(DefaultConfigValues.PACKET_BATCHING_INTERVAL_MS)
                            .setSaveConsumer(newValue -> StealthPipe.config.PACKET_BATCHING_INTERVAL_MS = newValue)
                            .setTooltip(
                                    Component.translatable("config.stealthpipe.packetBatchingInterval.description")
                            )
                            .build()
            );

            optimization.add(
                    entryBuilder.startBooleanToggle(Component.translatable("config.stealthpipe.parkCpu"), StealthPipe.config.PARK_CPU)
                            .setDefaultValue(DefaultConfigValues.PARK_CPU)
                            .setSaveConsumer(newValue -> StealthPipe.config.PARK_CPU = newValue)
                            .setTooltip(Component.translatable("config.stealthpipe.parkCpu.description"))
                            .build()
            );

            optimization.add(
                    entryBuilder.startIntField(Component.translatable("config.stealthpipe.threadPriority"), StealthPipe.config.THREAD_PRIORITY)
                            .setDefaultValue(DefaultConfigValues.THREAD_PRIORITY)
                            .setMin(1)
                            .setMax(10)
                            .setSaveConsumer(newValue -> StealthPipe.config.THREAD_PRIORITY = newValue)
                            .setTooltip(Component.translatable("config.stealthpipe.threadPriority.description"))
                            .build()
            );

            general.addEntry(optimization.build());


            var debug = entryBuilder.startSubCategory(Component.translatable("config.stealthpipe.debugging"));
            debug.add(
                    entryBuilder.startBooleanToggle(Component.translatable("config.stealthpipe.wrtcIceFailure"), StealthPipe.config.SIMULATE_ICE_CANDIDATES_FAILURE)
                            .setDefaultValue(DefaultConfigValues.SIMULATE_ICE_CANDIDATES_FAILURE)
                            .setSaveConsumer(newValue -> StealthPipe.config.SIMULATE_ICE_CANDIDATES_FAILURE = newValue)
                            .setTooltip(
                                    Component.translatable("config.stealthpipe.wrtcIceFailure.description")
                            )
                            .build()
            );

            debug.add(
                    entryBuilder.startBooleanToggle(Component.translatable("config.stealthpipe.logIceCandidates"), StealthPipe.config.LOG_WRTC_ICE_CANDIDATES)
                            .setDefaultValue(DefaultConfigValues.LOG_WRTC_ICE_CANDIDATES)
                            .setSaveConsumer(newValue -> StealthPipe.config.LOG_WRTC_ICE_CANDIDATES = newValue)
                            .setTooltip(
                                    Component.translatable("config.stealthpipe.logIceCandidates.description")
                            )
                            .build()
            );

            debug.add(
                    entryBuilder.startBooleanToggle(Component.translatable("config.stealthpipe.abnormalDisconnectHost"), StealthPipe.config.SIMULATE_ABNORMAL_DISCONNECT_HOST)
                            .setDefaultValue(DefaultConfigValues.SIMULATE_ABNORMAL_DISCONNECT_HOST)
                            .setSaveConsumer(newValue -> StealthPipe.config.SIMULATE_ABNORMAL_DISCONNECT_HOST = newValue)
                            .setTooltip(
                                    Component.translatable("config.stealthpipe.abnormalDisconnectHost.description")
                            )
                            .build()
            );

            debug.add(
                    entryBuilder.startBooleanToggle(Component.translatable("config.stealthpipe.abnormalDisconnectClient"), StealthPipe.config.SIMULATE_ABNORMAL_DISCONNECT_CLIENT)
                            .setDefaultValue(DefaultConfigValues.SIMULATE_ABNORMAL_DISCONNECT_CLIENT)
                            .setSaveConsumer(newValue -> StealthPipe.config.SIMULATE_ABNORMAL_DISCONNECT_CLIENT = newValue)
                            .setTooltip(
                                    Component.translatable("config.stealthpipe.abnormalDisconnectClient.description")
                            )
                            .build()
            );

            debug.add(
                    entryBuilder.startIntField(Component.translatable("config.stealthpipe.failureDelay"), StealthPipe.config.SIMULATED_FAILURE_DELAY)
                            .setDefaultValue(DefaultConfigValues.SIMULATED_FAILURE_DELAY)
                            .setSaveConsumer(newValue -> StealthPipe.config.SIMULATED_FAILURE_DELAY = newValue)
                            .setTooltip(
                                    Component.translatable("config.stealthpipe.failureDelay.description")
                            )
                            .build()
            );

            debug.add(
                    entryBuilder.startBooleanToggle(Component.translatable("config.stealthpipe.latencySpikes"), StealthPipe.config.LATENCY_SPIKES)
                            .setDefaultValue(DefaultConfigValues.LATENCY_SPIKES)
                            .setSaveConsumer(v -> StealthPipe.config.LATENCY_SPIKES = v)
                            .setTooltip(Component.translatable("config.stealthpipe.latencySpikes.description"))
                            .build()
            );

            debug.add(
                    entryBuilder.startEnumSelector(Component.translatable("config.stealthpipe.latencySpikesDirection"), LatencySpikeDirection.class, StealthPipe.config.LATENCY_DIRECTION)
                            .setDefaultValue(DefaultConfigValues.LATENCY_DIRECTION)
                            .setSaveConsumer(v -> StealthPipe.config.LATENCY_DIRECTION = v)
                            .setTooltip(Component.translatable("config.stealthpipe.latencySpikesDirection.description"))
                            .build()
            );

            debug.add(
                    entryBuilder.startIntField(Component.translatable("config.stealthpipe.latencyBaseline"), StealthPipe.config.LATENCY_BASELINE)
                            .setDefaultValue(DefaultConfigValues.LATENCY_BASELINE)
                            .setSaveConsumer(v -> StealthPipe.config.LATENCY_BASELINE = v)
                            .setTooltip(Component.translatable("config.stealthpipe.latencyBaseline.description"))
                            .build()
            );

            debug.add(
                    entryBuilder.startBooleanToggle(Component.translatable("config.stealthpipe.dataMisalignment"), StealthPipe.config.SIMULATE_DATA_MISALIGNMENT)
                            .setDefaultValue(DefaultConfigValues.SIMULATE_DATA_MISALIGNMENT)
                            .setSaveConsumer(v -> StealthPipe.config.SIMULATE_DATA_MISALIGNMENT = v)
                            .setTooltip(Component.translatable("config.stealthpipe.dataMisalignment.description"))
                            .build()
            );

            general.addEntry(debug.build());

            builder.setSavingRunnable(() -> {
                StealthPipe.config.save();
            });

            return builder.build();
        };
    }
}
