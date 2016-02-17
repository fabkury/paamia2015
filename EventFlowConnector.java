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
public class EventFlowConnector {
    String filename = "";
    PrintWriter pw = null;
    
    static SimpleDateFormat date_format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    public EventFlowConnector(String output_filename) {
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
            pw.close();
            System.out.println(filename + " succesfully written.");
        } else
            System.out.println("Unable to write to " + filename + ".");
    }
    
    public void writeEvent(String id, String name, long timestamp_start) {
        if(pw != null)
            pw.println(id + "\t" + name + "\t" + date_format.format(new Date(timestamp_start)) + "\t \t ");
    }
    
    public void writeEvent(String id, String name, long timestamp_start, long timestamp_end) {
        if(pw != null)
            pw.println(id + "\t" + name + "\t" + date_format.format(new Date(timestamp_start)) + "\t"
                + date_format.format(new Date(timestamp_end)) + "\t ");
    }
    
    public void writeEvent(String id, String name, long timestamp_start, long timestamp_end, String comment) {
        if(pw != null)
            pw.println(id + "\t" + name + "\t" + date_format.format(new Date(timestamp_start)) + "\t"
                + date_format.format(new Date(timestamp_end)) + "\t" + comment);
    }
}
