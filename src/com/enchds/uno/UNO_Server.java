/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.enchds.uno;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author mOSU_
 */
public class UNO_Server extends javax.swing.JFrame {
    private static final long serialVersionUID = 1L;
    private ArrayList<UnoCard> deck = new ArrayList<>(108);
    private ArrayList<UnoCard> discard = new ArrayList<>(50);
    private ArrayList<ArrayList<UnoCard>> hands = new ArrayList<>(2);
    private ServerConnection[] sc;
    private String[] names;
    private int players = 0;
    private int maxPlayers = 10;
    private ServerSocket ss2;
    private Socket s;
    private String desc;
    private boolean gameReady = false;
    private int port = 19283;
    private int score = 500;
    private int roundRace = 1;
    private int gameRace = 1;
    
    private class ServerConnectThread extends Thread {
        @Override
        public void run() {
            while (players < maxPlayers && !gameReady) {
                try {
                    s = ss2.accept();
                    sc[players] = new ServerConnection(s);
                    while (sc[players].os == null) {}
                    if (!desc.isEmpty()) desc(sc[players]);
                    tell(sc[players], "NewPlayer" + String.format("%1$02d%2$02d", players, maxPlayers));
                    // for example, "NewPlayer0010" would be given to player 1/10
                    names[players] = sc[players].is.readLine();
                    players++;
                    jLabel2.setText(players + "/" + maxPlayers + " players have now joined.");
                    if (!isVisible()) {
                        System.out.println(players + "/" + maxPlayers + " players have now joined.");
                    }
                } catch (IOException e) {
                    System.err.println("A connection error has occurred while trying to add a player.");
                }
            }
            gameReady = true;
            jButton2.setEnabled(false);
        }
    }
    
