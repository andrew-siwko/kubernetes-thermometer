package org.siwko.thermometer.model;

import java.time.Instant;

public class ReadingPoint {
    private String timestamp;
    private long epochMillis;
    private String model;
    private String id;
    private String channel;
    private Double temperatureF;
    private Double temperatureC;
    private Double humidity;
    private long ageSeconds;

    public ReadingPoint() {}

    public ReadingPoint(Instant instant, String model, String id, String channel, Double temperatureF, Double temperatureC, Double humidity) {
        if (instant != null) {
            this.timestamp = instant.toString();
            this.epochMillis = instant.toEpochMilli();
            this.ageSeconds = Instant.now().getEpochSecond() - instant.getEpochSecond();
        }
        this.model = model;
        this.id = id;
        this.channel = channel;
        this.temperatureF = temperatureF;
        this.temperatureC = temperatureC;
        this.humidity = humidity;
    }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public long getEpochMillis() { return epochMillis; }
    public void setEpochMillis(long epochMillis) { this.epochMillis = epochMillis; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public Double getTemperatureF() { return temperatureF; }
    public void setTemperatureF(Double temperatureF) { this.temperatureF = temperatureF; }

    public Double getTemperatureC() { return temperatureC; }
    public void setTemperatureC(Double temperatureC) { this.temperatureC = temperatureC; }

    public Double getHumidity() { return humidity; }
    public void setHumidity(Double humidity) { this.humidity = humidity; }

    public long getAgeSeconds() { return ageSeconds; }
    public void setAgeSeconds(long ageSeconds) { this.ageSeconds = ageSeconds; }
}
