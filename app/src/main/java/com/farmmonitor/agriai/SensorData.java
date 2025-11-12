package com.farmmonitor.agriai;

public class SensorData {
    private Double temperature;
    private Double humidity;
    private Double soilMoisture;
    private Double lightLevel;
    private Long lastUpdate;

    public SensorData() {
        // Default constructor required for calls to DataSnapshot.getValue(SensorData.class)
    }

    public Double getTemperature() { return temperature; }
    public Double getHumidity() { return humidity; }
    public Double getSoilMoisture() { return soilMoisture; }
    public Double getLightLevel() { return lightLevel; }
    public Long getLastUpdate() { return lastUpdate; }
}