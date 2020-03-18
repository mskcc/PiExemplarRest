package org.mskcc.limsrest.service;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.servermanager.PickListManager;
import com.velox.api.user.User;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mskcc.limsrest.ConnectionLIMS;
import org.mskcc.limsrest.service.samplemetadata.SampleMetadata;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.*;

public class GetSampleMetadataTask {

    private Log log = LogFactory.getLog(GetWESSampleDataTask.class);
    private String timestamp;
    private ConnectionLIMS conn;
    private User user;
    private final List<String> NUCLEIC_ACID_TYPES = Arrays.asList("dna", "rna", "cfdna", "amplicon", "cdna");
    private final List<String> LIBRARY_SAMPLE_TYPES = Arrays.asList("dna library", "cdna library", "pooled library");
    private final List<String> VALID_SAMPLETYPES = Arrays.asList("dna", "rna", "cdna", "cfdna", "amplicon", "dna library", "cdna library", "pooled library");
    private final List<String> SAMPLETYPES_IN_ORDER = Arrays.asList("dna", "rna", "cdna", "amplicon", "dna library", "cdnalibrary", "pooled library");
    public GetSampleMetadataTask(String timestamp, ConnectionLIMS conn) {
        this.timestamp = timestamp;
        this.conn = conn;
    }

    public List<SampleMetadata> execute() {
        long start = System.currentTimeMillis();
        try {
            VeloxConnection vConn = conn.getConnection();
            user = vConn.getUser();
            DataRecordManager dataRecordManager = vConn.getDataRecordManager();
            PickListManager pickListManager = vConn.getDataMgmtServer().getPickListManager(user);
            log.info(" Starting GetSampleMetadata task using timestamp " + timestamp);
            List<DataRecord> requests = new ArrayList<>();
            try {
                //requests = dataRecordManager.queryDataRecords("Request", "DateCreated > " + Long.parseLong(timestamp) , user);
                requests = dataRecordManager.queryDataRecords("Request", "RequestId = ", user);//for testing
                log.info("Num Request Records: " + requests.size());

                List<SampleMetadata> sampleMetadata = new ArrayList<>();
                for (DataRecord req: requests){
                    DataRecord[] samples = req.getChildrenOfType("Sample", user);
                    for (DataRecord sample: samples){
                        String mrn ="";
                        String cmoPatientId = (String)getFallBackValue(sample, "CorrectedInvestPatientId", "String");
                        String cmoSampleId = (String)getFallBackValue(sample, "CorrectedCMOID", "String");
                        String igoId = sample.getStringVal("SampleId", user);
                        String investigatorSampleId = (String)getFallBackValue(sample, "UserSampleID", "String");
                        String species = (String)getFallBackValue(sample, "Species", "String");
                        String sex = (String)getFallBackValue(sample, "Gender", "String");
                        String tumorOrNormal = (String)getFallBackValue(sample, "TumorOrNormal", "String");
                        String sampleType = (String)getValueFromDataRecord(sample, "ExemplarSampleType", "SampleType");
                        String preservation = (String)getFallBackValue(sample, "TumorOrNormal", "String");;
                        String tumorType = (String)getFallBackValue(sample,"TumorType", "String");
                        String parentTumorType = getOncotreeType(tumorType);
                        String specimenType = (String)getFallBackValue(sample,"SpecimenType", "String");
                        String sampleOrigin = (String)getFallBackValue(sample,"SpecimenType", "String");
                        String tissueSource = (String)getFallBackValue(sample,"SpecimenType", "String");
                        String tissueLocation = (String)getFallBackValue(sample,"SpecimenType", "String");
                        String recipe = (String)getFallBackValue(sample,"SpecimenType", "String");
                        String baitset = (String)getFallBackValue(sample,"SpecimenType", "String");
                        String fastqPath ="";
                        String ancestorSample = getOriginSampleId(sample);
                        boolean doNotUse = false;
                        String sampleStatus = getSampleStatus(sample);

                        SampleMetadata metadata = new SampleMetadata(mrn, cmoPatientId, cmoSampleId, investigatorSampleId, igoId, species, sex, tumorOrNormal, sampleType, preservation,
                                tumorType, parentTumorType, specimenType, sampleOrigin, tissueSource, tissueLocation, recipe, baitset, fastqPath, ancestorSample, doNotUse, sampleStatus);
                        sampleMetadata.add(metadata);
                    }
                }
            } catch (Throwable e) {
                log.error(e.getMessage(), e);
                return null;
            }

        }catch (Exception e){
            log.error(e.getMessage());
        }
        return null;
    }

    private boolean isValidSampleType(DataRecord sample){
        try{
            String sampleType = sample.getStringVal("ExemplarSampleType", user);
            return VALID_SAMPLETYPES.contains(sampleType.toLowerCase());
        }catch (Exception e){
            log.error(e.getMessage());
        }
        return false;
    }

