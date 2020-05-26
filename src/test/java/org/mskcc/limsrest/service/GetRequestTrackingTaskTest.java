package org.mskcc.limsrest.service;

import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mskcc.limsrest.ConnectionLIMS;

import java.rmi.RemoteException;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GetRequestTrackingTaskTest {
    ConnectionLIMS conn;

    @Before
    public void setup() {
        this.conn = new ConnectionLIMS("tango.mskcc.org", 1099, "fe74d8e1-c94b-4002-a04c-eb5c492704ba", "test-runner", "password1");
    }

    @After
    public void tearDown() {
        this.conn.close();
    }


    public static final Set<String> TEST_THESE_PROJECTS = new HashSet<>(Arrays.asList(
            // ALL PASSED
            "10795",        // Good:    BASIC                           3 IGO-Complete
            "09443_AS",		// Good:    BASIC                           8 IGO-complete
            "09602_F",		// Good:    Multiple successful Banches     12 IGO-complete
            "09367_K",		// Good:    Failed branches                 1 IGO-Complete
            "07428_AA",     // Good:    Includes extraction             4 IGO-Complete

            // Passed/Pending
            "10793",        // Good: 9 Passed, 1 Pending

            // Failed/Complete
            "06302_W",		// Good: 1 Failed Library Prep, 41 IGO-Complete
            "06302_AG",		// Not detecting the Data-QC failures (Many samples - should be excluded from most test runs)

            // Failed/Pending
            "05888_G",		// Good: 3 w/ failed Sequencing Branches, 5 "Under-Review"

            // Awaiting Processing - When a sample hasn't been assigned to a workflow
            "09546_T"      // Single awaitingProcessing Node
    ));


    @Test
    public void passedProjects() throws Exception {
        /*  ALL PASSED
            "10795",        // Good:    BASIC                           3 IGO-Complete
            "09443_AS",		// Good:    BASIC                           8 IGO-complete
            "09602_F",		// Good:    Multiple successful Banches     12 IGO-complete
            "09367_K",		// Good:    Failed branches                 1 IGO-Complete
         */
        List<Project> testCases = new ArrayList<>(Arrays.asList(
                new ProjectBuilder("10795")
                        .addStage("submitted", true, 3, 3, 0)
                        .addStage("sequencing", true, 3, 3, 0)
                        .addStage("dataQc", true, 3, 3, 0)
                        .build(),
                new ProjectBuilder("09443_AS")
                        .addStage("submitted", true, 8, 8, 0)
                        .addStage("libraryPrep", true, 8, 8, 0)
                        .addStage("sequencing", true, 8, 8, 0)
                        .addStage("dataQc", true, 8, 8, 0)
                        .build(),
                new ProjectBuilder("09602_F")
                        .addStage("libraryPrep", true, 12, 12, 0)
                        .addStage("sequencing", true, 12, 12, 0)
                        .addStage("dataQc", true, 12, 12, 0)
                        .build(),
                new ProjectBuilder("09367_K")
                        .addStage("libraryPrep", true, 1, 1, 0)
                        .addStage("sequencing", true, 1, 1, 0)
                        .addStage("dataQc", true, 1, 1, 0)
                        .build(),
                new ProjectBuilder("07428_AA")
                        .addStage("submitted", true, 4, 4, 0)
                        .addStage("extraction", true, 4, 4, 0)
                        .addStage("libraryPrep", true, 4, 4, 0)
                        .addStage("sampleQc", true, 4, 4, 0)
                        .addStage("sequencing", true, 4, 4, 0)
                        .addStage("dataQc", true, 4, 4, 0)
                        .build()
        ));

        testProjects(testCases);
    }

    @Test
    public void passedPendingProjects()  throws Exception {
        /*  Passed/Pending
            "10793"			// Good: 9 Passed, 1 Pending
         */
        List<Project> testCases = new ArrayList<>(Arrays.asList(
                new ProjectBuilder("10793")
                        .addStage("submitted", true, 10, 10, 0)
                        .addStage("libraryPrep", true, 10, 10, 0)
                        .addStage("sequencing", false, 10, 9, 0)
                        .addStage("dataQc", false, 10, 9, 0)
                        .build()));

        testProjects(testCases);
    }

    @Test
    public void failedComplete()  throws Exception {
        /*  Failed/Complete
            "06302_W",		// Good: 1 Failed Library Prep, 41 IGO-Complete
            "06302_AG",		// Not detecting the Data-QC failures
         */
        List<Project> testCases = new ArrayList<>(Arrays.asList(
                new ProjectBuilder("06302_W")
                        .addStage("libraryPrep", true, 42, 41, 1)
                        .addStage("sampleQc", true, 41, 41, 0)
                        .addStage("sequencing", true, 41, 41, 0)
                        .addStage("dataQc", true, 41, 41, 0)
                        .build(),
                new ProjectBuilder("06302_AG")
                        .addStage("libraryPrep", true, 382, 380, 2)
                        // TODO - Failed should be 48 & completed 332, but sequencing failures are difficult
                        .addStage("sequencing", false, 380, 332, 0)
                        .addStage("dataQc", false, 380, 332, 48)
                        .build()
        ));

        testProjects(testCases);
    }

    @Test
    public void failedPendingProjects()  throws Exception {
        /*  Failed/Pending
            "05888_G",		// Good: 3 w/ failed Sequencing Branches, 5 "Under-Review"
         */
        List<Project> testCases = new ArrayList<>(Arrays.asList(
                new ProjectBuilder("05888_G")
                        .addStage("submitted", true, 8, 8, 0)
                        .addStage("libraryPrep", true, 8, 8, 0)
                        .addStage("sequencing", false, 8, 0, 0)
                        .addStage("dataQc", false, 5, 0, 0)
                        .build()));

        testProjects(testCases);
    }

    @Test
    public void awaitingProcessingProjects()  throws Exception {
        /*  Single Awaiting Processing Node - All stages after "awaitingProcessing" should be incomplete
            "09546_T",			// doesn't load
         */
        List<Project> testCases = new ArrayList<>(Arrays.asList(
                new ProjectBuilder("09546_T")
                        .addStage("submitted", true, 26, 26, 0)
                        .addStage("awaitingProcessing", false, 10, 0, 0)
                        .addStage("libraryPrep", false, 16, 16, 0)
                        .addStage("sequencing", false, 16, 16, 0)
                        .addStage("dataQc", false, 16, 16, 0)
                        .build()));

        testProjects(testCases);
    }

    /**
     * Runner for testing input projects
     *
     * @param testCases
     */
    private void testProjects(List<Project> testCases) {
        for(Project project : testCases){
            // gate
            if(TEST_THESE_PROJECTS.contains(project.name)) {
                GetRequestTrackingTask t = new GetRequestTrackingTask(project.name, this.conn);
                Map<String, Object> requestTracker = new HashMap<>();
                try {
                    requestTracker = t.execute();
                } catch (IoError | RemoteException | NotFound e){
                    assertTrue("Exception in task execution", false);
                }

                Map<String, Object> request = (Map<String, Object>) requestTracker.get("request");
                testProjectStages(request, project);
            }
        }
    }

    /**
     * Runner for testing stages of a project
     *
     * @param requestTracker
     * @param project
     */
    private void testProjectStages(Map<String, Object> requestTracker, Project project) {
        List<Object> stages = (List<Object>) requestTracker.get("stages");

        // Verify correct number of stages
        Integer numStages = stages.size();
        Integer expectedNumStages = project.stages.size();
        assertEquals(String.format("Incorrect Number of Stages: %d, expected %d (Project: %s)", numStages, expectedNumStages, project.name),
            numStages, expectedNumStages);

        for(int i = 0; i<project.stages.size(); i++){
            Map<String, Object> stage = (Map<String, Object>) stages.get(i);
            Stage expectedStage = project.stages.get(i);

            // Verify order
            String stageName = (String) stage.get("stage");
            String expectedName = expectedStage.name;
            assertEquals(String.format("STAGE ORDER - %s, expected %s (Project: %s)", stageName, expectedName, project.name), stageName, expectedName);

            // Verify counts
            Integer total = (Integer) stage.get("totalSamples");
            Integer expectedTotal = expectedStage.totalCt;
            assertEquals(String.format("TOTAL - %d, expected %d (Project: %s, Stage: %s)", total, expectedTotal, project.name, stageName),
                    total, expectedTotal);

            Integer completedCt = (Integer) stage.get("completedSamples");
            Integer expectedCompletedCt = expectedStage.completedCt;
            assertEquals(String.format("COMPLETE COUNT - %d, expected %d (Project: %s, Stage: %s)", completedCt, expectedCompletedCt, project.name, stageName),
                    completedCt, expectedCompletedCt);

            Integer failedCt = (Integer) stage.get("failedSamples");
            Integer expectedFailedCt = (Integer) expectedStage.failedCt;
            assertEquals(String.format("FAILED COUNT - %d, expected %d (Project: %s, Stage: %s)", failedCt, expectedFailedCt, project.name, stageName),
                    failedCt, expectedFailedCt);

            Boolean complete = (Boolean) stage.get("complete");
            Boolean expectedComplete = expectedStage.complete;
            assertEquals(String.format("IS COMPLETE - %b, expected %b (Project: %s, Stage: %s)", complete, expectedComplete, project.name, stageName),
                    complete, expectedComplete);
        }
    }

    /**
     * Builder class for Project Representation. Should be used for testing class exclusively
     */
    private class ProjectBuilder {
        List<Stage> stages;         // All stages present in the project
        String name;                // Request ID of the project

        ProjectBuilder(String name){
            this.name = name;
            this.stages = new ArrayList<>();
        }

        /**
         * Add a test stage to the ProjectBuilder
         *
         * @param name          "stage"
         * @param complete      "complete"
         * @param total         "totalSamples"
         * @param completed     "completedSamples"
         * @param failed        "failedSamples"
         * @return
         */
        ProjectBuilder addStage(String name, Boolean complete, Integer total, Integer completed, Integer failed){
            Stage stage = new Stage(name, complete, total, completed, failed);
            this.stages.add(stage);
            return this;
        }

        Project build() {
            Project project = new Project(this.name, this.stages);
            return project;
        }
    }

    /**
     * Project data model. Should be used for testing class exclusively
     */
    private class Project {
        List<Stage> stages;
        String name;

        Project(String name, List<Stage> stages){
            this.name = name;
            this.stages = stages;
        }
    }

    /**
     * Stage data model. Should be used for testing class exclusively
     */
    private class Stage {
        String name;
        Boolean complete;
        Integer totalCt;
        Integer completedCt;
        Integer failedCt;

        Stage(String name, Boolean complete, Integer total, Integer completed, Integer failed){
            this.name = name;
            this.complete = complete;
            this.totalCt = total;
            this.completedCt = completed;
            this.failedCt = failed;
        }
    }
}