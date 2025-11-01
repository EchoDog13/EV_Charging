package com.kylebarker.ev_driver;

public class Simulation
 {
    public static void main(String[] args) throws InterruptedException 
    {
        CentralServer centralServer = new CentralServer();

        Driver Sufyan = new Driver(1001, centralServer);
        Driver Kyle = new Driver(2002, centralServer); 
        Driver khalid = new Driver(3003, centralServer); 

        Thread tA = new Thread(Sufyan);
        Thread tF = new Thread(Kyle);
        Thread tK = new Thread(khalid);

        System.out.println("Start");
        
        tA.start();
        tF.start();
        tK.start();

        tA.join();
        tF.join();
        tK.join();
        
        System.out.println("\nFinished");
        System.out.println(Sufyan.getFinalStatus());
        System.out.println(Kyle.getFinalStatus());
        System.out.println(khalid.getFinalStatus());
    }
}