    private class ServerGameThread extends Thread {
        @Override
        public void run() {
            if (roundRace < 1 - players) roundRace = 1 - players;
            if (roundRace > players) roundRace = players;
            if (roundRace <= 0) roundRace += maxPlayers;
            if (gameRace < 1 - players) gameRace = 1 - players;
            if (gameRace > players) gameRace = players;
            if (gameRace <= 0) gameRace += maxPlayers;
            ServerConnection[] scTemp = sc.clone();
            sc = new ServerConnection[players];
            System.arraycopy(scTemp, 0, sc, 0, players);
            String nameList = "NAME:";
            for (int i = 0; i < players; i++) {
                nameList += names[i];
                if (i != players - 1) nameList += "-";
            }
            for (ServerConnection connection : sc) tell(connection, nameList);
            int[] scores = new int[players];
            int playersScored = 0;
            boolean[] isOut = new boolean[players];
            int playersOut = 0;
            score:
            do {
                // Set up deck
                for (int i = 0; i < 4; i++) {
                    deck.add(new UnoCard(0, CardColour.values()[i]));
                }
                for (int i = 0, j = 0; !(i == 12 && j == 0); j++, j %= 4) {
                    if (j == 0) i++;
                    deck.add(new UnoCard(i, CardColour.values()[j]));
                }
                for (int i = 0, j = 0; !(i == 12 && j == 0); j++, j %= 4) {
                    if (j == 0) i++;
                    deck.add(new UnoCard(i, CardColour.values()[j]));
                }
                for (int i = 0; i < 4; i++) {
                    deck.add(new UnoCard(CardValue.WildCard, CardColour.Wild));
                    deck.add(new UnoCard(CardValue.DrawFour, CardColour.Wild));
                }
                Collections.shuffle(deck);
                // Start discard pile
                while (deck.get(0).value == CardValue.DrawFour) Collections.shuffle(deck);
                boolean rvs = deck.get(0).value == CardValue.Reverse;
                int currentPlayer = 0;
                // Start game
                boolean calledUno = false;
                boolean cardPlayed = true;
                boolean playerSkipped = false;
                boolean drawFour = false;
                boolean matchColour = false;
                int previousPlayer = 0;
                if (deck.get(0).value == CardValue.Skip || deck.get(0).value == CardValue.DrawTwo)
                    currentPlayer = 1 % players;
                if (rvs) currentPlayer = players - 1;
                discard.add(deck.get(0));
                deck.remove(0);
                updateCard(sc);
                // Set up hands
                for (int i = 0; i < players; i++) {
                    hands.add(new ArrayList<>(7));
                    for (int j = 0; j < 7; j++) {
                        hands.get(i).add(deck.get(0));
                        deck.remove(0);
                    }
                }
                if (discard.get(0).value == CardValue.DrawTwo)
                    for (int i = 0; i < 2; i++) {
                        hands.get(0).add(deck.get(0));
                        deck.remove(0);
                    }
                updateTurn(sc, currentPlayer, isOut);
                updateScores(sc, scores);
                game:
                while (playersOut < roundRace) {
                    if (deck.isEmpty()) {
                        while (discard.size() > 1) {
                            if (discard.get(discard.size() - 1).value == CardValue.WildCard
                                    || discard.get(discard.size() - 1).value == CardValue.DrawFour) {
                                discard.get(discard.size() - 1).colour = CardColour.Wild;
                            }
                            deck.add(discard.get(discard.size() - 1));
                            discard.remove(discard.size() - 1);
                        }
                        Collections.shuffle(deck);
                    }
                    if (!drawFour) {
                        String[] options = new String[hands.get(currentPlayer).size() + (deck.isEmpty() ? 1 : 2)];
                        options[0] = "CallUno";
                        for (int i = 0; i < hands.get(currentPlayer).size(); i++) {
                            options[i + 1] = hands.get(currentPlayer).get(i).toString();
                        }
                        if (!deck.isEmpty()) options[hands.get(currentPlayer).size() + 1] = "DrawPile";
                        else {
                            check:
                            do {
                                for (UnoCard currentCard : hands.get(currentPlayer)) {
                                    if (cardIsUsable(currentCard, discard.get(0))) {
                                        break check;
                                    }
                                }
                                tell(sc[currentPlayer], "SkipForNothing");
                                currentPlayer = nextPlayer(currentPlayer, isOut, rvs);
                                updateTurn(sc, currentPlayer, isOut);
                                continue game;
                            } while (false);
                        }
                        int card = waitForInt(sc[currentPlayer], deck.isEmpty() ? "EmptyDeck" : "WaitForAction", options) - 1;
                        if (card == Integer.MIN_VALUE + 1) {
                            isOut[currentPlayer] = true;
                            playersOut++;
                            scores[currentPlayer] = -scores[currentPlayer] - 1;
                            playersScored++;
                            for (UnoCard item : hands.get(currentPlayer))
                                deck.add(deck.size(), item);
                            hands.get(currentPlayer).clear();
                            currentPlayer = nextPlayer(currentPlayer, isOut, rvs);
                            updateTurn(sc, currentPlayer, isOut);
                            continue;
                        }
                        if (card == hands.get(currentPlayer).size()) {
                            if (!deck.isEmpty()) {
                                if (cardIsUsable(deck.get(0), discard.get(0))) {
                                    checkInput:
                                    while (true) {
                                        int input = waitForInt(sc[currentPlayer], "DrawCard",
                                                new String[] { "CallUno", deck.get(0).toString(), "NoPlay" });
                                        switch (input) {
                                            case Integer.MIN_VALUE + 2:
                                                isOut[currentPlayer] = true;
                                                playersOut++;
                                                scores[currentPlayer] = -scores[currentPlayer] - 1;
                                                playersScored++;
                                                for (UnoCard item : hands.get(currentPlayer))
                                                    deck.add(deck.size(), item);
                                                hands.get(currentPlayer).clear();
                                                currentPlayer = nextPlayer(currentPlayer, isOut, rvs);
                                                updateTurn(sc, currentPlayer, isOut);
                                                continue game;
                                            case 0:
                                                discard.add(0, deck.get(0));
                                                deck.remove(0);
                                                calledUno = true;
                                                cardPlayed = true;
                                                break checkInput;
                                            case 1:
                                                discard.add(0, deck.get(0));
                                                deck.remove(0);
                                                calledUno = false;
                                                cardPlayed = true;
                                                break checkInput;
                                            case 2:
                                                hands.get(currentPlayer).add(0, deck.get(0));
                                                deck.remove(0);
                                                cardPlayed = false;
                                                break checkInput;
                                        }
                                    }
                                } else {
                                    hands.get(currentPlayer).add(deck.get(0));
                                    deck.remove(0);
                                    cardPlayed = false;
                                }
                            }
                        } else if (card == -2) {
                            continue;
                        } else if (card == -1) {
                            calledUno = true;
                            for (int i = 0; i < sc.length; i++)
                                if (!isOut[i] && i != currentPlayer)
                                    tell(sc[i], "CalledUno" + String.format("%1$02d", currentPlayer));
                                    // Player % has called uno.
                            continue;
                        } else if (cardIsUsable(hands.get(currentPlayer).get(card), discard.get(0))) {
                            discard.add(0, hands.get(currentPlayer).get(card));
                            hands.get(currentPlayer).remove(card);
                            cardPlayed = true;
                        } else {
                            continue;
                        }
                        if (cardPlayed) updateCard(sc);
                        if (!calledUno & hands.get(currentPlayer).size() == 1) {
                            for (int i = 0; i < 2; i++) {
                                hands.get(currentPlayer).add(deck.get(0));
                                deck.remove(0);
                            }
                            tell(sc[currentPlayer], "UnoWarning");
                            // You failed to call uno, so you must draw two cards.
                        }
                        if (discard.get(0).value == CardValue.DrawTwo & cardPlayed) {
                            previousPlayer = currentPlayer;
                            currentPlayer = nextPlayer(currentPlayer, isOut, rvs);
                            tell(sc[currentPlayer], "WarningDrawTwo");
                            // A draw two card has been used on you, skipping your turn.
                            for (int i = 0; i < 2; i++) {
                                if (!deck.isEmpty()) {
                                    hands.get(currentPlayer).add(deck.get(0));
                                    deck.remove(0);
                                }
                            }
                            playerSkipped = true;
                        }
                        if (discard.get(0).value == CardValue.DrawFour & cardPlayed) {
                            drawFour = true;
                            for (UnoCard currentCard : hands.get(currentPlayer)) {
                                if (currentCard.colour == discard.get(1).colour) {
                                    matchColour = true;
                                    break;
                                }
                            }
                            if (!matchColour) {
                                for (int i = 0; i < 4; i++) {
                                    if (!deck.isEmpty()) {
                                        hands.get(nextPlayer(currentPlayer, isOut, rvs)).add(deck.get(0));
                                        deck.remove(0);
                                    }
                                }
                            }
                        }
                        if (hands.get(currentPlayer).isEmpty()) {
                            isOut[currentPlayer] = true;
                            playersOut++;
                            tell(sc[currentPlayer], "WentOut");
                            // Congratulations! You went out!
                            for (ArrayList<UnoCard> hand : hands) {
                                for (UnoCard item : hand) {
                                    for (int i = 1; i <= 9; i++)
                                        if (item.value == CardValue.values()[i]) scores[currentPlayer] += i;
                                    if (item.value == CardValue.Reverse || item.value == CardValue.Skip || item.value == CardValue.DrawTwo)
                                        scores[currentPlayer] += 20;
                                    if (item.value == CardValue.WildCard || item.value == CardValue.DrawFour)
                                        scores[currentPlayer] += 50;
                                }
                            }
                            if (scores[currentPlayer] >= score) playersScored++;
                            updateScores(sc, scores);
                        }
                        if (discard.get(0).value == CardValue.Reverse & cardPlayed) {
                            tell(sc[nextPlayer(currentPlayer, isOut, rvs)], "WarningReverse");
                            // A reverse card has been used on you, avoiding your turn.
                            rvs = !rvs;
                            if (players - playersOut == 2) {
                                previousPlayer = currentPlayer;
                                currentPlayer = nextPlayer(currentPlayer, isOut, rvs);
                                playerSkipped = true;
                            }
                        }
                        if (discard.get(0).value == CardValue.Skip & cardPlayed) {
                            previousPlayer = currentPlayer;
                            currentPlayer = nextPlayer(currentPlayer, isOut, rvs);
                            tell(sc[currentPlayer], "WarningSkip");
                            // Your turn has been skipped.
                            playerSkipped = true;
                        }
                        if (discard.get(0).colour == CardColour.Wild) {
                            if (!discard.get(0).setWildColour(currentPlayer, sc[currentPlayer])) {
                                isOut[currentPlayer] = true;
                                playersOut++;
                                scores[currentPlayer] = -scores[currentPlayer] - 1;
                                playersScored++;
                                for (UnoCard item : hands.get(currentPlayer))
                                    deck.add(deck.size(), item);
                                hands.get(currentPlayer).clear();
                                currentPlayer = nextPlayer(currentPlayer, isOut, rvs);
                                updateTurn(sc, currentPlayer, isOut);
                                continue;
                            }
                            updateCard(sc);
                        }
                        if (!playerSkipped) {
                            previousPlayer = currentPlayer;
                        }
                        currentPlayer = nextPlayer(currentPlayer, isOut, rvs);
                        calledUno = false;
                        playerSkipped = false;
                    } else {
                        if (isOut[previousPlayer] || deck.isEmpty()) {
                            tell(sc[currentPlayer], "WarningDrawFourSkip");
                            currentPlayer = nextPlayer(currentPlayer, isOut, rvs);
                            continue;
                        }
                        int input = waitForInt(sc[currentPlayer], "WarningDrawFourAction", new String[] {
                            "DrawFour", "Illegal" });
                        // A draw four card has been used on you.
                        boolean illegal = input == 1;
                        if (input == Integer.MIN_VALUE + 2) {
                            isOut[currentPlayer] = true;
                            playersOut++;
                            scores[currentPlayer] = -scores[currentPlayer] - 1;
                            playersScored++;
                            for (UnoCard card : hands.get(currentPlayer))
                                deck.add(deck.size(), card);
                            hands.get(currentPlayer).clear();
                            currentPlayer = nextPlayer(currentPlayer, isOut, rvs);
                            updateTurn(sc, currentPlayer, isOut);
                            continue;
                        }
                        if (illegal & matchColour)
                            for (int i = 0; i < 4; i++) {
                                if (!deck.isEmpty()) {
                                    hands.get(previousPlayer).add(deck.get(0));
                                    deck.remove(0);
                                }
                            }
                        if (illegal & !matchColour) {
                            for (int i = 0; i < 2; i++) {
                                if (!deck.isEmpty()) {
                                    hands.get(currentPlayer).add(deck.get(0));
                                    deck.remove(0);
                                }
                            }
                            currentPlayer = nextPlayer(currentPlayer, isOut, rvs);
                        }
                        if (!illegal) {
                            if (matchColour)
                                for (int i = 0; i < 4; i++) {
                                    if (!deck.isEmpty()) {
                                        hands.get(currentPlayer).add(deck.get(0));
                                        deck.remove(0);
                                    }
                                }
                            currentPlayer = nextPlayer(currentPlayer, isOut, rvs);
                        }
                        drawFour = false;
                        matchColour = false;
                    }
                    updateTurn(sc, currentPlayer, isOut);
                }
                playersOut = 0;
                for (int i = 0; i < players; i++) {
                    isOut[i] = scores[i] < 0 || scores[i] >= score;
                    if (isOut[i]) playersOut++;
                }
                deck.clear();
                discard.clear();
                hands.clear();
            } while (playersScored < gameRace);
        }
    }

