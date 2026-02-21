package com.stealthpipe;

import java.util.ArrayDeque;

public class RoundTripTimeMonitor {
    private final ArrayDeque<Double> rttWindow = new ArrayDeque<>();
    private final int windowSize;
    private final double avgThreshold;
    private final double stdThreshold;

    public RoundTripTimeMonitor(int windowSize, double avgThreshold, double stdThreshold) {
        this.windowSize = windowSize;
        this.avgThreshold = avgThreshold;
        this.stdThreshold = stdThreshold;
    }


    // Add new RTT sample
    public void addSample(double rtt) {
        if (rttWindow.size() >= windowSize) {
            rttWindow.pollFirst(); // remove oldest
        }
        rttWindow.addLast(rtt);
    }

    // Compute rolling average
    public double getAverage() {
        if (rttWindow.isEmpty()) return 0;
        double sum = 0;
        for (double rtt : rttWindow) sum += rtt;
        return sum / rttWindow.size();
    }

    // Compute rolling standard deviation
    public double getStdDev() {
        if (rttWindow.isEmpty()) return 0;
        double mean = getAverage();
        double variance = 0;
        for (double rtt : rttWindow) {
            variance += (rtt - mean) * (rtt - mean);
        }
        variance /= rttWindow.size();
        return Math.sqrt(variance);
    }

    // Determine if connection is unstable
    public boolean isUnstable() {
        double avg = getAverage();
        double std = getStdDev();
        return avg > avgThreshold || std > stdThreshold;
    }
}
