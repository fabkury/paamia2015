/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package hedicim;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 *
 * @author kuryfs
 */
public class SummaryReportWriter {
    String filename = "";
    PrintWriter pw = null;
    
    static SimpleDateFormat date_format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    public SummaryReportWriter(String output_filename) {
        try {
            pw = new PrintWriter(new FileWriter(output_filename, true));
            filename = output_filename;
        } catch(IOException e) {
            System.out.println("Unable to open file " + output_filename + " for writing.");
            pw = null;
        }
    }
    
    public void close() {
        if(pw != null) {
            write("-- Summary report \"" + filename + "\" closed on " + date_format.format(new java.util.Date().getTime()) + ".");
            pw.close();
            System.out.println("Summary report " + filename + " succesfully written.");
            pw = null;
        } else
            System.out.println("Unable to write summary report to " + filename + ".");
    }
    
    public void write(String string) {
        if(pw != null)
            pw.println(string);
    }
}