    @SuppressWarnings("UnnecessaryContinue")
    private void main() {
        // Start server
        try (InputStream in = new FileInputStream("GAME.CFG")) {
            Properties p = new Properties();
            p.load(in);
            try {
                maxPlayers = Integer.parseInt(p.getProperty("maxplayers", "10"));
                if (maxPlayers < 1) maxPlayers = 1;
                if (maxPlayers > 15) maxPlayers = 15;
            } catch (NumberFormatException exc) {
                maxPlayers = 10;
            }
            try {
                port = Integer.parseUnsignedInt(p.getProperty("port", "19283"));
                if (port > 65535) port = 19283;
            } catch (NumberFormatException exc) {
                port = 19283;
            }
            try {
                score = Integer.parseUnsignedInt(p.getProperty("score", "500"));
            } catch (NumberFormatException exc) {
                score = 500;
            }
            try {
                roundRace = Integer.parseInt(p.getProperty("roundrace", "1"));
                if (roundRace < 1 - maxPlayers) roundRace = 1 - maxPlayers;
                if (roundRace > maxPlayers) roundRace = maxPlayers;
            } catch (NumberFormatException exc) {
                roundRace = 1;
            }
            try {
                gameRace = Integer.parseInt(p.getProperty("gamerace", "1"));
                if (gameRace < 1 - maxPlayers) gameRace = 1 - maxPlayers;
                if (gameRace > maxPlayers) gameRace = maxPlayers;
            } catch (NumberFormatException exc) {
                gameRace = 1;
            }
            desc = p.getProperty("desc", "");
        } catch (IOException exc) {
            System.err.println("Failed to load game.cfg. Using default configuration (10 players on port 19283, official rules).");
        }
        sc = new ServerConnection[maxPlayers];
        names = new String[maxPlayers];
        try {
            ss2 = new ServerSocket(port); // can also use static final PORT_NUM , when defined
        } catch (IOException e) {
            System.err.println("The server has failed to start. It seems there is already a server running here.");
            System.exit(1);
        }
        jLabel1.setText("The game will automatically start once " + maxPlayers + " players have joined.");
        jLabel2.setText("0/" + maxPlayers + " players have now joined.");
        if (!isVisible()) {
            System.out.println("The game will automatically start once " + maxPlayers + " players have joined.");
            System.out.println("0/" + maxPlayers + " players have now joined.");
            System.out.println("Press enter to cut off.");
        }
        new ServerConnectThread().start();
        while (!gameReady) {
            if (!isVisible())
                try {
                    if (System.in.read() != -1) break;
                } catch (IOException ex) {
                    Logger.getLogger(UNO_Server.class.getName()).log(Level.SEVERE, null, ex);
                }
            System.out.print(""); // do, quote-on-quote, "something"
        }
        new ServerGameThread().start();
    }

