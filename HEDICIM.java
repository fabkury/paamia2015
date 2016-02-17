/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package hedicim;

import java.io.File;
import java.io.FileOutputStream;
import static java.lang.Long.max;
import static java.lang.Long.min;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;


/**
 *
 * @author kuryfs
 */
public class HEDICIM {
    static MySQLConnMIMICII msc;
    static MySQLConnBTRIS mscb;
    static SimpleDateFormat output_file_date_format = new SimpleDateFormat("yy-MM-dd a hh-mm");
    static String execution_label = "HEDICIM";
    static int count_of_all_patients_processed = 0;
    static int count_of_non_neonates_processed = 0;
    static int count_of_patients_removed_because_of_no_DIC = 0;
    static int eligible_count = 0;
    
    static EnumMap<DICSS, DICScorer> dic_scorers = new EnumMap<>(DICSS.class);
    
    static EnumMap<DICSS, EnumMap<ITEMID, Long[]>> scorers_structure_table = new EnumMap<>(DICSS.class);
        
    static void removeNonAdults(Set<Patient> patients) {
        int count = 0;
        for(Iterator it = patients.iterator(); it.hasNext();) {
            Patient patient = (Patient)it.next();
            if(patient.age_group != Const.AGE_GROUP_ADULT) {
                Patient.map.remove(patient.origin_id);
                it.remove();
                count++;
            }
        }
        
        System.out.println(count + " non-adult" + ((count==1)?"":"s") + " removed from patient list.");
    }
    
    static long countEligiblePatients(Set<Patient> patients) {
        long count = 0;
        for(Iterator it = patients.iterator(); it.hasNext();) {
            Patient patient = (Patient)it.next();
            if(patient.eligible)
                count++;
        }
        return count;
    }
    
    static long countPatientsWithDIC(Set<Patient> patients) {
        long count = 0;
        for(Iterator it = patients.iterator(); it.hasNext();) {
            Patient patient = (Patient)it.next();
            if(patient.has_dic)
                count++;
        }
        return count;
    }
    
    static int removePatientsWithoutDIC(Set<Patient> patients) {
        Set<Patient> to_remove = new HashSet<>();
        for(Iterator it = patients.iterator(); it.hasNext();) {
            Patient patient = (Patient)it.next();
            if(!patient.has_dic)
                to_remove.add(patient);
        }
        patients.removeAll(to_remove);
        
        System.out.println(to_remove.size() + " patient" + ((to_remove.size()==1)?"":"s") + 
            " removed becasue no DIC was found.");
        count_of_patients_removed_because_of_no_DIC += to_remove.size();
        return to_remove.size();
    }
    
    static void sortDICPeriods(List<DICPeriod> periods_reference) {
        Collections.sort(periods_reference, new Comparator<DICPeriod>(){
            @Override
            public int compare(DICPeriod dicp1, DICPeriod dicp2) {
                if(dicp1.timestamps[0] > dicp2.timestamps[0])
                        return 1;
                if(dicp1.timestamps[0] < dicp2.timestamps[0])
                    return -1;
                return 0;
            }
        });
    }
    
    static Long[] compareDICPeriods(List<DICPeriod> periods_reference, List<DICPeriod> periods_comparison) {
        Long[] comparison = { 0L, 0L };
        if(periods_reference == null)
            return comparison;
        
        sortDICPeriods(periods_reference);
        if(periods_comparison != null)
            sortDICPeriods(periods_comparison);
        
        long total_reference_time = 0;
        long total_agreement_time = 0;
        
        for(DICPeriod period_reference : periods_reference) {
            total_reference_time += period_reference.timestamps[1] - period_reference.timestamps[0];
            if(periods_comparison != null)
                for(DICPeriod period_comparison : periods_comparison) {
                    if(period_comparison.timestamps[0] >= period_reference.timestamps[1])
                        break;
                    if(period_comparison.timestamps[1] <= period_reference.timestamps[0])
                        continue;
                    total_agreement_time += min(period_reference.timestamps[1], period_comparison.timestamps[1])
                        - max(period_reference.timestamps[0], period_comparison.timestamps[0]);
                }
        }
        
        comparison[0] = total_agreement_time;
        comparison[1] = total_reference_time;
        return comparison;
    }
    
