package org.mskcc.limsrest.service.cmoinfo.retriever;

import org.mskcc.domain.sample.CorrectedCmoSampleView;

import java.util.List;

public interface CmoSampleIdRetriever {
    String retrieve(CorrectedCmoSampleView correctedCmoSampleView, List<CorrectedCmoSampleView> patientSamples,
                    String requestId);
}
