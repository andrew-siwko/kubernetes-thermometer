package org.siwko.thermometer.dao;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonNumber;
import org.siwko.thermometer.model.ProbeInfo;
import org.siwko.thermometer.model.ReadingPoint;

import javax.sql.DataSource;
import java.io.StringReader;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class ReadingDao {
    private static final Logger LOGGER = Logger.getLogger(ReadingDao.class.getName());

    @Resource(lookup = "jdbc/sdr433DS")
    private DataSource dataSource;

    @PostConstruct
    public void initSchema() {
        if (dataSource == null) {
            LOGGER.severe("DataSource jdbc/sdr433DS is null!");
            return;
        }
        String sql = "CREATE TABLE IF NOT EXISTS probe_names (" +
                "model VARCHAR(200) NOT NULL, " +
                "id VARCHAR(200) NOT NULL, " +
                "custom_name VARCHAR(200) NOT NULL, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (model, id)" +
                ")";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            LOGGER.info("Initialized probe_names table successfully.");
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize probe_names schema", e);
        }
    }

    public List<ProbeInfo> getProbes() {
        initSchema();
        List<ProbeInfo> probes = new ArrayList<>();
        String sql = "SELECT r.model, r.id, MAX(r.channel) as channel, pn.custom_name, MAX(r.timestamp) as last_ts " +
                "FROM all_readings r " +
                "LEFT JOIN probe_names pn ON r.model = pn.model AND r.id = pn.id " +
                "GROUP BY r.model, r.id, pn.custom_name " +
                "ORDER BY last_ts DESC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String model = rs.getString("model");
                String id = rs.getString("id");
                String channel = rs.getString("channel");
                String customName = rs.getString("custom_name");
                Timestamp ts = rs.getTimestamp("last_ts");
                Instant instant = ts != null ? ts.toInstant() : null;

                // Fetch latest temperature reading for this probe
                Double latestTempF = getLatestTemperatureF(conn, model, id);

                ProbeInfo probe = new ProbeInfo(model, id, channel, customName, instant, latestTempF);
                probes.add(probe);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching probes list", e);
        }
        return probes;
    }

    public void saveProbeName(String model, String id, String customName) {
        initSchema();
        String sql = "INSERT INTO probe_names (model, id, custom_name, updated_at) " +
                "VALUES (?, ?, ?, NOW()) " +
                "ON CONFLICT (model, id) " +
                "DO UPDATE SET custom_name = EXCLUDED.custom_name, updated_at = NOW()";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, model);
            ps.setString(2, id);
            ps.setString(3, customName != null ? customName.trim() : "");
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error saving probe name for " + model + " / " + id, e);
        }
    }

    public List<ReadingPoint> getReadings(String model, String id, int windowMinutes) {
        List<ReadingPoint> points = new ArrayList<>();
        if (windowMinutes <= 0) {
            windowMinutes = 60; // Default to 1 hour (60 mins)
        }

        String sql = "SELECT timestamp, model, id, channel, reading " +
                "FROM all_readings " +
                "WHERE timestamp >= NOW() - (? || ' minutes')::INTERVAL ";

        if (model != null && !model.trim().isEmpty() && !"ALL".equalsIgnoreCase(model)) {
            sql += "AND model = ? ";
        }
        if (id != null && !id.trim().isEmpty() && !"ALL".equalsIgnoreCase(id)) {
            sql += "AND id = ? ";
        }

        sql += "ORDER BY timestamp ASC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int paramIndex = 1;
            ps.setInt(paramIndex++, windowMinutes);

            if (model != null && !model.trim().isEmpty() && !"ALL".equalsIgnoreCase(model)) {
                ps.setString(paramIndex++, model.trim());
            }
            if (id != null && !id.trim().isEmpty() && !"ALL".equalsIgnoreCase(id)) {
                ps.setString(paramIndex++, id.trim());
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("timestamp");
                    Instant instant = ts != null ? ts.toInstant() : null;
                    String rModel = rs.getString("model");
                    String rId = rs.getString("id");
                    String channel = rs.getString("channel");
                    String readingJson = rs.getString("reading");

                    ParsedReading parsed = parseReadingJson(readingJson);
                    if (parsed.tempF != null) {
                        points.add(new ReadingPoint(
                                instant,
                                rModel,
                                rId,
                                channel,
                                parsed.tempF,
                                parsed.tempC,
                                parsed.humidity
                        ));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error fetching readings", e);
        }
        return points;
    }

    private Double getLatestTemperatureF(Connection conn, String model, String id) {
        String sql = "SELECT reading FROM all_readings WHERE model = ? AND id = ? ORDER BY timestamp DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, model);
            ps.setString(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ParsedReading parsed = parseReadingJson(rs.getString("reading"));
                    return parsed.tempF;
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Could not fetch latest temp for " + model + " / " + id, e);
        }
        return null;
    }

    private static class ParsedReading {
        Double tempF;
        Double tempC;
        Double humidity;
    }

    private ParsedReading parseReadingJson(String jsonStr) {
        ParsedReading result = new ParsedReading();
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return result;
        }

        try (JsonReader reader = Json.createReader(new StringReader(jsonStr))) {
            JsonObject json = reader.readObject();

            // Check Fahrenheit keys
            if (json.containsKey("temperature_F")) {
                result.tempF = getDoubleVal(json, "temperature_F");
            } else if (json.containsKey("temperature_1_F")) {
                result.tempF = getDoubleVal(json, "temperature_1_F");
            } else if (json.containsKey("temperature_2_F")) {
                result.tempF = getDoubleVal(json, "temperature_2_F");
            } else if (json.containsKey("temp_F")) {
                result.tempF = getDoubleVal(json, "temp_F");
            }

            // Check Celsius keys
            if (json.containsKey("temperature_C")) {
                result.tempC = getDoubleVal(json, "temperature_C");
            } else if (json.containsKey("temperature_1_C")) {
                result.tempC = getDoubleVal(json, "temperature_1_C");
            } else if (json.containsKey("temperature_2_C")) {
                result.tempC = getDoubleVal(json, "temperature_2_C");
            } else if (json.containsKey("temp_C")) {
                result.tempC = getDoubleVal(json, "temp_C");
            }

            // Convert C to F if F was missing but C was present
            if (result.tempF == null && result.tempC != null) {
                result.tempF = Math.round(((result.tempC * 9.0 / 5.0) + 32.0) * 100.0) / 100.0;
            }

            // Convert F to C if C was missing but F was present
            if (result.tempC == null && result.tempF != null) {
                result.tempC = Math.round(((result.tempF - 32.0) * 5.0 / 9.0) * 100.0) / 100.0;
            }

            if (json.containsKey("humidity")) {
                result.humidity = getDoubleVal(json, "humidity");
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to parse reading JSON: " + jsonStr, e);
        }

        return result;
    }

    private Double getDoubleVal(JsonObject json, String key) {
        try {
            if (json.isNull(key)) return null;
            JsonNumber num = json.getJsonNumber(key);
            return num != null ? num.doubleValue() : Double.parseDouble(json.getString(key));
        } catch (Exception e) {
            try {
                return Double.parseDouble(json.getString(key));
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
