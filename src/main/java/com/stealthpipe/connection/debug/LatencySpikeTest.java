package com.stealthpipe.connection.debug;

import com.stealthpipe.ModState;
import com.stealthpipe.StealthPipe;
import com.stealthpipe.enums.PacketFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LatencySpikeTest {

    private static final ReentrantReadWriteLock yieldLock = new ReentrantReadWriteLock();
    private static final int LATENCY_SPIKE = 1200;
    private static final int LATENCY_SPIKE_INTERVAL = 10_000;
    private static final int LATENCY_SPIKES_COUNT = 7;
    private static final int LATENCY_SPIKES_MIN_INTERVAL = 500;
    private static final AtomicBoolean running = new AtomicBoolean(false);


    private static final Logger LOGGER = LoggerFactory.getLogger(StealthPipe.MOD_ID);

    public static void run() {
        if (running.get()) {
            LOGGER.warn("Already running");
            return;
        }
        if (ModState.isClientConnectingToStealthServer.get()) {
            LOGGER.warn("Not running for client");
            return;
        }
        new Thread(() -> {
            try {
                running.set(true);
                while (running.get()) {
                    Thread.sleep(LATENCY_SPIKE_INTERVAL);
                    LOGGER.info("latency spike starting");
                    for (int i = 0; i < LATENCY_SPIKES_COUNT; i++) {
                        yieldLock.writeLock().lock();
                        Thread.sleep(LATENCY_SPIKE);
                        yieldLock.writeLock().unlock();
                        Thread.sleep(LATENCY_SPIKES_MIN_INTERVAL);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Failed to run latency spike test: ", e);
            }

        }).start();
    }

    public static void yield(DataDirection flow) {
        try {
            Thread.sleep(StealthPipe.config.LATENCY_BASELINE);
        } catch (Exception _) {}

        if (flow == DataDirection.SEND && (StealthPipe.config.LATENCY_DIRECTION == LatencySpikeDirection.DIRECTION_SEND_ONLY || StealthPipe.config.LATENCY_DIRECTION == LatencySpikeDirection.DIRECTION_BOTH)) {
            yieldLock.readLock().lock();
            // do nothing
            yieldLock.readLock().unlock();
        } else if (flow == DataDirection.RECEIVE && (StealthPipe.config.LATENCY_DIRECTION == LatencySpikeDirection.DIRECTION_RECV_ONLY || StealthPipe.config.LATENCY_DIRECTION == LatencySpikeDirection.DIRECTION_BOTH)) {
            yieldLock.readLock().lock();
            // do nothing
            yieldLock.readLock().unlock();
        }
    }
}
