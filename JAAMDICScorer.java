
package hedicim;

import static java.lang.Integer.max;
import java.util.*;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author kuryfs
 */
public class JAAMDICScorer extends DICScorer {
    static final Float PT_INR_L1 = 1.2f; // Threshold for PT ratio to be considered elevated
    static final Float FDP_L1 = 10f; // Threshold for fibrin degradation products to be considered normal
    static final Float FDP_L2 = 25f; // Threshold for fibrin degradation products ratio to be considered elevated
    static final Float PLATELET_L1 = 80f; // Threshold for platelet count to be considered low
    static final Float PLATELET_L2 = 120f; // Threshold for platelet count to be considered very low
    
    static final int FLAG_HAS_CONDITION_PREDISPOSING_TO_DIC = 0;
    
    @Override
    DICSS getDICSS() {
        return DICSS.JAAM;
    }
    
    @Override
    ITEMID getITEMID() {
        return ITEMID.DIC_SCORE_JAAM;
    }
    
    @Override
    public int[] getScoresToReport() {
        return new int[]{ 3, 4, 5 };
    }
    
    @Override
    int getDiagnosticScore(boolean[] flags, Patient patient) {
        return 4;        
    }
    
    @Override
    boolean[] calculatePatientFlags(Patient patient) {
        boolean[] patient_flags = new boolean [1];
        patient_flags[FLAG_HAS_CONDITION_PREDISPOSING_TO_DIC] = hasConditionAssociatedWithDIC(patient);
        return patient_flags;
    }
    
    @Override
    boolean thisDICSSCanBeUsed(boolean[] flags, Patient patient) {
        return flags[FLAG_HAS_CONDITION_PREDISPOSING_TO_DIC];
    }
    
    @Override
    DICScore calculateDICScore(boolean[] flags, Map<ITEMID, EVENT> events_map) {
        DICScore dic_score = new DICScore();
        
        SIRSScore sirs_score = SIRSScore.calculateSIRSScore(events_map);

        if(sirs_score.score >= 3) {
            dic_score.addScore(ITEMID.HEDICIM_SIRS, 1);
            dic_score.sirs_score = sirs_score;
        }

        if(events_map.containsKey(ITEMID.PT_INR))
            if(events_map.get(ITEMID.PT_INR).VALUENUM >= PT_INR_L1)
                dic_score.addScore(ITEMID.PT_INR, 1);

        if(events_map.containsKey(ITEMID.FDP)) {
            EVENT fdp = events_map.get(ITEMID.FDP);
            if(!fdp.VALUE.isEmpty()) {
                if(fdp.VALUENUM >= FDP_L2)
                    dic_score.addScore(ITEMID.FDP, 3);
                else if(fdp.VALUENUM >= FDP_L1)
                    dic_score.addScore(ITEMID.FDP, 1);
            }
        }
                
        int platelet_count_contribution_to_score = 0,
            platelet_count_reduction_contribution_to_score = 0;
        EVENT platelet_count = events_map.get(ITEMID.PLATELET);
        if(platelet_count != null && platelet_count.VALUENUM > 0f) {
            if(platelet_count.VALUENUM < PLATELET_L1)
                platelet_count_contribution_to_score = max(platelet_count_contribution_to_score, 3);
            else if(platelet_count.VALUENUM >= PLATELET_L1 && platelet_count.VALUENUM < PLATELET_L2)
                platelet_count_contribution_to_score = max(platelet_count_contribution_to_score, 1);
        }

        if(events_map.containsKey(ITEMID.HEDICIM_PLATELET_COUNT_50P_REDUCTION))
            platelet_count_reduction_contribution_to_score = max(platelet_count_reduction_contribution_to_score, 3);
        else if(events_map.containsKey(ITEMID.HEDICIM_PLATELET_COUNT_30P_REDUCTION))
            platelet_count_reduction_contribution_to_score = max(platelet_count_reduction_contribution_to_score, 1);

        if(platelet_count_reduction_contribution_to_score > 0 || platelet_count_contribution_to_score > 0)
            if(platelet_count_reduction_contribution_to_score >= platelet_count_contribution_to_score) {
                if(platelet_count_reduction_contribution_to_score > platelet_count_contribution_to_score)
                    dic_score.addScore(ITEMID.HEDICIM_PLATELET_REDUCTION, platelet_count_reduction_contribution_to_score);
                else
                    dic_score.addScore(ITEMID.PLATELET, platelet_count_contribution_to_score);
            }
            else
                dic_score.addScore(ITEMID.PLATELET, platelet_count_contribution_to_score);
        
        return dic_score;
    }
}
