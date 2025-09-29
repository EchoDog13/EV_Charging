package com.kylebarker.ev_cp;

import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class EV_CP_M implements Runnable {

    @Option(names = {"-cp", "--central-port"}, description = "Central server port", required = true)
    static Integer centralPort;

    @Option(names = {"-ci", "--central-ip"}, description = "Central server IP or hostname", required = true)
    static String centralIP;

    @Option(names = {"-ep", "--engine-port"}, description = "Port of the engine")
    static Integer enginePort;

    @Option(names = {"-ei", "--engine-ip"}, description = "Engine IP or hostname")
    static String engineIP;

    @Option(names = {"-u", "--uid"}, description = "Unique Identifier of charge point")
    static int uid;

    @Option(names = {"-c", "--cost"}, description = "Price per KW (default: ${DEFAULT-VALUE})")
    static double pricePerKW = 10;

    @Option(names = {"-r", "--register"}, description = "Register with the central server")
    static boolean registerFlag = false;

    @Option(names = {"-l", "--location"}, description = "Charging point location")
    static String cpLocation;



    static Socket centralSocket = null;
    static BufferedReader in = null;
    static PrintWriter out = null;

    static String state = "disconnected";

    @Override
    public void run() {
        // Print parsed configuration
        System.out.println("Parsed configuration:");
        System.out.println("IP: " + centralIP);
        System.out.println("Port: " + centralPort);
        System.out.println("Engine port: " + enginePort);
        System.out.println("Engine IP: " + engineIP);
        System.out.println("UID: " + uid);

        System.out.println("Register flag: " + registerFlag);
        System.out.println("Location: " + cpLocation);
        System.out.println("Price per KW: " + pricePerKW);



        //Initalise a socket connection with the central server
        initConnection();

        if (registerFlag) {
            //Check that all the required arguments are given for registration
            if ((centralIP != null) && (!centralIP.isEmpty()) && (centralPort > 0) && enginePort > 0 && engineIP != null && !engineIP.isEmpty()) {
            register();
        }}

        try {
            out.println("Hello Server!");
            String serverMsg = in.readLine();
            System.out.println("Received from server: " + serverMsg);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (centralSocket != null) centralSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void initConnection() {
        int retries = 100;
        int waitTime = 1000;
        for (int i = 0; i < retries; i++) {
            try {
                centralSocket = new Socket(centralIP, centralPort);
                in = new BufferedReader(new InputStreamReader(centralSocket.getInputStream()));
                out = new PrintWriter(centralSocket.getOutputStream(), true);
                break;
            } catch (IOException e) {
                e.printStackTrace();
                try { Thread.sleep(waitTime); } catch (InterruptedException ignored) {}
            }
        }
    }

    public static boolean register() {
        System.out.println("Registering with central server at " + centralIP + ":" + centralPort);

        if (centralSocket == null) {
            initConnection();
        }

        out.println("REGISTER," + cpLocation + "," + pricePerKW + "," + state);

        try {
            String response = in.readLine();
            if (response != null && response.startsWith("REGISTER")) {
                switch (response.split(",")[1]) {
                    case "200":
                        System.out.println("Successfully registered with central server");
                        return true;
                    case "409":
                        System.out.println("Already registered with central server");
                        return false;
                }
            } else {
                System.out.println("Unknown response from server: " + response);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new EV_CP_M()).execute(args);
        System.exit(exitCode);
    }
}