    private boolean cardIsUsable(UnoCard cardToUse, UnoCard discardTop) {
        if (cardToUse.colour == CardColour.Wild) return true;
        if (discardTop.colour == CardColour.Wild) return true;
        if (cardToUse.colour == discardTop.colour) return true;
        return cardToUse.value == discardTop.value;
    }
    
    private int nextPlayer(int currentPlayer, boolean[] isOut, boolean rvs) {
        boolean allOut = true;
        for (boolean out : isOut) {
            allOut &= out;
        }
        if (allOut) return -1;
        int nextPlayer = currentPlayer;
        do {
            if (rvs) {
                nextPlayer--;
                if (nextPlayer == -1) nextPlayer = isOut.length - 1;
            } else {
                nextPlayer++;
                nextPlayer %= isOut.length;
            }
        } while (isOut[nextPlayer]);
        return nextPlayer;
    }
    
    private int waitForInt(ServerConnection sc, String reason, String[] options) {
        try {
            String print;
            print = reason + "/";
            for (int i = 0; i < options.length; i++) {
                print += options[i];
                if (i != options.length - 1) print += "-";
            }
            sc.os.println(print);
            sc.os.flush();
            String input;
            while ((input = sc.is.readLine()) == null) {}
            return Integer.parseInt(input);
        } catch (IOException exc) {
            System.err.println("It appears as though " + sc + " has closed the game applet. "
                    + "They will now be removed from the game.");
            return Integer.MIN_VALUE + 2;
        }
    }
    
