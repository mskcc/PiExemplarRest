
package org.mskcc.limsrest.limsapi;


import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.velox.api.datamgmtserver.DataMgmtServer;
import com.velox.api.datarecord.*;
import com.velox.api.user.User;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sapioutils.client.standalone.VeloxStandalone;
import com.velox.sapioutils.client.standalone.VeloxStandaloneException;
import com.velox.sapioutils.client.standalone.VeloxStandaloneManagerContext;
import com.velox.sapioutils.client.standalone.VeloxExecutable;
import com.velox.sapioutils.shared.managers.AliquotHelper;

/**
 * A queued task that takes a sample id and a status, and creates a child aliquot
 * 
 * @author Aaron Gabow
 * 
 */
@Service
public class AddChildSample  extends LimsTask 
{
  String sampleId; 
  String status;
  String igoUser; 
  String additionalType;
  String childId;

  public void init(String sampleId, String status, String additionalType, String igoUser, String childId){ 
    this.status = status;
    this.sampleId = sampleId;
    this.igoUser = igoUser;
    this.additionalType = additionalType;
    this.childId = childId;
  }
 //execute the velox call
@PreAuthorize("hasRole('ADMIN')")
@Override
 public Object execute(VeloxConnection conn){
  String newId = "";
  try { 
     List<DataRecord> samps = dataRecordManager.queryDataRecords("Sample", "SampleId = '" + sampleId + "'", user);
    
    if(samps.size() != 1){
       return "ERROR: This service must match exactly one sample"; 
    }
    DataRecord parentSample = samps.get(0);

    if(!childId.equals("NULL")){
        List<DataRecord> destSamps = dataRecordManager.queryDataRecords("Sample", "SampleId = '" + childId + "'", user);
        if(destSamps.size() != 1){
            return "ERROR: If a child sample is specified, it must already exist";
        }
        parentSample.addChild(destSamps.get(0), user);
        dataRecordManager.storeAndCommit(igoUser  + " made " +  childId + " a child sample for " + sampleId, user);
        return "Existing sample " + childId;        
    }

    DataRecord[] childrenSamples = parentSample.getChildrenOfType("Sample", user);
    int max = 0;
    Pattern endPattern = Pattern.compile(".*_(\\d+)$");
    for(int i = 0; i < childrenSamples.length; i++){ 
         String childId = childrenSamples[i].getStringVal("SampleId", user);
         Matcher matcher = endPattern.matcher(childId);
         if(matcher.matches()){
             int ending = Integer.parseInt(matcher.group(1));
             if(ending >= max){
                max = ending;
              }
         }
    }
    max += 1;
    newId = sampleId + "_" + Integer.toString(max);
    DataRecord child =  parentSample.addChild("Sample", user);
    if(!additionalType.equals("NULL")){
        child.addChild(additionalType, user);
    }
    Map<String, Object> parentFields = parentSample.getFields(user);
    child.setDataField("SampleId", newId, user);
    child.setDataField("OtherSampleId", parentFields.get("OtherSampleId"), user);
    child.setDataField("UserSampleID", parentFields.get("UserSampleID"), user); 
    child.setDataField("ExemplarSampleType", parentFields.get("ExemplarSampleType"), user);
    child.setDataField("RequestId", parentFields.get("RequestId"), user);
    child.setDataField("Concentration", parentFields.get("Concentration"), user);
    child.setDataField("ConcentrationUnits", parentFields.get("ConcentrationUnits"), user);
    child.setDataField("TotalMass", parentFields.get("TotalMass"), user);
    child.setDataField("Volume", parentFields.get("Volume"), user);
    child.setDataField("Species", parentFields.get("Species"), user);
    child.setDataField("Preservation", parentFields.get("Preservation"), user);
    child.setDataField("Platform", parentFields.get("Platform"), user);
    child.setDataField("Recipe", parentFields.get("Recipe"), user);
    child.setDataField("IsControl", parentFields.get("IsControl"), user);
    child.setDataField("TumorOrNormal", parentFields.get("TumorOrNormal"), user);
    child.setDataField("ExemplarSampleStatus", status, user);


    dataRecordManager.storeAndCommit(igoUser  + " made a child sample for " + sampleId, user);


  } catch (Throwable e) {
          StringWriter sw = new StringWriter();
          PrintWriter pw = new PrintWriter(sw);
          e.printStackTrace(pw);
           return "ERROR IN ADDING CHILD SAMPLE: " + e.getMessage() + "TRACE: " + sw.toString();   
  
  }

  return "New sample "+ newId ; 
 }

}