    static void exportResults(Set<Patient> patients) {
        SummaryReportWriter srw = new SummaryReportWriter(Const.OUTPUT_FOLDER + execution_label + " Summary.txt");
        srw.write("## Summary report for HEDICIM labeled: \"" + execution_label + "\" ##");
        srw.write("# LAB_TIME_WINDOW: " + Const.LAB_TIME_WINDOW);
        srw.write("# EARLY_DEATH_TIME_WINDOW: " + Const.EARLY_DEATH_TIME_WINDOW);
        srw.write("# IGNORE_DIC_IF_NEAR_DEATH: " + Const.IGNORE_DIC_IF_NEAR_DEATH);
        srw.write("# IGNORE_MEDICATION_IF_FAR_FROM_DIC: " + Const.IGNORE_MEDICATION_IF_FAR_FROM_DIC);
        srw.write("# debug_patient_number_limiter: " + Const.debug_patient_number_limiter);
        srw.write("# origin_ids_chunk_size: " + Const.processing_chunk_size);
        if(Const.do_MIMICII) {
            srw.write("# ICD9_queries.length: " + Const.ICD9_queries.length);
            for(int i = 0; i < Const.ICD9_queries.length ; i++)
                srw.write("# > ICD9_queries[" + i + "]: " + Const.ICD9_queries[i]);
        }
        if(Const.do_BTRIS)
            srw.write("# BTRIS table queried: " + Const.BTRIS_TABLE);
        srw.write("Total patients processed: " + count_of_all_patients_processed);
        srw.write("Total non-neonates processed: " + count_of_non_neonates_processed);
        srw.write("Total eligible patients found: " + eligible_count);
        int eligible_dic_count = 0;
        for(Patient patient : patients)
            if(patient.eligible && patient.has_dic)
                eligible_dic_count++;
        srw.write("Total eligible patients with DIC found: " + eligible_dic_count);
        /*
        srw.write("LABEVENTS exceptions: " + msc.LABEVENTS_exceptions);
        srw.write("ADMISSIONS exceptions: " + msc.ADMISSIONS_exceptions);
        srw.write("ICUSTAY_DETAIL exceptions: " + msc.ICUSTAY_DETAIL_exceptions);
        srw.write("D_PATIENTS exceptions: " + msc.D_PATIENTS_exceptions);
        srw.write("POE_ORDER exceptions: " + msc.POE_ORDER_exceptions);
        srw.write("CHARTEVENTS exceptions: " + msc.CHARTEVENTS_exceptions);
        */
        srw.close();
        
        EnumMap<DICSS, EnumMap<DICSS, Long[]>> scorers_comparison_table_time = 
            compareDICSSOverlappingTime(patients);
        EnumMap<DICSS, EnumMap<DICSS, Long[]>> scorers_comparison_table_cases = 
            compareDICSSOverlappingCases(patients);
        EnumMap<DICSS, DICScore> score_structures = buildScoreStructures(patients);
        
        try(FileOutputStream file = new FileOutputStream(new File(Const.OUTPUT_FOLDER + execution_label + 
            " DICSS comparison.xlsx"))) {
            XSSFWorkbook workbook = new XSSFWorkbook();
            XSSFSheet sheet = workbook.createSheet("DIC SS Overlapping Periods of Time");
            int r = 0;
            Row row = sheet.createRow(r++);
            int c = 0;
            row.createCell(c++).setCellValue("Reference\\Comparison");
            for(DICSS dicss_reference : DICSS.values())
                row.createCell(c++).setCellValue(dicss_reference.name());
            row.createCell(c++).setCellValue("Total reference time (weeks)");
            for(DICSS dicss_reference : DICSS.values()) {
                row = sheet.createRow(r++);
                c = 0;
                row.createCell(c++).setCellValue(dicss_reference.name());
                for(DICSS dicss_comparison : DICSS.values())
                    row.createCell(c++).setCellValue(String.format("%.1f", 100d*((double)(scorers_comparison_table_time.
                        get(dicss_reference).get(dicss_comparison)[0]))/((double)(scorers_comparison_table_time.get(
                        dicss_reference).get(dicss_comparison)[1])))+"%");
                row.createCell(c++).setCellValue(String.format("%.1f", 100d*((double)(scorers_comparison_table_time.get(
                    dicss_reference).get(dicss_reference)[1]))/(1000d*60d*60d*24d*7d)));
            }
            
            sheet = workbook.createSheet("DIC SS Overlapping Cases");
            r = 0;
            row = sheet.createRow(r++);
            c = 0;
            row.createCell(c++).setCellValue("Reference\\Comparison");
            for(DICSS dicss_reference : DICSS.values())
                row.createCell(c++).setCellValue(dicss_reference.name());
            for(DICSS dicss_reference : DICSS.values()) {
                row = sheet.createRow(r++);
                c = 0;
                row.createCell(c++).setCellValue(dicss_reference.name());
                for(DICSS dicss_comparison : DICSS.values())
                    row.createCell(c++).setCellValue(String.format("%.1f", 100d*((double)(scorers_comparison_table_cases
                        .get(dicss_reference).get(dicss_comparison)[0]))/((double)(scorers_comparison_table_cases.get(
                        dicss_reference).get(dicss_comparison)[1])))+"%");
            }
            
            for(DICSS dicss : DICSS.values()) {
                sheet = workbook.createSheet(dicss.name() + " score structure");
                r = 0;
                row = sheet.createRow(r++);
                c = 0;
                DICScore dic_score = score_structures.get(dicss);
                if(dic_score.itemids.isEmpty()) {
                    row.createCell(c++).setCellValue("Empty.");
                    continue;
                }
                row.createCell(c++).setCellValue("ITEMID");
                row.createCell(c++).setCellValue("Contribution to score (%)");
                for(ITEMID itemid : dic_score.itemids.keySet()) {
                    row = sheet.createRow(r++);
                    c=0;
                    row.createCell(c++).setCellValue(itemid.name());
                    row.createCell(c++).setCellValue(100d*dic_score.itemids.get(itemid));
                }                
                row = sheet.createRow(r++);
                c=0;
                row.createCell(c++).setCellValue("Average score");
                row.createCell(c++).setCellValue(dic_score.score);
            }
            
            workbook.write(file);
            file.close();                    
        } catch(Exception ex) {
            System.out.print("Unable to open " + Const.OUTPUT_FOLDER + execution_label + " DICSS comparison.xlsx" + 
                " for writing:\n" + ex.getMessage());
        }
        
    }
    
