package com.distributed.chatapp;

import org.apache.commons.cli.*;
import org.jgroups.*;
import org.jgroups.util.Util;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@SpringBootApplication
public class P2PChatGame extends ReceiverAdapter {

    private JChannel channel;
    private String userName;
    private String clusterName;
    private View lastView;
    private boolean running = true;
    private Integer groundNumber = null;
    private int to_guess = 0;

    // Our shared state
    //private Integer messageCount = 0;
    private SharedState state = new SharedState();

    /**
     * Connect to a JGroups cluster using command line options
     * @param args command line arguments
     * @throws Exception
     */
    private void start(String[] args) throws Exception {
        processCommandline(args);

        // Create the channel
        // This file could be moved, or made a command line option.
        channel = new JChannel("src/main/resources/udp.xml");

        // Set a name
        channel.name(userName);

        // Register for callbacks
        channel.setReceiver(this);

        // Ignore own messages
        channel.setDiscardOwnMessages(true);

        // Connect
        channel.connect(clusterName);

        // Start state transfer
        channel.getState(null, 0);

        // Do the things
        processInput();

        // Clean up
        channel.close();

    }

    /**
     * Quick and dirty implementaton of commons cli for command line args
     * @param args the command line args
     * @throws ParseException
     */
    private void processCommandline(String[] args) throws ParseException {

        // Options, parser, friendly help
        Options options = new Options();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();

        options.addOption("u", "user", true, "User name")
                .addOption("c", "cluster", true, "Cluster name");


        CommandLine line = parser.parse(options, args);


        if (line.hasOption("user")) {
            userName = line.getOptionValue("user");
        } else {
            formatter.printHelp("JGroupsMessenger: need a user name.\n", options);
            System.exit(-1);
        }

        if (line.hasOption("cluster")) {
            clusterName = line.getOptionValue("cluster");
        } else {
            formatter.printHelp("JGroupsMessenger: need a cluster name.\n", options);
            System.exit(-1);
        }
    }

    // Start it up
    public static void main(String[] args) throws Exception {
        new P2PChatGame().start(args);
    }


    @Override
    public void viewAccepted(View newView) {

        // Save view if this is the first
        if(lastView==null | state.lastView==null){
            state.liderName = userName;
            state.status = GameStatus.CHOOSING_MASTER;
            System.out.println("Received initial view:");
            newView.forEach(System.out::println);
        } else {

            // Compare to last view
            System.out.println("Received new view.");

            List<Address> newMembers = View.newMembers(lastView, newView);
            System.out.println("New members: ");
            newMembers.forEach(System.out::println);

            List<Address> exMembers = View.leftMembers(lastView, newView);
            System.out.println("Exited members:");
            exMembers.forEach(System.out::println);
        }
        lastView = newView;
        state.lastView = newView.getMembers().get(0).toString();
        System.out.print(newView.getMembers().get(0).toString());
    }

    private Address selectRandomTurn(){
                View view = channel.view();
                List<Address> members = view.getMembers();
        return members.get(new Random().nextInt(members.size()));
    }

