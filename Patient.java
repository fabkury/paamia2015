/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package hedicim;

import static hedicim.Const.stat_cells;
import static hedicim.HEDICIM.dic_scorers;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 *
 * @author kuryfs
 */
public class Patient {
    static int hedicim_id_count = 1;
    static Map<String, Patient> map = new HashMap<>();
    static Map<Integer, List<Patient>> subject_id_map = new HashMap<>();
    static Map<Integer, Patient> icustay_map = new HashMap<>();
    String stat_cells_content[];
    boolean eligible = false;
    long eligibility_begin = -1;
    EnumMap<DICSS, DICScore> has_1_day_dic;
    EnumMap<DICSS, DICScore> has_4_day_dic;
    EVENT evt_t0;
    EVENT sofa_1_day, sofa_4_day;
    EVENT saps_1_day, saps_4_day;
    EVENT fibrinogen_1_day, fibrinogen_4_day;
    EVENT procalcitonin_1_day, procalcitonin_4_day;
    
    EnumMap<DICSS, Map<Integer, List<DICPeriod>>> dic_periods;
    List<EVENT> events = new ArrayList<>();
    String origin_id = "";
    DATA_SOURCE data_source = DATA_SOURCE.UNKNOWN;
    
    int subject_id = 0;
    List<Float> icd9s = new ArrayList<>();
    List<Long[]> heparin_periods = new ArrayList<>();
    Set<ICUStay> icustays = new HashSet<>();
    long icustay_total_los = 0;
    long icustay_subject_total_num;
    long hospital_los = 0;
    boolean has_dic = false;
    boolean age_group = Const.AGE_GROUP_NEONATE;
    boolean received_heparin = false;
    boolean received_lmwh = false;
    boolean died_early = false;
    String hospital_expire = null;
    int sapsi_min = 0, sapsi_max = 0;
    int sofa_min = 0, sofa_max = 0;
    Date admit_dt, disch_dt;
    long dod;
    
    long ts_first_adm_of_hep_or_lmwh = 0;
    long ts_first_dic_event = 0;
    long ts_first_medication_is_relevant_event; // This is used to trim medication events to the proximity of DIC or near-DIC events
    long ts_last_medication_is_relevant_event; // This is used to trim medication events to the proximity of DIC or near-DIC events
    
    String gender = "X";
    int age_on_admit = 0;
    Patient(DATA_SOURCE _data_source, String _origin_id) {
        origin_id = _origin_id;
        map.put(origin_id, this);
        data_source = _data_source;
        dic_periods = new EnumMap<>(DICSS.class);
        has_1_day_dic = new EnumMap<>(DICSS.class);
        has_4_day_dic = new EnumMap<>(DICSS.class);
        stat_cells_content = new String[stat_cells.length];
    }
    
    static void mapSubjectID(Patient patient) {
        if(!subject_id_map.containsKey(patient.subject_id))
            subject_id_map.put(patient.subject_id, new ArrayList<>());
        ((List<Patient>)subject_id_map.get(patient.subject_id)).add(patient);
    }
    
    void addICUStay(ICUStay icustay) {
        icustays.add(icustay);
        icustay_map.put(icustay.id, this);
    }

    EVENT findEventByTimeDistance(long ts, ITEMID itemid) {
        return findEventByTimeWindowCenter(ts>Const.STANDARD_DISTANCE_WINDOW?(ts-Const.STANDARD_DISTANCE_WINDOW):0,
            ts+Const.STANDARD_DISTANCE_WINDOW, itemid);
    }
    
    EVENT findEventByTimeWindowCenter(long ts_start_inclusive, long ts_end_exclusive, ITEMID itemid) {
        EVENT retval = null;
        long diff = Integer.MAX_VALUE;
        long ts = (ts_end_exclusive-ts_start_inclusive)/2 + ts_start_inclusive;
        
        for(int j=events.size()-1; j>=0; j--) {
            EVENT event = events.get(j);
            long evt_ts = event.CHARTTIME.getTime();
            if(evt_ts > ts_end_exclusive)
                continue;
            if(evt_ts > ts) {
                if(event.ITEMID == itemid.value
                && evt_ts - ts < diff) {
                    retval = event;
                    diff = evt_ts - ts;
                }
            }
            else {
                if(ts_start_inclusive > evt_ts)
                    break;
                if(event.ITEMID == itemid.value
                && ts - evt_ts < diff) {
                    retval = event;
                    diff = ts - evt_ts;
                }
            }
        }
                
        return retval;
    }
    