    static EnumMap<DICSS, EnumMap<DICSS, Long[]>> compareDICSSOverlappingTime(Set<Patient> patients) {
        EnumMap<DICSS, EnumMap<DICSS, Long[]>> comparison_table = new EnumMap<>(DICSS.class);
        
        for(DICSS dicss_reference : DICSS.values()) {
            EnumMap<DICSS, Long[]> dicss_reference_map = new EnumMap<>(DICSS.class);
            for(DICSS dicss_comparison : DICSS.values()) {
                Long[] comparison = { 0L, 0L };
                dicss_reference_map.put(dicss_comparison, comparison);
            }
            comparison_table.put(dicss_reference, dicss_reference_map);
        }

        for(Patient patient : patients)
            for(DICSS dicss_reference : DICSS.values())
                for(DICSS dicss_comparison : DICSS.values()) {
                    Long[] comparison = compareDICPeriods(patient.dic_periods.get(dicss_reference).
                        get(dic_scorers.get(dicss_reference).getDiagnosticScore()),
                        patient.dic_periods.get(dicss_comparison).
                        get(dic_scorers.get(dicss_comparison).getDiagnosticScore()));
                    comparison_table.get(dicss_reference).get(dicss_comparison)[0] += comparison[0];
                    comparison_table.get(dicss_reference).get(dicss_comparison)[1] += comparison[1];
                }
        
        return comparison_table;
    }
    
