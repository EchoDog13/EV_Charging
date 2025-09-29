package com.kylebarker.ev_central;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.kylebarker.ev_central.model.Charger;
import com.kylebarker.ev_central.model.chargerState;
import com.kylebarker.ev_central.repository.ChargerRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.*;

@SpringBootApplication
public class EV_Central {

    private static ChargerRepository chargerRepository;

    public static void main(String[] args) {
        // Start Spring application context
        ApplicationContext context = SpringApplication.run(EV_Central.class, args);

        // Get the repository bean from Spring context
        chargerRepository = context.getBean(ChargerRepository.class);

        int port = 5500; // default port

        // Set port from command line argument
        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }

        // Create server socket
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Central server listening on port " + port);

            // Continuously accept client connections
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());

                // Handle client in a new thread
                new Thread(() -> handleClient(clientSocket)).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles communication with a connected client.
     *
     * @param clientSocket The socket connected to the client.
     */
    private static void handleClient(Socket clientSocket) {
        try (
                // Create input and output streams
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            ObjectMapper mapper = new ObjectMapper();



            String line;
            while ((line = in.readLine()) != null) {


                JsonNode node = mapper.readTree(line);
                if(node.has("function")){
                    String function = node.get("function").asText();
                    if (function.equals("register")) {
                       String result =  registerChargerDB(node);
                       out.println(result);
                    }
                }
                else {
                    // Echo any other messages
                    out.println("ECHO: " + line);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
                System.out.println("Client disconnected.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static String registerChargerDB(JsonNode node) {

        ObjectMapper mapper = new ObjectMapper();
        try {

            Charger charger = mapper.treeToValue(node, Charger.class);
            Charger savedCharger = chargerRepository.save(charger);
            return "REGISTER,200,Charger ID: " + savedCharger.getUid();

        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}