package Test;

import common.java.httpServer.booter;
import common.java.nlogger.nlogger;

public class TestReport {
    public static void main(String[] args) {
        booter booter = new booter();
        try {
            System.out.println("GrapeReport");
            System.setProperty("AppName", "GrapeReport");
            booter.start(1006);
        } catch (Exception e) {
            nlogger.logout(e);
        }
    } 
}