    private void tell(ServerConnection sc, String msg) {
        sc.os.println("MSG:" + msg);
        sc.os.flush();
    }
    
    private void desc(ServerConnection sc) {
        sc.os.println("MSG:DESC:" + desc);
        sc.os.flush();
    }
    
    private void updateTurn(ServerConnection[] sc, int currentPlayer, boolean[] dontUpdate) {
        for (int i = 0; i < sc.length; i++)
            if (i != currentPlayer && !dontUpdate[i])
                tell(sc[i], "NextTurn" + String.format("%1$02d", currentPlayer));
    }
    
    private void updateCard(ServerConnection[] sc) {
        for (ServerConnection connection : sc) {
            connection.os.println("CARD:" + discard.get(0).toString());
            connection.os.flush();
        }
    }
    
    private void updateScores(ServerConnection[] sc, int[] scores) {
        String print = "SCORE:";
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] >= 0) print += String.format("%1$08d", scores[i]);
            else print += String.format("%1$08d", -scores[i] - 1);
            if (i != scores.length - 1) print += "-";
        }
        for (ServerConnection connection : sc) {
            connection.os.println(print);
            connection.os.flush();
        }
    }
    
    private class UnoCard {
        CardValue value;
        CardColour colour;

        UnoCard(CardValue value, CardColour colour) {
            this.value = value;
            this.colour = colour;
        }

        UnoCard(int value, CardColour colour) {
            this.value = CardValue.values()[value];
            this.colour = colour;
        }

        @Override
        public String toString() {
            if (colour == CardColour.Wild) return value.toString();
            return colour + " " + value;
        }

        boolean setWildColour(int currentPlayer, ServerConnection sc) {
            checkInput:
            while (true) {
                int newColour = waitForInt(sc, "WildColour", new String[] { "Red WildCard", "Green WildCard",
                    "Yellow WildCard", "Blue WildCard" });
                // Please select a colour.
                switch (newColour) {
                    case Integer.MIN_VALUE + 2:
                        return false;
                    case 0:
                        colour = CardColour.Red;
                        return true;
                    case 1:
                        colour = CardColour.Green;
                        return true;
                    case 2:
                        colour = CardColour.Yellow;
                        return true;
                    case 3:
                        colour = CardColour.Blue;
                        return true;
                }
            }
        }
    }
    
    private class ServerConnection {
        
        String line = null;
        BufferedReader is = null;
        PrintWriter os = null;
        Socket s = null;

        ServerConnection(Socket s) {
            this.s = s;
            try {
                is = new BufferedReader(new InputStreamReader(s.getInputStream()));
                os = new PrintWriter(s.getOutputStream());
            } catch (IOException e) {
                System.err.println("An I/O error occurred while trying to add a player.");
            }
        }
    }

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public UNO_Server(boolean b) {
        initComponents();
        if (b) {
            setVisible(true);
        }
        main();
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT modify this code. The
     * content of this method is always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jButton1 = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jButton2 = new javax.swing.JButton();

        jButton1.setText("jButton1");

        setTitle("UNO Server");

        jLabel1.setText("The game will automatically start once 15 players have joined.");

        jLabel2.setText("15/15 players have now joined.");

        jButton2.setText("Cut Off");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jButton2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        if (players >= 1) {
            gameReady = true;
        }
    }//GEN-LAST:event_jButton2ActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
            /* Create and display the form */
            new UNO_Server(args.length >= 1 && args[0].equalsIgnoreCase("-cli"));
    }
    
    boolean ready = false;

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    // End of variables declaration//GEN-END:variables
}

enum CardValue {
    Zero, One, Two, Three, Four, Five, Six, Seven, Eight, Nine, Skip, Reverse, DrawTwo, WildCard, DrawFour
}

enum CardColour {
    Red, Yellow, Green, Blue, Wild
}
