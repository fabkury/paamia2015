
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
public class ISTHDICScorer extends DICScorer {    
    static final Float D_DIMER_L1 = 0.4f * 1000; // Threshold for D-Dimer to be considered moderately elevated, in nanograms/milliliter.
    static final Float D_DIMER_L2 = 4.0f * 1000; // Threshold for D-Dimer to be considered highly elevated
    static final Float PT_UPPER_BASELINE = 13.0f; // Threshold for PT to be considered elevated
    static final Float PT_L1 = PT_UPPER_BASELINE+3f; // Threshold for PT to be considered elevated
    static final Float PT_L2 = PT_UPPER_BASELINE+6f; // Threshold for PT to be considered highly elevated
    static final Float PT_INR_L1 = (PT_UPPER_BASELINE + PT_L1) / PT_UPPER_BASELINE; // Threshold for PT ratio to be considered elevated
    static final Float PT_INR_L2 = (PT_UPPER_BASELINE + PT_L2) / PT_UPPER_BASELINE; // Threshold for PT ratio to be considered highly elevated
    static final Float FIBRINOGEN_L1 = 1f * 100; // Threshold for fibrinogen to be considered normal, 1 g/L, in mg/dL
    static final Float PLATELET_L1 = 50f; // Threshold for platelet count to be considered very low
    static final Float PLATELET_L2 = 100f; // Threshold for platelet count to be considered low
    static final Float FDP_L1 = 10f; // Threshold for fibrin degradation products to be considered normal
    static final Float FDP_L2 = 20f; // Threshold for fibrin degradation products ratio to be considered elevated
    static final Float FDP_L3 = 40f; // Threshold for fibrin degradation products ratio to be considered very elevated
    static final int FLAG_HAS_CONDITION_PREDISPOSING_TO_DIC = 0;
    
    @Override
    DICSS getDICSS() {
        return DICSS.ISTH;
    }
    
    @Override
    ITEMID getITEMID() {
        return ITEMID.DIC_SCORE_ISTH;
    }
    
    @Override
    int[] getScoresToReport() {
        return new int[]{6, 5, 4, 3};
    }
    
    @Override
    int getDiagnosticScore(boolean[] flags, Patient patient) {
        return 5;
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
        
        EVENT platelet_count = events_map.get(ITEMID.PLATELET);
        EVENT pt = events_map.get(ITEMID.PT);
        EVENT pt_inr = events_map.get(ITEMID.PT_INR);
        
        // Calculate the ISTH score and report it in case of overt DIC
        // Notice that we do need to check whether the results exist or not -- this is done
        // by comparing the ITEMID to the constants they are supposed to hold.

        // Because the PT and PT Ratio interpretations are interlinked, and our intent is to
        // calculate the score for the worst case scenario (that is, the combination of results
        // that lead to the worst diagnosis), we need to separate their contributions to the
        // ISTH score and then use the worst one.
        Integer PT_contribution_score = 0;
        Integer PT_INR_contribution_score = 0;
        //Integer PT_ratio_contribution_to_ISTH_score = 0;
        if(pt != null) {
            if(pt.VALUENUM >= PT_L1 && pt.VALUENUM < PT_L2)
                PT_contribution_score = 1;
            if(pt.VALUENUM >= PT_L2)
                PT_contribution_score = 2;
        }
        else
            if(pt_inr != null) {
                if(pt_inr.VALUENUM >= PT_INR_L1 && pt_inr.VALUENUM < PT_INR_L2)
                    PT_INR_contribution_score = 1;
                if(pt_inr.VALUENUM >= PT_INR_L2)
                    PT_INR_contribution_score += 2;
            }   
        if(PT_INR_contribution_score > PT_contribution_score)
            dic_score.addScore(ITEMID.PT_INR, PT_INR_contribution_score);
        else if(PT_contribution_score > 0)
            dic_score.addScore(ITEMID.PT, PT_contribution_score);
       
        if(events_map.containsKey(ITEMID.FIBRINOGEN))
            if(events_map.get(ITEMID.FIBRINOGEN).VALUENUM < FIBRINOGEN_L1)
                dic_score.addScore(ITEMID.FIBRINOGEN, 1);

        // Because the D-Dimer and Fibrin Degradation Products interpretations are interlinked,
        // and our intent is to calculate the score for the worst case scenario (that is, the
        // combination of results that leads to the worst diagnosis), we need to separate their
        // contributions to the ISTH score and then use the worst one.
        int D_dimer_contribution_to_ISTH_score = 0, FDP_contribution_to_ISTH_score = 0;
        if(events_map.containsKey(ITEMID.D_DIMER)) {
            EVENT d_dimer = events_map.get(ITEMID.D_DIMER);
            if(d_dimer.VALUENUM >= D_DIMER_L1 && d_dimer.VALUENUM <= D_DIMER_L2)
                D_dimer_contribution_to_ISTH_score = 2;
            if(d_dimer.VALUENUM > D_DIMER_L2)
                D_dimer_contribution_to_ISTH_score = 3;
        }

        if(events_map.containsKey(ITEMID.FDP)) {
            EVENT fdp = events_map.get(ITEMID.FDP);
            if(!fdp.VALUE.isEmpty()) {
                if(fdp.VALUENUM >= FDP_L3)
                    dic_score.addScore(ITEMID.FDP, 3);
                else if(fdp.VALUENUM >= FDP_L2)
                    dic_score.addScore(ITEMID.FDP, 2);
                else if(fdp.VALUENUM >= FDP_L1)
                    dic_score.addScore(ITEMID.FDP, 1);
            }
        }

        dic_score.addScore(ITEMID.HEDICIM_FIBRIN_RELATED_MARKER, max(D_dimer_contribution_to_ISTH_score, 
            FDP_contribution_to_ISTH_score));

        if(platelet_count != null) {
            if(platelet_count.VALUENUM <= PLATELET_L1)
                dic_score.addScore(ITEMID.PLATELET, 2);
            if(platelet_count.VALUENUM > PLATELET_L1 && platelet_count.VALUENUM <= PLATELET_L2)
                dic_score.addScore(ITEMID.PLATELET, 1);
        }
        
        return dic_score;
    }
}