    EVENT findEventByTimeWindow(long ts, long ts_start_inclusive, long ts_end_exclusive, ITEMID itemid) {
        EVENT retval = null;
        long diff = Integer.MAX_VALUE;
        
        for(int j=events.size()-1; j>=0; j--) {
            EVENT event = events.get(j);
            long evt_ts = event.CHARTTIME.getTime();
            if(evt_ts > ts_end_exclusive)
                continue;
            if(evt_ts > ts) {
                if(event.ITEMID == itemid.value
                && evt_ts - ts < diff) {
                    retval = event;
                    diff = evt_ts - ts;
                }
            }
            else {
                if(ts_start_inclusive > evt_ts)
                    break;
                if(event.ITEMID == itemid.value
                && ts - evt_ts < diff) {
                    retval = event;
                    diff = ts - evt_ts;
                }
            }
        }
                
        return retval;
    }
    
    void removeLabEventsNearDeath(long nearness_window) {
        if(dod == 0)
            return;
        
        for(Iterator it = events.iterator(); it.hasNext();) {
            EVENT labe = (EVENT)it.next();
            if(labe.CHARTTIME.getTime() > dod - nearness_window)
                // Ignore all DIC events too close to death.
                it.remove();
        }
    }
    
    void alignTimestampsToHospitalAdmission() {
        for(EVENT event : events)
            event.CHARTTIME = new Date(event.CHARTTIME.getTime()-admit_dt.getTime());
        //if(dod != 0)
        //    dod = dod-admit_dt.getTime();
    }
    
    void sortEvents() {
        Collections.sort(events, new Comparator<EVENT>(){
            @Override
            public int compare(EVENT l1, EVENT l2) {
                if(l1.CHARTTIME.getTime() > l2.CHARTTIME.getTime())
                        return 1;
                if(l1.CHARTTIME.getTime() < l2.CHARTTIME.getTime())
                    return -1;
                return 0;
            }
        });
    }
    
    Map<ITEMID, EVENT> mapEventsBeforePointInList(int i) {
        // Find the most recent lab of each type inside the time window
        long timestamp = events.get(i).CHARTTIME.getTime();
        Map<ITEMID, EVENT> events_map = new HashMap<>();

        // Inside the time window get the most recent lab event of each type.
        for(int j=i; j >= 0 && timestamp - events.get(j).CHARTTIME.getTime() <= Const.LAB_TIME_WINDOW; j--) {
            // In here we're restricted to the LAB_TIME_WINDOW.
            // Get the most recent EVENT of each type.                
            EVENT labe = events.get(j);
            if(!events_map.containsKey(labe.ITEMID))
                    events_map.put(Const.value_map.get(labe.ITEMID), labe);
        }

        return events_map;
    }
    
    int fastForwardToSameTimestamp(int i) {
        for(int f=i+1; f < events.size(); f++) {
            if(events.get(f).CHARTTIME.getTime() < events.get(i).CHARTTIME.getTime()) {
                // This never happened, because the EVENTS are sorted by CHARTTIME.
                // But, just in case something happens in the future, I've left this 'if' here.
                System.out.println("Big exception in generateSIRSEvents() at origin_id=" + 
                    origin_id + ", f=" + f + ", i=" + i + ".");
            }

            if(events.get(f).CHARTTIME.getTime() != events.get(i).CHARTTIME.getTime())
                break;
            else
                i=f;
        }
        return i;        
    }
    
