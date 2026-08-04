package org.siwko.thermometer.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.siwko.thermometer.dao.ReadingDao;
import org.siwko.thermometer.model.ProbeInfo;
import org.siwko.thermometer.model.ReadingPoint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProbeResource {

    @Inject
    private ReadingDao readingDao;

    @GET
    @Path("/probes")
    public List<ProbeInfo> getProbes() {
        return readingDao.getProbes();
    }

    public static class NameUpdateRequest {
        public String model;
        public String id;
        public String customName;
    }

    @POST
    @Path("/probes/name")
    public Response updateProbeName(NameUpdateRequest req) {
        if (req == null || req.model == null || req.id == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Model and ID are required"))
                    .build();
        }

        readingDao.saveProbeName(req.model, req.id, req.customName);
        return Response.ok(Map.of("status", "success", "message", "Probe name updated")).build();
    }

    @GET
    @Path("/readings")
    public List<ReadingPoint> getReadings(
            @QueryParam("model") String model,
            @QueryParam("id") String id,
            @QueryParam("window") @DefaultValue("60") String windowParam) {

        int minutes = parseWindowToMinutes(windowParam);
        return readingDao.getReadings(model, id, minutes);
    }

    @GET
    @Path("/health")
    public Response getHealth() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "kubernetes-thermometer-probe");
        return Response.ok(status).build();
    }

    private int parseWindowToMinutes(String windowParam) {
        if (windowParam == null || windowParam.trim().isEmpty()) {
            return 60; // 1 hour default
        }
        String p = windowParam.trim().toLowerCase();
        try {
            if (p.endsWith("m")) {
                return Integer.parseInt(p.replace("m", ""));
            } else if (p.endsWith("h")) {
                return Integer.parseInt(p.replace("h", "")) * 60;
            } else if (p.endsWith("d")) {
                return Integer.parseInt(p.replace("d", "")) * 1440;
            } else {
                return Integer.parseInt(p);
            }
        } catch (NumberFormatException e) {
            return 60;
        }
    }
}
