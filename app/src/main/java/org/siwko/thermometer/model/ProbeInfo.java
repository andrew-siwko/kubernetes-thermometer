package org.siwko.thermometer.model;

import java.time.Instant;

public class ProbeInfo {
    private String model;
    private String id;
    private String channel;
    private String customName;
    private String displayName;
    private Instant lastTimestamp;
    private Double lastTemperatureF;
    private Long lastAgeSeconds;

    public ProbeInfo() {}

    public ProbeInfo(String model, String id, String channel, String customName, Instant lastTimestamp, Double lastTemperatureF) {
        this.model = model;
        this.id = id;
        this.channel = channel;
        this.customName = customName;
        this.lastTimestamp = lastTimestamp;
        this.lastTemperatureF = lastTemperatureF;
        
        if (customName != null && !customName.trim().isEmpty()) {
            this.displayName = customName.trim();
        } else {
            String label = (model != null ? model : "Unknown");
            if (id != null && !id.trim().isEmpty()) {
                label += " #" + id;
            }
            if (channel != null && !channel.trim().isEmpty()) {
                label += " (Ch " + channel + ")";
            }
            this.displayName = label;
        }

        if (lastTimestamp != null) {
            this.lastAgeSeconds = Instant.now().getEpochSecond() - lastTimestamp.getEpochSecond();
        }
    }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getCustomName() { return customName; }
    public void setCustomName(String customName) { this.customName = customName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Instant getLastTimestamp() { return lastTimestamp; }
    public void setLastTimestamp(Instant lastTimestamp) { this.lastTimestamp = lastTimestamp; }

    public Double getLastTemperatureF() { return lastTemperatureF; }
    public void setLastTemperatureF(Double lastTemperatureF) { this.lastTemperatureF = lastTemperatureF; }

    public Long getLastAgeSeconds() { return lastAgeSeconds; }
    public void setLastAgeSeconds(Long lastAgeSeconds) { this.lastAgeSeconds = lastAgeSeconds; }
}