    void generateSIRSEvents() {
        // NOTICE: The constants remove the magic numbers from this function, but not the magic
        // operators. The calculator defined by the ISTH requires correct use of >, >=, < and <=.
        List<EVENT> events_to_add = new ArrayList<>();
        
        // Iterate through the lab tests, get the worst-case lab test of each type in case of duplicates
        for(int i=0; i < events.size(); i++) {
            // Fast-foward to all events charted at the same time. This often is the case.
            i = fastForwardToSameTimestamp(i);
            SIRSScore sirs_score = SIRSScore.calculateSIRSScore(mapEventsBeforePointInList(i));
            sirs_score.date = events.get(i).CHARTTIME;
            
            if(sirs_score.score > 0d) {
                events_to_add.add(new EVENT(ITEMID.HEDICIM_SIRS.value, sirs_score.date,
                    Double.toString(sirs_score.score), (float)sirs_score.score.intValue(), null, null));
                
                if(!eligible)
                    if(sirs_score.score >= 2d) {
                        EVENT evt = findEventByTimeDistance(sirs_score.date.getTime(), ITEMID.Overall_SOFA_Score);
                        if(evt != null)
                            if(evt.VALUENUM >= 3) {
                                eligible = true;
                                eligibility_begin = sirs_score.date.getTime();
                            }
                    }
            }
        }
        
        events.addAll(events_to_add);
        sortEvents();
    }
    
    void generatePlateletReductionEvents() {
        boolean needs_to_add_and_resort = false;
        List<EVENT> events_to_add = new ArrayList<>();
        for(int i=0; i < events.size(); i++) {
            if(events.get(i).ITEMID != ITEMID.PLATELET.value)
                continue;
            // Platelet count found.
            EVENT eventi = events.get(i);
            // Now look back up to 24 hours.
            boolean n30p_reduction_found = false;
            for(int h=i-1; h >= 0 && events.get(i).CHARTTIME.getTime() - 
                events.get(h).CHARTTIME.getTime() > Const.N24H_IN_MILLISECONDS; h--) {
                if(!Objects.equals(events.get(h).ITEMID, ITEMID.PLATELET.value))
                    continue;
                // Platelet count previous to i found.
                EVENT eventh = events.get(h);
                // Check if there was a reduction from h to i.
                if(eventi.VALUENUM < eventh.VALUENUM*(1f-0.5f)) {    // 50% reduction
                    // Found 50% reduction.
                    // Create an event with the previous higher value, special ITEMID, but the time of when the reduction was noticed.
                    EVENT n50p_reduction = new EVENT(eventh);
                    n50p_reduction.ITEMID = ITEMID.HEDICIM_PLATELET_COUNT_50P_REDUCTION.value;
                    n50p_reduction.CHARTTIME = new Date(eventi.CHARTTIME.getTime());
                    events_to_add.add(n50p_reduction);
                    needs_to_add_and_resort = true;
                    break; // Stop looking for more reductions in comparison to eventi
                }                
                if(!n30p_reduction_found && eventi.VALUENUM < eventh.VALUENUM*(1f-0.3f)) {    // 30% reduction
                    // Found 30% reduction.
                    // Create an event with the previous higher value, special ITEMID, but the time of when the reduction was noticed.
                    EVENT n30p_reduction = new EVENT(eventh);
                    n30p_reduction.ITEMID = ITEMID.HEDICIM_PLATELET_COUNT_30P_REDUCTION.value;
                    n30p_reduction.CHARTTIME = new Date(eventi.CHARTTIME.getTime());
                    n30p_reduction_found = true; // Stop looking for 30% reductions.
                    events_to_add.add(n30p_reduction);
                    needs_to_add_and_resort = true;
                }
            }
        }
        
        if(needs_to_add_and_resort) {
            events.addAll(events_to_add);
            sortEvents();
        }
    }
    
    void setStatCell(String stat_cell, String value) {
        stat_cells_content[Arrays.asList(stat_cells).indexOf(stat_cell)] = value;
    }
    
    static final float icd9s_for_sepsis[] = {
        785.52f, // SEPTIC SHOCK -- 1064 HADM_IDs in MIMIC-II
    };
    
    // In the intervals below, beginnings are inclusive, endings are exclusive.
    static final float icd9_intervals_for_sepsis[][] = {
        {995.9f, 996f}, // Sepsis: 2013 ICD-9-CM Diagnosis Code 995.91
        {038f, 039f}, // SEPTICEMIA -- 3309 HADM_IDs in MIMIC-II.
    };
    