    static EnumMap<DICSS, EnumMap<DICSS, Long[]>> compareDICSSOverlappingCases(Set<Patient> patients) {
        EnumMap<DICSS, EnumMap<DICSS, Long[]>> comparison_table = new EnumMap<>(DICSS.class);
        
        for(DICSS dicss_reference : DICSS.values()) {
            EnumMap<DICSS, Long[]> dicss_reference_map = new EnumMap<>(DICSS.class);
            for(DICSS dicss_comparison : DICSS.values()) {
                Long[] comparison = { 0L, 0L };
                dicss_reference_map.put(dicss_comparison, comparison);
            }
            comparison_table.put(dicss_reference, dicss_reference_map);
        }

        for(Patient patient : patients)
            for(DICSS dicss_reference : DICSS.values())
                if(patient.has_1_day_dic.get(dicss_reference) != null)
                    for(DICSS dicss_comparison : DICSS.values()) {
                        if(patient.has_1_day_dic.get(dicss_comparison) != null)
                            comparison_table.get(dicss_reference).get(dicss_comparison)[0] += 1L;
                        comparison_table.get(dicss_reference).get(dicss_comparison)[1] += 1L;
                    }
        
        return comparison_table;
    }
    
    static EnumMap<DICSS, DICScore> buildScoreStructures(Set<Patient> patients) {
        EnumMap<DICSS, DICScore> dics_score_structures = new EnumMap<>(DICSS.class);

        for(DICSS dicss : DICSS.values()) {
            DICScore structure_count = new DICScore();
            DICScore structure_sum = new DICScore();
            for(Patient patient : patients)
                if(patient.has_1_day_dic.containsKey(dicss))
                    for(DICPeriod dic_period : patient.dic_periods.get(dicss).get(dic_scorers.get(dicss).
                        getDiagnosticScore()))
                        for(DICScore dic_score : dic_period.dic_scores) {
                            structure_sum.score += dic_score.score;
                            structure_count.score += 1d;
                            for(ITEMID itemid : dic_score.itemids.keySet()) {
                                structure_sum.itemids.put(itemid, (structure_sum.itemids.containsKey(itemid)?
                                    structure_sum.itemids.get(itemid):0d) + 
                                    (dic_score.itemids.get(itemid)/dic_score.score));
                                structure_count.itemids.put(itemid, (structure_count.itemids.containsKey(itemid)?
                                    structure_count.itemids.get(itemid):0d) + 1d);
                            }
                        }
            if(structure_count.score > 0) {
                structure_sum.score /= structure_count.score;
                for(ITEMID itemid : structure_sum.itemids.keySet())
                    structure_sum.itemids.put(itemid, structure_sum.itemids.get(itemid) / 
                        structure_count.itemids.get(itemid));
                dics_score_structures.put(dicss, structure_sum);
            }
            else
                // If no scores, leave a blank one.
                dics_score_structures.put(dicss, new DICScore());
        }
        
        
        for(DICSS dicss : DICSS.values()) {
            DICScore dic_score = dics_score_structures.get(dicss);
            System.out.print("## " + dicss.name() + " score structure: ");
            if(dic_score.itemids.isEmpty()) {
                System.out.println("empty.");
                continue;
            }
            for(ITEMID itemid : dic_score.itemids.keySet())
                System.out.print(itemid.name() + "=" + String.format("%.1f", 
                    100d*dic_score.itemids.get(itemid)) + "%, ");
            System.out.print(" average score = " + String.format("%.1f", dic_score.score) + ".\n");
        }
        
        return dics_score_structures;
    }
    
