package com.stealthpipe.debug;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class StatisticCounter {

    private final AtomicInteger counterCurrent = new AtomicInteger(0);
    private final AtomicInteger counterLastSecond = new AtomicInteger(0);
    private final AtomicLong lastTickMilli = new AtomicLong(0);

    public StatisticCounter() {
        this.lastTickMilli.set(Instant.now().toEpochMilli());
    }

    public void getAndAdd(int amount) {
        this.counterCurrent.getAndAdd(amount);
    }

    public void update() {
        if (Instant.now().toEpochMilli() - this.lastTickMilli.get() > 1000) {
            this.counterLastSecond.set(this.counterCurrent.get());
            this.counterCurrent.set(0);
            this.lastTickMilli.set(Instant.now().toEpochMilli());
        }
    }

    public int get() {
        return this.counterLastSecond.get();
    }
}