    /**
     * Get a DataField value from a DataRecord.
     *
     * @param record
     * @param fieldName
     * @param fieldType
     * @return Object
     * @throws NotFound
     * @throws RemoteException
     */
    private Object getValueFromDataRecord(DataRecord record, String fieldName, String fieldType) throws NotFound, RemoteException {
        if (record == null) {
            return "";
        }
        if (record.getValue(fieldName, user) != null) {
            if (fieldType.equals("String")) {
                return record.getStringVal(fieldName, user);
            }
            if (fieldType.equals("Integer")) {
                return record.getIntegerVal(fieldName, user);
            }
            if (fieldType.equals("Long")) {
                return record.getLongVal(fieldName, user);
            }
            if (fieldType.equals("Double")) {
                return record.getDoubleVal(fieldName, user);
            }
            if (fieldType.equals("Date")) {
                SimpleDateFormat dateFormatter = new SimpleDateFormat("MM-dd-yyyy");
                return dateFormatter.format(new Date(record.getDateVal(fieldName, user)));
            }
        }
        return "";
    }

    private DataRecord getRelatedCmoInfoRec(DataRecord sample){
        String sampleId="";
        try{
            sampleId = sample.getStringVal("SampleId", user);
            if (sample.getChildrenOfType("SampleCMOInfoRecords", user).length>0){
                return sample.getChildrenOfType("SampleCMOInfoRecords", user)[0];
            }
            Stack<DataRecord> sampleStack = new Stack<>();
            if (sample.getParentsOfType("Sample", user).size() > 0) {
                sampleStack.addAll((sample.getParentsOfType("Sample", user)));
            }
            do {
                DataRecord startSample = sampleStack.pop();
                if (startSample.getChildrenOfType("SampleCMOInfoRecords", user).length > 0) {
                    return startSample.getChildrenOfType("SampleCMOInfoRecords", user)[0];
                }
                if (startSample.getParentsOfType("Sample", user).size() > 0) {
                    sampleStack.addAll(startSample.getParentsOfType("Sample", user));
                }
            } while (!sampleStack.isEmpty());
        } catch (Exception e) {
            log.error(String.format("Error occured while finding related SampleCMOInfoRecords for Sample %s", sampleId));
        }
        return null;
    }

    private Object getFallBackValue(DataRecord sample, String fieldName, String fieldType ) {
        String sampleId="";
        try {
            sampleId = sample.getStringVal("SampleId", user);
            DataRecord cmoInfoRecord = getRelatedCmoInfoRec(sample);
            if (cmoInfoRecord != null) {
                return getValueFromDataRecord(cmoInfoRecord, fieldName, fieldType);
            }
            return getValueFromDataRecord(sample, fieldName, fieldType);
        } catch (Exception e) {
            log.error(String.format("Error getting '%s' value for sample '%s' from related samples or SampleCMOInfoRecords", fieldName, sampleId));
        }
        return "";
    }