    void makeStatCell(Row row, String stat_cell) {
        int index = Arrays.asList(stat_cells).indexOf(stat_cell);
        Cell cell = row.createCell(index);
        /*if(stat_cells_content[index] != null) {
            cell.setCellValue(stat_cells_content[index]);
            return;
        }*/
        sortEvents();
        EVENT evt;
        switch(stat_cell) {
            case "eligible":
                cell.setCellValue(eligible);
                break;
            case "id":
                cell.setCellValue(origin_id);
                break;
            case "age":
                cell.setCellValue(age_on_admit);
                break;
            case "gender":
                cell.setCellValue(gender);
                break;
            case "icd9":
                if(icd9s != null && icd9s.size() > 0)
                    cell.setCellValue(icd9s.get(0));
                break;
            case "SOFA peak":
                cell.setCellValue(sofa_max);
                break;
            case "SAPSI max":
                cell.setCellValue(sapsi_max);
                break;
            case "MODS day 1":
                evt = findEventByTimeDistance(eligibility_begin, ITEMID.Overall_SOFA_Score);
                if(evt != null) {
                    if(evt.VALUENUM >= 12)
                        cell.setCellValue(true);
                    else
                        cell.setCellValue(false);
                }
                break;
            case "MODS day 4":
                evt = findEventByTimeDistance(eligibility_begin+Const.STANDARD_DISTANCE_WINDOW*3, 
                    ITEMID.Overall_SOFA_Score);
                if(evt != null) {
                    if(evt.VALUENUM >= 12)
                        cell.setCellValue(true);
                    else
                        cell.setCellValue(false);
                }
                break;
            case "SOFA score day 1":
                evt = findEventByTimeDistance(eligibility_begin, ITEMID.Overall_SOFA_Score);
                if(evt != null)
                    cell.setCellValue(evt.VALUENUM);
                break;
            case "SOFA score day 4":
                evt = findEventByTimeDistance(eligibility_begin+Const.STANDARD_DISTANCE_WINDOW*3, 
                    ITEMID.Overall_SOFA_Score);
                if(evt != null)
                    cell.setCellValue(evt.VALUENUM);
                break;
            case "jaam score day 1":
                evt = findEventByTimeDistance(eligibility_begin, ITEMID.DIC_SCORE_JAAM);
                if(evt != null)
                    cell.setCellValue(evt.VALUENUM);
                break;
            case "jaam score day 4":
                evt = findEventByTimeDistance(eligibility_begin+Const.STANDARD_DISTANCE_WINDOW*3, 
                    ITEMID.DIC_SCORE_JAAM);
                if(evt != null)
                    cell.setCellValue(evt.VALUENUM);
                break;
            case "isth score day 1":
                evt = findEventByTimeDistance(eligibility_begin, ITEMID.DIC_SCORE_ISTH);
                if(evt != null)
                    cell.setCellValue(evt.VALUENUM);
                break;
            case "isth score day 4":
                evt = findEventByTimeDistance(eligibility_begin+Const.STANDARD_DISTANCE_WINDOW*3,
                    ITEMID.DIC_SCORE_ISTH);
                if(evt != null)
                    cell.setCellValue(evt.VALUENUM);
                break;
            case "jmhw score day 1":
                evt = findEventByTimeDistance(eligibility_begin, ITEMID.DIC_SCORE_JMHW);
                if(evt != null)
                    cell.setCellValue(evt.VALUENUM);
                break;
            case "jmhw score day 4":
                evt = findEventByTimeDistance(eligibility_begin+Const.STANDARD_DISTANCE_WINDOW*3,
                    ITEMID.DIC_SCORE_JMHW);
                if(evt != null)
                    cell.setCellValue(evt.VALUENUM);
                break;
            case "ksth score day 1":
                evt = findEventByTimeDistance(eligibility_begin, ITEMID.DIC_SCORE_KSTH);
                if(evt != null)
                    cell.setCellValue(evt.VALUENUM);
                break;
            case "ksth score day 4":
                evt = findEventByTimeDistance(eligibility_begin+Const.STANDARD_DISTANCE_WINDOW*3,
                    ITEMID.DIC_SCORE_KSTH);
                if(evt != null)
                    cell.setCellValue(evt.VALUENUM);
                break;
            case "deceased within 28 days":
                if(dod != 0 && eligibility_begin != -1 && dod > eligibility_begin
                && dod <= Const.EARLY_DEATH_TIME_WINDOW + eligibility_begin)
                    cell.setCellValue(true);
                else
                    cell.setCellValue(false);
                break;
            case "deceased within 1 year":
                if(dod != 0 && eligibility_begin != -1 && dod > eligibility_begin
                && dod <= (1000L * 60L * 60L * 24L * 365L) + eligibility_begin)
                    cell.setCellValue(true);
                else
                    cell.setCellValue(false);
                break;
            case "received heparin":
                cell.setCellValue(received_heparin | received_lmwh);
                break;  
            case "total hospital los":
                cell.setCellValue(hospital_los);
                break;
            case "total icu los":
                cell.setCellValue(icustay_total_los);                
            case "fibrinogen day 1":
                evt = findEventByTimeDistance(eligibility_begin, ITEMID.FIBRINOGEN);
                if(evt != null)
                    cell.setCellValue(evt.VALUE);
                // TO DO
                break;
            case "fibrinogen day 4":
                evt = findEventByTimeDistance(eligibility_begin+Const.STANDARD_DISTANCE_WINDOW*3, ITEMID.FIBRINOGEN);
                if(evt != null)
                    cell.setCellValue(evt.VALUENUM);
                break;
            case "saps1 day 1": 
                evt = findEventByTimeDistance(eligibility_begin, ITEMID.SAPSI);
                if(evt != null)
                    cell.setCellValue(evt.VALUENUM);
                break;
            case "saps1 day 4": 
                evt = findEventByTimeDistance(eligibility_begin+Const.STANDARD_DISTANCE_WINDOW*3, ITEMID.SAPSI);
                if(evt != null)
                    cell.setCellValue(evt.VALUENUM);
                break;
            case "sirs day 1":
                evt = findEventByTimeDistance(eligibility_begin, ITEMID.HEDICIM_SIRS);
                if(evt != null)
                    cell.setCellValue(evt.VALUENUM);
                break;
            case "sirs day 4":
                evt = findEventByTimeDistance(eligibility_begin+Const.STANDARD_DISTANCE_WINDOW*3, ITEMID.HEDICIM_SIRS);
                if(evt != null)
                    cell.setCellValue(evt.VALUENUM);
                break;
            case "T0":
                if(eligibility_begin != -1)
                    cell.setCellValue(eligibility_begin/1000d*60d*60d);
                break;
            case "DIC day 1": {
                boolean found_dic = false;
                for(DICScorer dics : dic_scorers.values()) {
                    evt = findEventByTimeDistance(eligibility_begin, dics.getITEMID());
                    if(evt != null)
                        if(evt.VALUENUM >= dics.getDiagnosticScore(this)) {
                            found_dic = true;
                            break;
                        }
                }
                cell.setCellValue(found_dic);
                break;
            }
            case "DIC day 4": {
                boolean found_dic = false;
                for(DICScorer dics : dic_scorers.values()) {
                    evt = findEventByTimeDistance(eligibility_begin+Const.STANDARD_DISTANCE_WINDOW*3, dics.getITEMID());
                    if(evt != null)
                        if(evt.VALUENUM >= dics.getDiagnosticScore(this)) {
                            found_dic = true;
                            break;
                        }
                }
                cell.setCellValue(found_dic);
                break;
            }
            case "has any DIC":
                cell.setCellValue(has_dic);
                break;
//            case "lactate day 1":
//                evt = findEventByTimeDistance(eligibility_begin, ITEMID.LACTATE);
//                if(evt != null)
//                    cell.setCellValue(evt.VALUE);
//                break;
//            case "lactate day 4":
//                evt = findEventByTimeDistance(eligibility_begin+Const.STANDARD_DISTANCE_WINDOW*3, ITEMID.LACTATE);
//                if(evt != null)
//                    cell.setCellValue(evt.VALUE);
//                break;
            case "pt day 1":
                evt = findEventByTimeDistance(eligibility_begin, ITEMID.PT);
                if(evt != null)
                    cell.setCellValue(evt.VALUE);
            case "pt day 4":
                evt = findEventByTimeDistance(eligibility_begin+Const.STANDARD_DISTANCE_WINDOW*3, ITEMID.PT);
                if(evt != null)
                    cell.setCellValue(evt.VALUE);
                break;
            case "aptt day 1":
                evt = findEventByTimeDistance(eligibility_begin, ITEMID.APTT);
                if(evt != null)
                    cell.setCellValue(evt.VALUE);
                break;
            case "aptt day 4":
                evt = findEventByTimeDistance(eligibility_begin+Const.STANDARD_DISTANCE_WINDOW*3, ITEMID.APTT);
                if(evt != null)
                    cell.setCellValue(evt.VALUE);
                break;
            case "fdp day 1":
                evt = findEventByTimeDistance(eligibility_begin, ITEMID.FDP);
                if(evt != null)
                    cell.setCellValue(evt.VALUE);
                break;
            case "fdp day 4":
                evt = findEventByTimeDistance(eligibility_begin+Const.STANDARD_DISTANCE_WINDOW*3, ITEMID.FDP);
                if(evt != null)
                    cell.setCellValue(evt.VALUE);
                break;
            case "platelets day 1":
                evt = findEventByTimeDistance(eligibility_begin, ITEMID.PLATELET);
                if(evt != null)
                    cell.setCellValue(evt.VALUE);
                break;
            case "platelets day 4":
                evt = findEventByTimeDistance(eligibility_begin+Const.STANDARD_DISTANCE_WINDOW*3, ITEMID.PLATELET);
                if(evt != null)
                    cell.setCellValue(evt.VALUE);
                break;
            case "pt-inr day 1":
                evt = findEventByTimeDistance(eligibility_begin, ITEMID.PT_INR);
                if(evt != null)
                    cell.setCellValue(evt.VALUE);
                break;
            case "pt-inr day 4":
                evt = findEventByTimeDistance(eligibility_begin+Const.STANDARD_DISTANCE_WINDOW*3, ITEMID.PT_INR);
                if(evt != null)
                    cell.setCellValue(evt.VALUE);
                break;
            case "d-dimer day 1":
                evt = findEventByTimeDistance(eligibility_begin, ITEMID.D_DIMER);
                if(evt != null)
                    cell.setCellValue(evt.VALUE);
                break;
            case "d-dimer day 4":
                evt = findEventByTimeDistance(eligibility_begin+Const.STANDARD_DISTANCE_WINDOW*3, ITEMID.D_DIMER);
                if(evt != null)
                    cell.setCellValue(evt.VALUE);
                break;
            case "has malignancy":
                break;
            case "has ICD9 for DIC":
                cell.setCellValue(ISTHDICScorer.hasConditionAssociatedWithDIC(this));
                break;
            case "has ICD9 for sepsis":
                cell.setCellValue(false);
                for(float icd9 : icd9s) {
                    for(float icd9_for_sepsis : icd9s_for_sepsis)
                        if(icd9 == icd9_for_sepsis) {
                            cell.setCellValue(true);
                            break;
                        }
                    for(float[] interval : icd9_intervals_for_sepsis)
                        if(icd9 >= interval[0] && icd9 < interval[1]) {
                            cell.setCellValue(true);
                            break;
                        }
                }
                break;
            case "hospital expire":
                if("Y".equals(hospital_expire))
                    cell.setCellValue(true);
                else
                if("N".equals(hospital_expire))
                    cell.setCellValue(false);
                break;
            case "Needed platelet reduction event":
                break;
            case "dod":
                if(dod > 0)
                    cell.setCellValue(dod);
                break;
        }
    }
    
    static void makeStatFile(String filename) throws FileNotFoundException, IOException {
        try(FileOutputStream fileOut = new FileOutputStream(filename)) {
            Workbook wb = new XSSFWorkbook();
            Sheet sheet = wb.createSheet("HEDICIM AMIA 2015");
            int r=0;
            Row header_row = sheet.createRow(r++);
            for(String stat_cell : stat_cells)
                header_row.createCell(Arrays.asList(stat_cells).indexOf(stat_cell)).setCellValue(stat_cell);
            for(Patient patient : map.values()) {
                if(!patient.eligible)
                    continue;
                Row row = sheet.createRow(r++);
                for(String stat_cell : stat_cells)
                    patient.makeStatCell(row, stat_cell);
            }
            wb.write(fileOut);
            fileOut.close();
            System.out.println("Statistics file " + filename + " succesfully written.");
        } catch(Exception ex) {
            System.err.println("Exception at makeStatFile(\"" + filename + "\"): " + ex.getLocalizedMessage());
        }
    }
}
