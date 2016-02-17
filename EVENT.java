/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package hedicim;

import java.util.Date;

/**
 *
 * @author kuryfs
 */
public class EVENT {
    public Integer ITEMID = 0;
    public Date CHARTTIME = new Date(0);
    public String VALUE = "";
    public Float VALUENUM = 0f;
    public String FLAG = "";
    public String VALUEUOM = "";

    public EVENT() {};
    
    public EVENT(Integer _ITEMID, Date _CHARTTIME, String _VALUE, Float _VALUENUM, String _FLAG, String _VALUEUOM) {
        ITEMID = _ITEMID;
        CHARTTIME = _CHARTTIME;
        VALUE = _VALUE;
        VALUENUM = _VALUENUM;
        FLAG = _FLAG;
        VALUEUOM = _VALUEUOM;
    }
    
    public EVENT(EVENT copy_me) {
        ITEMID = copy_me.ITEMID;
        CHARTTIME = new Date(copy_me.CHARTTIME.getTime());
        VALUE = copy_me.VALUE;
        VALUENUM = copy_me.VALUENUM;
        FLAG = copy_me.FLAG;
        VALUEUOM = copy_me.VALUEUOM;
    }
}