    /**
     * Get MainCancerType from oncotree
     *
     * @param tumorType
     * @return String
     */
    private String getOncotreeType(String tumorType) {
        StringBuffer response = new StringBuffer();
        JSONArray oncotreeResponseData = null;
        String mainTumorType = "";
        try {
            URL url = new URL("http://oncotree.mskcc.org/api/tumorTypes/search/name/" + tumorType.split("/")[0].replace(" ", "%20") + "?exactMatch=false");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            con.disconnect();
            oncotreeResponseData = new JSONArray(response.toString());
        } catch (Exception e) {
            log.error(String.format("Error occured while querying oncotree end point for Tumor Type %s\n%s", tumorType, Arrays.toString(e.getStackTrace())));
            return "";
        }
        if (oncotreeResponseData.length() > 0) {
            try {
                JSONObject rec = oncotreeResponseData.getJSONObject(0);
                mainTumorType = rec.getString("mainType");
                log.info(mainTumorType);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            mainTumorType = "Oncotree Tumor Type not found.";
        }
        return mainTumorType;
    }

    private String getOriginSampleId(DataRecord sample){
        String sampleId="";
        try{
            sampleId = sample.getStringVal("SampleId", user);
            if (sample.getChildrenOfType("SampleCMOInfoRecords", user).length>0){
                return sample.getStringVal("SampleId", user);
            }
            Stack<DataRecord> sampleStack = new Stack<>();
            if (sample.getParentsOfType("Sample", user).size() > 0) {
                sampleStack.addAll((sample.getParentsOfType("Sample", user)));
            }
            do {
                DataRecord startSample = sampleStack.pop();
                if (startSample.getChildrenOfType("SampleCMOInfoRecords", user).length > 0) {
                    return sample.getStringVal("SampleId", user);
                }
                if (startSample.getParentsOfType("Sample", user).size() > 0) {
                    sampleStack.addAll(startSample.getParentsOfType("Sample", user));
                }
            } while (!sampleStack.isEmpty());
        } catch (Exception e) {
            log.error(String.format("Error occured while finding related SampleCMOInfoRecords for Sample %s", sampleId));
        }
        return "";
    }

    private String getSampleStatus(DataRecord sample){
        String requestId;
        String sampleId ;
        String status;
        String sampleType;
        String sampleStatus;
        try{
            requestId = (String) getValueFromDataRecord(sample, "RequstId", "String");
            sampleId = sample.getStringVal("SampleId", user);
            status= (String) getValueFromDataRecord(sample, "ExemplarSampleStatus", "String");
            sampleType = (String)getValueFromDataRecord(sample, "ExemplarSampleType", "String");;
            sampleStatus = (String)getValueFromDataRecord(sample, "ExemplarSampleType", "String");;
            int statusOrder=-1;
            long recordId = 0;
            String overAllSampleStatus = "";

            Stack<DataRecord> sampleStack = new Stack<>();
            sampleStack.add(sample);
            do{
                DataRecord current = sampleStack.pop();
                String currentSampleType = (String)getValueFromDataRecord(current, "ExemplarSampleType", "String");
                String currentSampleStatus = (String)getValueFromDataRecord(current, "ExemplarSampleStatus", "String");
                int currentStatusOrder = SAMPLETYPES_IN_ORDER.indexOf(currentSampleType.toLowerCase());
                long currentRecordId = current.getRecordId();
                if (isSequencingComplete(current) && currentRecordId > recordId){
                    recordId = currentRecordId;
                    sampleStatus = "Completed Sequencing";
                }
                if (currentRecordId > recordId && currentStatusOrder > statusOrder && isCompleteStatus(currentSampleStatus)){
                    sampleStatus = resolveCurrentStatus(currentSampleStatus, currentSampleType);
                    recordId = currentRecordId;
                }

            }while(sampleStack.size()>0);
        }catch (Exception e){
            log.error(String.format("Error while getting status for sample '%s'.", sample));
            return "";
        }
        return sampleStatus;
    }

    private boolean isCompleteStatus(String status){
        if (status.toLowerCase().contains("completed")){
            return true;
        }
        return false;
    }

    private boolean isSequencingCompleteStatus(String status){
        status = status.toLowerCase();
        if (status.contains("completed - ") && status.contains("illumina") && status.contains("sequencing")){
            return true;
        }
        return false;
    }

    private String resolveCurrentStatus(String status, String sampleType) {
        if (NUCLEIC_ACID_TYPES.contains(sampleType.toLowerCase()) && status.toLowerCase().contains("completed -") && status.toLowerCase().contains("extraction") && status.toLowerCase().contains("dna/rna simultaneous") ) {
            return String.format("Completed - %s Extraction", sampleType.toUpperCase());
        }
        if (NUCLEIC_ACID_TYPES.contains(sampleType.toLowerCase()) && status.toLowerCase().contains("completed -") && status.toLowerCase().contains("extraction") && status.toLowerCase().contains("rna") ) {
            return "Completed - RNA Extraction";
        }
        if (NUCLEIC_ACID_TYPES.contains(sampleType.toLowerCase()) && status.toLowerCase().contains("completed -") && status.toLowerCase().contains("extraction") && status.toLowerCase().contains("dna") ) {
            return "Completed - DNA Extraction";
        }
        if (NUCLEIC_ACID_TYPES.contains(sampleType.toLowerCase()) && status.toLowerCase().contains("completed -") && status.toLowerCase().contains("quality control")) {
            return "Completed - Quality Control";
        }
        if (LIBRARY_SAMPLE_TYPES.contains(sampleType.toLowerCase()) && status.toLowerCase().contains("completed") && status.toLowerCase().contains("library preparation")) {
            return "Completed - Library Preparaton";
        }
        if (LIBRARY_SAMPLE_TYPES.contains(sampleType.toLowerCase()) && isSequencingCompleteStatus(status)){
            return "Completed - Sequencing";
        }
        return "";
    }

    private Boolean isSequencingComplete(DataRecord sample){
        try {
            List<DataRecord> seqAnalysisRecords = Arrays.asList(sample.getChildrenOfType("SeqAnalysisSampleQC", user));
            if (seqAnalysisRecords.size()>0) {
                Object sequencingStatus = seqAnalysisRecords.get(0).getValue("SeqQCStatus", user);
                if (sequencingStatus != null && (sequencingStatus.toString().equalsIgnoreCase("passed") || sequencingStatus.toString().equalsIgnoreCase("failed"))){
                    return true;
                }
            }
        }catch (Exception e){
            log.error(e.getMessage());
            return false;
        }
        return false;
    }

}
