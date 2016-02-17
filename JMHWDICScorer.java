
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
public class JMHWDICScorer extends DICScorer {
    static final Float D_DIMER_L1 = 0.4f * 1000; // Threshold for D-Dimer to be considered moderately elevated, in nanograms/milliliter.
    static final Float D_DIMER_L2 = 4.0f * 1000; // Threshold for D-Dimer to be considered highly elevated
    static final Float PT_INR_L1 = 1.25f; // Threshold for PT ratio to be considered elevated
    static final Float PT_INR_L2 = 1.67f; // Threshold for PT ratio to be considered highly elevated
    static final Float FIBRINOGEN_L1 = 1f * 100; // 1 g/L in mg/dL
    static final Float FIBRINOGEN_L2 = 1.5f * 100; // 1.5 g/L in mg/dL
    static final Float FDP_L1 = 10f; // Threshold for fibrinogen ratio to be considered normal
    static final Float FDP_L2 = 20f; // Threshold for fibrinogen ratio to be considered elevated
    static final Float FDP_L3 = 40f; // Threshold for fibrinogen ratio to be considered very elevated
    static final Float PLATELET_L1 = 50f; // Threshold for platelet count to be considered low
    static final Float PLATELET_L2 = 80f; // Threshold for platelet count to be considered very low
    static final Float PLATELET_L3 = 120f; // Threshold for platelet count to be considered low

    static final float ICD9_HEMATOLOGICAL_MALIGANCIES_START = 200f; // ***INCLUSIVE!
    static final float ICD9_HEMATOLOGICAL_MALIGANCIES_END = 210f; // ***EXCLUSIVE!
    
    static final int FLAG_HAS_HEMATOLOGICAL_MALIGNANCY = 0;
    static final int FLAG_HAS_CONDITION_PREDISPOSING_TO_DIC = 1;
    
    static boolean hasHematologicalMalignancy(Patient hospitalization) {
        for(Float icd9 : hospitalization.icd9s)
            if(icd9 >= ICD9_HEMATOLOGICAL_MALIGANCIES_START && icd9 < ICD9_HEMATOLOGICAL_MALIGANCIES_END)
                return true;
        return false;
    }
    
    @Override
    DICSS getDICSS() {
        return DICSS.JMHW;
    }
    
    @Override
    ITEMID getITEMID() {
        return ITEMID.DIC_SCORE_JMHW;
    }
    
    @Override
    int[] getScoresToReport() {
        return new int[]{ 4, 5, 6, 7, 8, 9 };
    }
    
    @Override
    boolean thisDICSSCanBeUsed(boolean[] flags, Patient hospitalization) {
        return true;
    }
    
    @Override
    int getDiagnosticScore(boolean[] flags, Patient hospitalization) {
        if(hospitalization == null || flags == null ||
            flags[FLAG_HAS_HEMATOLOGICAL_MALIGNANCY])
            return 4;
        else
            return 7;
    }
    
    @Override
    boolean[] calculatePatientFlags(Patient hospitalization) {
        boolean[] hospitalization_flags = new boolean[2];
        hospitalization_flags[FLAG_HAS_HEMATOLOGICAL_MALIGNANCY] = hasHematologicalMalignancy(hospitalization);
        hospitalization_flags[FLAG_HAS_CONDITION_PREDISPOSING_TO_DIC] = hasConditionAssociatedWithDIC(hospitalization);
        return hospitalization_flags;
    }    
    
    @Override
    DICScore calculateDICScore(boolean[] flags, Map<ITEMID, EVENT> events_map) {
        DICScore dic_score = new DICScore();                      
        // Calculate the score and report it in case of overt DIC
        // Notice that we do need to check whether the results exist or not -- this is done
        // by comparing the ITEMID to the constants they are supposed to hold.
        if(!flags[FLAG_HAS_HEMATOLOGICAL_MALIGNANCY])
            dic_score.addScore(ITEMID.HEDICIM_HEMATOLOGICAL_MALIGNANCY, 1);

        if(flags[FLAG_HAS_CONDITION_PREDISPOSING_TO_DIC])
            dic_score.addScore(ITEMID.HEDICIM_UNDERLYING_CONDITION_ASSOCIATED_WITH_DIC, 1);
        
//      Integer PT_contribution_to_ISTH_score = 0;
//      Integer PT_ratio_contribution_to_ISTH_score = 0;
/*          
        if(Objects.equals(pt.ITEMID, ITEMID.PT)) {
            if(pt.VALUENUM >= PT_L1 && pt.VALUENUM < PT_L2)
                PT_contribution_to_ISTH_score = 1;
            else if(pt.VALUENUM >= PT_L2)
                PT_contribution_to_ISTH_score += 2;
        }
*/            
        if(events_map.containsKey(ITEMID.PT_INR)) {
            EVENT pt_inr = events_map.get(ITEMID.PT_INR);
            // Notice: There are only 31 records of this test in the whole MIMIC-II v2.6 database.
            if(pt_inr.VALUENUM >= PT_INR_L1 && pt_inr.VALUENUM < PT_INR_L2)
                dic_score.addScore(ITEMID.PT_INR, 1);
            else if(pt_inr.VALUENUM >= PT_INR_L2)
                dic_score.addScore(ITEMID.PT_INR, 2);
        }
        if(events_map.containsKey(ITEMID.Overall_SOFA_Score)) {
            EVENT overall_sofa = events_map.get(ITEMID.Overall_SOFA_Score);
            if(overall_sofa.VALUENUM >= 3)
                dic_score.addScore(ITEMID.Overall_SOFA_Score, 1);
        }

        //score += (PT_contribution_to_ISTH_score > PT_ratio_contribution_to_ISTH_score) ?
//                PT_contribution_to_ISTH_score : PT_ratio_contribution_to_ISTH_score;

        if(events_map.containsKey(ITEMID.FIBRINOGEN)) {
            EVENT fibrinogen = events_map.get(ITEMID.FIBRINOGEN);
            if(fibrinogen.VALUENUM <= FIBRINOGEN_L1)
                dic_score.addScore(ITEMID.FIBRINOGEN, 2);
            else if(fibrinogen.VALUENUM > FIBRINOGEN_L1 && fibrinogen.VALUENUM <= FIBRINOGEN_L2)
                dic_score.addScore(ITEMID.FIBRINOGEN, 1);
        }

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

        if(D_dimer_contribution_to_ISTH_score > 0 || FDP_contribution_to_ISTH_score > 0)
            dic_score.addScore(ITEMID.HEDICIM_FIBRIN_RELATED_MARKER, max(D_dimer_contribution_to_ISTH_score, 
                FDP_contribution_to_ISTH_score));

        if(!flags[FLAG_HAS_HEMATOLOGICAL_MALIGNANCY]) 
            if(events_map.containsKey(ITEMID.PLATELET)) {
                EVENT platelet_count = events_map.get(ITEMID.PLATELET);
                if(platelet_count.VALUENUM <= PLATELET_L1)
                    dic_score.addScore(ITEMID.PLATELET, 3);
                else if(platelet_count.VALUENUM > PLATELET_L1 && platelet_count.VALUENUM <= PLATELET_L2)
                    dic_score.addScore(ITEMID.PLATELET, 2);
                else if(platelet_count.VALUENUM > PLATELET_L2 && platelet_count.VALUENUM <= PLATELET_L3)
                    dic_score.addScore(ITEMID.PLATELET, 1);
            }

        // TO DO: Find a way to identify "thrombosis related organ failure"
        return dic_score;
    }
}
