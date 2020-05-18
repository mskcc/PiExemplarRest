package org.mskcc.limsrest.service;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.user.User;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mskcc.limsrest.ConnectionLIMS;
import org.mskcc.limsrest.service.requesttracker.*;

import java.rmi.RemoteException;
import java.util.*;
import java.util.stream.Collectors;

import static org.mskcc.limsrest.service.requesttracker.StatusTrackerConfig.*;
import static org.mskcc.limsrest.util.DataRecordAccess.*;

public class GetRequestTrackingTask {
    private static Log log = LogFactory.getLog(GetRequestTrackingTask.class);
    private static Integer SAMPLE_COUNT = 1;
    private static String[] requestDataLongFields = new String[]{"ReceivedDate"};
    private static String[] requestDataStringFields = new String[]{
            "LaboratoryHead",
            "GroupLeader",
            "TATFromInProcessing",
            "TATFromReceiving",
            "ProjectManager",
            "LabHeadEmail",
            "Investigator"
    };
    private ConnectionLIMS conn;
    private String requestId;

    public GetRequestTrackingTask(String requestId, ConnectionLIMS conn) {
        this.requestId = requestId;
        this.conn = conn;
    }

    public Map<String, Object> execute() {
        try {
            VeloxConnection vConn = conn.getConnection();
            User user = vConn.getUser();
            DataRecordManager drm = vConn.getDataRecordManager();

            String serviceId = getBankedSampleServiceId(this.requestId, user, drm);
            Request request = new Request(requestId, serviceId);

            if (serviceId != null && !serviceId.equals("")) {
                // Add "submitted" stage if a serviceID exists
                // TODO - Why is this the case (QA: "06302_W")
                // TODO - Place in thread as this can be executed independently
                SampleStageTracker submittedStage = getSubmittedStage(serviceId, user, drm);
                request.addStage("submitted", submittedStage);
            }

            // Validate request record
            List<DataRecord> requestRecordList = drm.queryDataRecords("Request", "RequestId = '" + requestId + "'", user);
            if (requestRecordList.size() != 1) {  // error: request ID not found or more than one found
                log.error(String.format("Request %s not found for requestId %s. Returning incomplete information", requestId));
                return request.toApiResponse();
            }
            DataRecord requestRecord = requestRecordList.get(0);
            
            Map<String, Object> metaData = getMetaDataFromRecord(requestRecord, user);
            request.setMetaData(metaData);

            // Immediate samples of record represent physical samples. LIMS creates children of these in the workflow
            DataRecord[] samples = requestRecord.getChildrenOfType("Sample", user);

            // Create the tree of each ProjectSample aggregating per-sample status/stage information
            for (DataRecord record : samples) {
                ProjectSampleTree tree = createProjectSampleTree(record, user);
                ProjectSample projectSample = tree.convertToProjectSample();
                request.addTrackedSample(projectSample);
            }

            // TODO - Make input List<ProjectSample> and explicitly show that aggregateStages are being added to request
            aggregateStageInfoForRequest(request);

            Map<String, Object> apiResponse = new HashMap<>();
            apiResponse.put("request", request.toApiResponse());
            apiResponse.put("requestId", this.requestId);
            apiResponse.put("serviceId", serviceId);

            return apiResponse;
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    /**
     * Summarizes stage-level information about the request using each sample in the request
     *
     * @param request
     */
    private void aggregateStageInfoForRequest(Request request){
        // Aggregate flattened stage information for each sample in the request
        List<SampleStageTracker> requestStages = request.getSamples().stream()
                .flatMap(tracker -> tracker.getStages().stream())
                .collect(Collectors.toList());
        Map<String, SampleStageTracker> requestStagesMap = aggregateStages(requestStages);
        for (Map.Entry<String, SampleStageTracker> requestStage : requestStagesMap.entrySet()) {
            request.addStage(requestStage.getKey(), requestStage.getValue());
        }
    }

    /**
     * Populates input @tree w/ based on the input @root sample & its children.
     * Assumptions -
     *  SAMPLES (WorkflowSample)
     *      STATUSES
     *      I) PENDING
     *          - WorkflowSamples default to Pending, i.e. complete = false on initialization
     *      II) FAILED
     *          - WorkflowSamples are evaluated for failure on initialization based on their initialized status
     *          - Failed WorkflowSamples ARE considered complete (different from Stage)
     *          - All parent WorkflowSamples from a failed leaf are marked failed until a parent w/ non-failed children
     *          - All WorkflowSamples in a path to a Non-failed leaf sample should not be failed
     *          - If the root WorkflowSample has not failed, then the ProjectSample has not failed, i.e. there only
     *            needs to be one non-failed leaf sample in the tree for the whole tree to have not failed
     *      III) COMPLETE
     *          - WorkflowSample w/ child is completed b/c moving on in the workflow creates a child sample
     *          - WorkflowSample w/o child samples is incomplete, unless failed or has status indicating completion
     *      OTHER
     *      - Samples that are awaiting processing or have an ambiguous status will take their stage from their parent
     *
     *  STAGES (SampleStageTracker)
     *      INITIALIZATION
     *          - Each WorkflowSample will add/update a SampleStageTracker instance in the tree
     *          - SampleStageTracker instances default to complete
     *      STATUSES
     *      I) COMPLETE
     *          - SampleStageTracker instances default to complete
     *          - SampleStageTracker instances' completion status can only be updated by a leaf WorkflowSamples of
     *            that stage
     *      II) INCOMPLETE
     *          - Leaf WorkflowSamples can only set the stage to incomplete if it is non-failed and is incomplete as
     *            determined by StatusTrackerConfig::isCompletedStatus. Note - non-failed WorkflowSamples are considered
     *            to have "completed" that stage
     *
     * @param root - Enriched model of a Sample record's data
     * @param tree - Data Model for tracking Sample Stages, samples, and the root node
     * @return
     */
    private ProjectSampleTree createWorkflowTree(WorkflowSample root, ProjectSampleTree tree){
        // Update the tracked stages with the input sample's stage
        tree.addStageToTracked(root);

        // Search each child of the input
        DataRecord[] children = new DataRecord[0];
        try {
            children = root.getRecord().getChildrenOfType("Sample", tree.getUser());
        } catch (IoError | RemoteException e) { /* Expected - No more children of the sample */ }

        if (children.length == 0) {
            tree.updateTreeOnLeafStatus(root);
            return tree;
        } else {
            // Sample w/ children is complete
            root.setComplete(Boolean.TRUE);

            // Add all data for the root's children at that level. Allows us to fail only the failed branch
            List<WorkflowSample> workflowChildren = new ArrayList<>();
            for(DataRecord record : children){
                WorkflowSample sample = new WorkflowSample(record, tree.getUser());
                sample.setParent(root);
                if(STAGE_UNKNOWN.equals(sample.getStage()) || STAGE_AWAITING_PROCESSING.equals(sample.getStage())){
                    // Update the stage of the sample to the parent stage if it is unknown
                    if(!STAGE_UNKNOWN.equals(root.getStage())){
                        sample.setStage(root.getStage());
                    }
                }
                root.addChild(sample);
                tree.addSample(sample);
                workflowChildren.add(sample);
            }

            for(WorkflowSample sample : workflowChildren){
                // Update tree w/ each sample
                log.info(String.format("Searching children of data record ID: %d", root.getRecordId()));
                tree = createWorkflowTree(sample, tree);
            }
        }
        return tree;
    }

    /**
     * Creates data model of the tree for the DataRecord corresponding to a ProjectSample
     *
     * @param record
     * @param user
     * @return
     */
    private ProjectSampleTree createProjectSampleTree(DataRecord record, User user) {
        // Initialize input
        WorkflowSample root = new WorkflowSample(record, user);
        ProjectSampleTree rootTree = new ProjectSampleTree(root, user);
        rootTree.addSample(root);

        // Recursively create the workflowTree from the input tree
        ProjectSampleTree workflowTree = createWorkflowTree(root, rootTree);

        return workflowTree;
    }

    /**
     * Calculates the stage the overall sample is at based on the least advanced path
     *
     * @param sampleStages - List of SampleStageTracker instances representing one stage of one sample
     * @return
     */
    public Map<String, SampleStageTracker> aggregateStages(List<SampleStageTracker> sampleStages) {
        Map<String, SampleStageTracker> stageMap = new TreeMap<>(new StatusTrackerConfig.StageComp());
        if (sampleStages.size() == 0) return stageMap;

        String stageName;
        SampleStageTracker projectStage;    // SampleStageTracker of the project created from aggregated sampleStages
        Boolean isFailedStage;
        for (SampleStageTracker sampleStage : sampleStages) {
            stageName = sampleStage.getStage();
            if (stageMap.containsKey(stageName)) {
                projectStage = stageMap.get(stageName);
                projectStage.updateStageTimes(sampleStage);
                projectStage.addStartingSample(SAMPLE_COUNT);
                isFailedStage = sampleStage.getFailedSamplesCount() > 0;
                if (sampleStage.getComplete() && !isFailedStage) {
                    // Only non-failed, completed stages are considered to have "ended" the stage
                    projectStage.addEndingSample(SAMPLE_COUNT);
                }
                // Incremement the number of failed samples in the aggregated
                if (isFailedStage){
                    projectStage.addFailedSample();
                }
            } else {
                Integer endingCount = sampleStage.getComplete() ? SAMPLE_COUNT : 0;
                projectStage = new SampleStageTracker(stageName, SAMPLE_COUNT, endingCount, sampleStage.getStartTime(), sampleStage.getUpdateTime());
                stageMap.put(stageName, projectStage);
            }
        }

        // Calculate completion status of stage based on whether ending + failed equals total size
        stageMap.values().forEach(
                tracker -> {
                    final Integer completedCount = tracker.getEndingSamples() + tracker.getFailedSamplesCount();
                    tracker.setComplete(completedCount == tracker.getSize());
                }
        );

        return stageMap;
    }

    /**
     * Retrieves the Submitted Stage of the sample
     *
     * @param serviceId
     * @param user
     * @param drm
     * @return
     */
    private SampleStageTracker getSubmittedStage(String serviceId, User user, DataRecordManager drm) {
        Map<String, Integer> tracker = new HashMap<>();

        String query = String.format("ServiceId = '%s'", serviceId);
        List<DataRecord> bankedList = new ArrayList<>();
        try {
            bankedList = drm.queryDataRecords("BankedSample", query, user);
        } catch (NotFound | IoError | RemoteException e) {
            log.info(String.format("Could not find BankedSample record for %s", serviceId));
            return null;
        }

        Boolean wasPromoted;
        Integer total;
        Integer promoted;
        for (DataRecord record : bankedList) {
            // Increment total
            total = tracker.computeIfAbsent("Total", k -> 0);
            tracker.put("Total", total + 1);

            // Increment promoted if sample was promoted
            promoted = tracker.computeIfAbsent("Promoted", k -> 0);
            wasPromoted = getRecordBooleanValue(record, "Promoted", user);
            if (wasPromoted) {
                tracker.put("Promoted", promoted + 1);
            }
        }

        total = tracker.get("Total");
        promoted = tracker.get("Promoted");

        SampleStageTracker requestStage = new SampleStageTracker("submitted", total, promoted, null, null);
        Boolean submittedComplete = total == promoted;
        if (submittedComplete) {
            requestStage.setComplete(Boolean.TRUE);
        } else {
            requestStage.setComplete(Boolean.FALSE);
        }

        return requestStage;
    }

    /**
     * Returns the serviceId for the input requestId
     *
     * @param requestId
     * @param user
     * @param drm
     * @return
     */
    public String getBankedSampleServiceId(String requestId, User user, DataRecordManager drm) {
        String query = String.format("RequestId = '%s'", requestId);
        List<DataRecord> bankedList = new ArrayList<>();
        try {
            bankedList = drm.queryDataRecords("BankedSample", query, user);
        } catch (NotFound | IoError | RemoteException e) {
            log.info(String.format("Could not find BankedSample records w/ RequestId: %s", requestId));
            return null;
        }

        Set<String> serviceIds = new HashSet<>();
        String serviceId;
        for (DataRecord record : bankedList) {
            serviceId = getRecordStringValue(record, "ServiceId", user);
            serviceIds.add(serviceId);
        }

        if (serviceIds.size() != 1) {
            String error = String.format("Failed to retrieve serviceId for RequestId: %s. ServiceIds Returned: %s",
                    requestId, serviceIds.toString());
            log.error(error);
            return "";
        }

        return new ArrayList<>(serviceIds).get(0);
    }

    /**
     * Populates the metaData for the request using the Record from the Request DataType
     *
     * @param requestRecord - DataRecord from the Request DataType
     * @param user
     * @return
     */
    private Map<String, Object> getMetaDataFromRecord(DataRecord requestRecord, User user){
        Map<String, Object> requestMetaData = new HashMap<>();
        for (String field : requestDataStringFields) {
            requestMetaData.put(field, getRecordStringValue(requestRecord, field, user));
        }
        for (String field : requestDataLongFields) {
            requestMetaData.put(field, getRecordLongValue(requestRecord, field, user));
        }

        // IGO Completion is confirmed by delivery, which sets the "RecentDeliveryDate" field
        final Long mostRecentDeliveryDate = getRecordLongValue(requestRecord, "RecentDeliveryDate", user);
        if(mostRecentDeliveryDate != null){
            requestMetaData.put("isIgoComplete", true);
        }
        requestMetaData.put("RecentDeliveryDate", mostRecentDeliveryDate);

        return requestMetaData;
    }

    /**
     * Projects are considered IGO-Complete if they have a delivery date
     *
     * @param requestRecord
     * @param user
     * @return
     */
    private Boolean isIgoComplete(DataRecord requestRecord, User user) {
        return getRecordLongValue(requestRecord, "RecentDeliveryDate", user) != null;
    }
}