    static void reportComparisons(Set<Patient> patients) {
        EnumMap<DICSS, EnumMap<DICSS, Long[]>> dicss_comparison_time = compareDICSSOverlappingTime(patients);
        EnumMap<DICSS, EnumMap<DICSS, Long[]>> dicss_comparison_cases = compareDICSSOverlappingCases(patients);

        for(DICSS dicss_reference : DICSS.values()) {
            System.out.print("# " + dicss_reference.name() + " vs: ");
            for(DICSS dicss_comparison : DICSS.values()) {
                System.out.print(dicss_comparison.name() + "=" + String.format("%.1f", 100d*((double)(dicss_comparison_time.
get(dicss_reference).get(dicss_comparison)[0]))/((double)(dicss_comparison_time.get(dicss_reference).get(dicss_comparison)[1]))) +
"% ("+String.format("%.1f", 100d*((double)(dicss_comparison_cases.get(dicss_reference).get(dicss_comparison)[0]))/((double)
(dicss_comparison_cases.get(dicss_reference).get(dicss_comparison)[1]))) + "%), ");
            }
            System.out.print(" total = " + String.format("%.1f", 100d*((double)(dicss_comparison_time.get(dicss_reference).
                get(dicss_reference)[1]))/(1000d*60d*60d*24d*7d)) + " weeks of DIC.\n");
        }
    }
    