    /**
     * Loop on console input until we see 'x' to exit
     */
    private void processInput() throws Exception {

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        while (running) {
            try {

                View view = channel.view();
                List<Address> members = view.getMembers();

                if (state.liderName!=null && userName!=null && (state.liderName.equals(userName)) && (2 == members.size())) {
                    state.status = GameStatus.ROUNDSTARTED;
                }

                if (state.status == GameStatus.CHOOSING_MASTER) {
                    System.out.println("Print READY to start a game");
                    System.out.flush();
                    String ready = in.readLine().toLowerCase();
                    if (ready.equals("READY")) {
                        state.readyPlayers.add(userName);
                    }
                    if (state.liderName!=null && userName!=null && state.liderName.equals(userName)){
                        View view2 = channel.view();
                        List<Address> members23 = view2.getMembers();
                        state.masterName = members23.get(new Random().nextInt(members.size())).toString();
                    }
                }

                if (state.status == GameStatus.ROUNDSTARTED){
                    System.out.print(String.format("State %s", state.status.toString()));

                    if (userName!=null && state.masterName!=null && state.masterName.equals(userName)){
                        state.status = GameStatus.THINKING;
                        sendMessage(null, "Master is thinking of a number");
                        System.out.println("Think of a number");
                        System.out.flush();
                        groundNumber = Integer.parseInt(in.readLine().toLowerCase());
                        System.out.println("Think of a range. Lowest");
                        System.out.flush();
                        state.a = in.readLine().toLowerCase();
                        System.out.println("Think of a range. Highest");
                        System.out.flush();
                        state.b = in.readLine().toLowerCase();
                        state.status = GameStatus.PLAYING;
                        System.out.print(String.format("State %s", state.status.toString()));
                    }
                }


                if (state.status == GameStatus.PLAYING){
                    if (state.masterName!=null && userName!=null && !state.masterName.equals(userName)){
                        System.out.print("Guess Your Number! ");
                        System.out.print(String.format("The range is %d - %d", state.a, state.b));
                        System.out.flush();
                        Integer guessedNumber = Integer.parseInt(in.readLine().toLowerCase());
                        View view2 = channel.view();
                        List<Address> members1 = view2.getMembers();
                        List<String> members2 = new ArrayList<>();
                        for (Address addr : members1) {
                            members2.add(addr.toString());
                        }
                        Address dest = members1.get(members2.indexOf(state.masterName));
                        sendMessage(dest, guessedNumber.toString());
                    }
                }


            } catch (IOException ioe) {
                running = false;
            }
        }
        System.out.println("Exiting.");
    }

    /**
     * Send message from here
     * @param destination the destination
     * @param messageString the message
     */
    private Boolean sendMessage(Address destination, String messageString) {
        Boolean success = false;
        try {
            System.out.println("Sending " + messageString + " to " + destination);
            Message message = new Message(destination, messageString);
            channel.send(message);
            success = true;
        } catch (Exception exception) {
            System.err.println("Exception sending message: " + exception.getMessage());
            running = false;
            success = false;

        }
        return success;
    }

    @Override
    public void receive(Message message) {

        View view = channel.view();
        List<Address> members = view.getMembers();
        if ((state.liderName.equals(userName)) && (state.readyPlayers.size() == members.size())) {
            state.status = GameStatus.ROUNDSTARTED;
        }
        if (state.status ==  GameStatus.PLAYING){
            if (userName!=null && state.masterName!=null && state.masterName.equals(userName) && message.getDest().toString().equals(userName)){
                if (groundNumber != null && message.getObject()!=null && message.getObject().equals(groundNumber.toString())){
                    sendMessage(null, String.format("The winner is %s. And the number was %s", message.getSrc(), groundNumber.toString()));
                    state.status = GameStatus.CHOOSING_MASTER;
                    sendMessage(null, "Round has ended!");
                }
            }
        }
        // Print source and dest with message
        String line = "Message received from: " + message.getSrc() + " to: " + message.getDest() + " -> " + message.getObject();



        System.out.println(line);
    }

    @Override
    public void getState(OutputStream output) throws Exception {
        // Serialize into the stream
        Util.objectToStream(state, new DataOutputStream(output));
    }

    @Override
    public void setState(InputStream input) {

        // NOTE: since we know that incrementing the count and transferring the state
        // is done inside the JChannel's thread, we don't have to worry about synchronizing
        // messageCount. For production code it should be synchronized!
        try {
            // Deserialize
            state = Util.objectFromStream(new DataInputStream(input));
        } catch (Exception e) {
            System.out.println("Error deserialing state!");
            e.printStackTrace();
        }
        //System.out.println(messageCount + " is the current messagecount.");
    }


    private Optional<Address> getAddress(String name) {
        View view = channel.view();
        return view.getMembers().stream().filter(address -> name.equals(address.toString())).findFirst();
    }

}