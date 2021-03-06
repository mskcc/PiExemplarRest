package org.mskcc.limsrest.service.requesttracker;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.*;
import java.util.stream.Collectors;

import static org.mskcc.limsrest.util.StatusTrackerConfig.StageComp;

/**
 * API representation of a Request in the LIMs with tracking data
 *
 * @author David Streid
 */
public class Request {
    private static Log log = LogFactory.getLog(Request.class);

    private String requestId;
    private Map<String, StageTracker> stages;
    private List<ProjectSample> samples;                // Tree of samples
    private Map<String, Object> metaData;               // Summary of metaData
    private Map<String, Object> summary;                // Summary of overall project status

    public Request(String requestId) {
        this.requestId = requestId;
        this.samples = new ArrayList<>();
        this.stages = new TreeMap<>(new StageComp());
        this.metaData = new HashMap<>();
    }


    public void setMetaData(Map<String, Object> metaData) {
        this.metaData = metaData;
    }

    public void setSummary(Map<String, Object> summary) {
        this.summary = summary;
    }

    /**
     * Adds a stage to the request
     *
     * @param stageName
     * @param stage
     */
    public void addStage(String stageName, StageTracker stage) {
        if (this.stages.containsKey(stageName)) {
            log.warn(String.format("Overriding stage: %s recorded for record: %s", stageName, this.requestId));
        }
        this.stages.put(stageName, stage);
    }

    public Map<String, StageTracker> getStages() {
        Map<String, StageTracker> cloned = new TreeMap<>(new StageComp());
        for(Map.Entry<String, StageTracker> entry : this.stages.entrySet()){
            cloned.put(entry.getKey(), entry.getValue());
        }
        return cloned;

    }

    public List<ProjectSample> getSamples() {
        return samples;
    }

    public void setSamples(List<ProjectSample> samples) {
        this.samples = samples;
    }

    public Map<String, Object> toApiResponse() {
        Map<String, Object> apiResponse = new HashMap<>();

        apiResponse.put("requestId", this.requestId);
        apiResponse.put("summary", this.summary);
        apiResponse.put("metaData", this.metaData);
        apiResponse.put("samples", this.samples.stream().map(tracker -> tracker.toApiResponse()).collect(Collectors.toList()));
        apiResponse.put("stages", this.stages.values().stream().map(
                stage -> stage.toApiResponse(false)
        ).collect(Collectors.toList()));

        return apiResponse;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