    /**
     * @param args the command line arguments
     * @throws java.lang.Exception
     */
    public static void main(String[] args) throws Exception {
        dic_scorers.put(DICSS.ISTH, new ISTHDICScorer());
        ITEMID.DICSS_to_ITEMID.put(DICSS.ISTH, ITEMID.DIC_SCORE_ISTH);
        dic_scorers.put(DICSS.JMHW, new JMHWDICScorer());
        ITEMID.DICSS_to_ITEMID.put(DICSS.JMHW, ITEMID.DIC_SCORE_JMHW);
        dic_scorers.put(DICSS.JAAM, new JAAMDICScorer());
        ITEMID.DICSS_to_ITEMID.put(DICSS.JAAM, ITEMID.DIC_SCORE_JAAM);
        dic_scorers.put(DICSS.KSTH, new KSTHDICScorer());
        ITEMID.DICSS_to_ITEMID.put(DICSS.KSTH, ITEMID.DIC_SCORE_KSTH);
        execution_label = execution_label + " " + output_file_date_format.format(new java.util.Date().getTime());
        
        Set<Patient> all_dic_patients = new HashSet<>();
        Set<Patient> patient_chunk = new HashSet<>();

        if(Const.do_MIMICII) {
            execution_label = (execution_label + " MIMICII").trim();
            msc = new MySQLConnMIMICII();
            for(Iterator it = msc.getPatientsFromTableICD9(Const.ICD9_queries).iterator(); it.hasNext();) {
                patient_chunk.add((Patient)it.next());
                if(patient_chunk.size() < Const.processing_chunk_size && it.hasNext())
                    continue;

                msc.queryADMISSIONS(patient_chunk);
                msc.queryICUSTAY_DETAIL(patient_chunk);
                count_of_all_patients_processed += patient_chunk.size();
                removeNonAdults(patient_chunk);
                count_of_non_neonates_processed += patient_chunk.size();
                msc.queryLABEVENTS(patient_chunk);
                msc.queryD_PATIENTS(patient_chunk);  // Must be called after queryADMISSIONS, because it needs the 
                    // SUBJECT_ID
                msc.queryICD9(patient_chunk);
                msc.queryCHARTEVENTS(patient_chunk);

                for(Patient patient : patient_chunk) {
                    patient.alignTimestampsToHospitalAdmission();
                    patient.sortEvents();
                    //patient.removeLabEventsNearDeath(Const.LAB_TIME_WINDOW);
                    patient.generatePlateletReductionEvents(); // Needs to be done after sorting.
                    patient.generateSIRSEvents();
                    for(DICSS dicss : DICSS.values())
                        patient.dic_periods.put(dicss, dic_scorers.get(dicss).calculateDICPeriods(patient));
                    
                    if(patient.eligible)
                        eligible_count++;
                }

                //removePatientsWithoutDIC(patient_chunk);
                msc.queryPOE_ORDER(patient_chunk);

                EventFlowConnector efc = new EventFlowConnector(Const.OUTPUT_FOLDER +
                    execution_label + " EventFlow.txt");
                for(Patient patient : patient_chunk)
                    for(DICSS dicss : DICSS.values()) {
                        for(int i : dic_scorers.get(dicss).getScoresToReport())
                            if(patient.dic_periods.get(dicss).containsKey(i))
                                for(DICPeriod dic_period : patient.dic_periods.get(dicss).get(i))
                                    efc.writeEvent(patient.origin_id, dicss.name() + ">="+i,
                                        dic_period.timestamps[0], dic_period.timestamps[1]);
                        for(Long[] heparin_period : patient.heparin_periods)
                            efc.writeEvent(patient.origin_id, "Heparin", heparin_period[0], heparin_period[1]);
                        if(patient.dod != 0 && patient.dod-patient.ts_first_dic_event < Const.EARLY_DEATH_TIME_WINDOW)
                            efc.writeEvent(patient.origin_id, "Death", patient.dod);
                    }
                efc.close();
                all_dic_patients.addAll(patient_chunk);
                patient_chunk.clear();
                Patient.makeStatFile(Const.OUTPUT_FOLDER + execution_label + ".xlsx");
                System.out.println("# So far: " + count_of_all_patients_processed + " total patients processed, " +
                    count_of_non_neonates_processed + " were non-neonates, " + eligible_count + 
                    " were eligible, " + countPatientsWithDIC(all_dic_patients) + " had any DIC.");
                reportComparisons(all_dic_patients);
                buildScoreStructures(all_dic_patients);
                exportResults(all_dic_patients);
            }
        }

        if(Const.do_BTRIS) {
            execution_label = (execution_label + " BTRIS").trim();
            mscb = new MySQLConnBTRIS();
            for(Iterator it = mscb.getPatients().iterator(); it.hasNext();) {
                patient_chunk.add((Patient)it.next());
                if(patient_chunk.size() < Const.processing_chunk_size && it.hasNext())
                    continue;

                mscb.getLabs(patient_chunk);
                mscb.getICD9(patient_chunk);

                count_of_all_patients_processed += patient_chunk.size();
                // No need to remove neonates from BTRIS, this is done via the SQL query in getPatients.

                for(Patient patient : patient_chunk) {
                    //patient.alignTimestampsToHospitalAdmission();
                    patient.sortEvents();
                    //patient.removeLabEventsNearDeath(Const.LAB_TIME_WINDOW);
                    patient.generatePlateletReductionEvents(); // Needs to be done after sorting.
                    for(DICSS dicss : DICSS.values())
                        patient.dic_periods.put(dicss, dic_scorers.get(dicss).
                            calculateDICPeriods(patient));
                    if(patient.eligible)
                        eligible_count++;
                }

                //removePatientsWithoutDIC(patient_chunk);

                EventFlowConnector efc = new EventFlowConnector(Const.OUTPUT_FOLDER + execution_label +
                    " EventFlow.txt");
                for(Patient patient : patient_chunk)
                    for(DICSS dicss : DICSS.values()) {
                        for(int i : dic_scorers.get(dicss).getScoresToReport())
                            if(patient.dic_periods.get(dicss).containsKey(i))
                                for(DICPeriod dic_period : patient.dic_periods.get(dicss).get(i))
                                    efc.writeEvent(patient.origin_id, dicss.name() + ">="+i,
                                        dic_period.timestamps[0], dic_period.timestamps[1]);

                    for(Long[] heparin_period : patient.heparin_periods)
                        efc.writeEvent(patient.origin_id, "Heparin", heparin_period[0], heparin_period[1]);

                    if(patient.dod != 0)
                        if(patient.dod-patient.ts_first_dic_event < Const.
                            EARLY_DEATH_TIME_WINDOW)
                            efc.writeEvent(patient.origin_id, "Death", patient.dod);
                }
                efc.close();

                all_dic_patients.addAll(patient_chunk);
                patient_chunk.clear();

                System.out.println("# So far: " + count_of_all_patients_processed + " total patients processed, " +
                    count_of_non_neonates_processed + " were non-neonates, " + eligible_count + 
                    " were eligible.");
                reportComparisons(all_dic_patients);
                buildScoreStructures(all_dic_patients);
            }
        }

        exportResults(all_dic_patients);
    }
}
