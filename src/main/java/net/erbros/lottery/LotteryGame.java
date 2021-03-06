package net.erbros.lottery;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.regex.Matcher;


public class LotteryGame {

    private final Lottery plugin;
    private final LotteryConfig lConfig;

    public LotteryGame(Lottery plugin) {
        this.plugin = plugin;
        lConfig = plugin.getLotteryConfig();
    }

    public boolean addPlayer(Player player, int numberOfTickets) {

        // Do the ticket cost money or item?
        if (lConfig.useEconomy()) {
            // Do the player have money?
            // First checking if the player got an account, if not let's create
            // it.
            if (!plugin.getEcon().hasAccount(player)) {
                plugin.getEcon().createPlayerAccount(player);
            }

            // And lets withdraw some money
            if (plugin.getEcon().has(player, lConfig.getCost() * numberOfTickets)) {
                plugin.getEcon().withdrawPlayer(player, lConfig.getCost() * numberOfTickets);
            } else {
                return false;
            }
            lConfig.debugMsg("taking " + (lConfig.getCost() * numberOfTickets) + "from account");
        } else {
            // Do the user have the item
            if (player.getInventory().contains(lConfig.getMaterial(), (int) lConfig.getCost() * numberOfTickets)) {
                // Remove items.
                player.getInventory().removeItem(
                        new ItemStack(lConfig.getMaterial(), (int) lConfig.getCost() * numberOfTickets));
            } else {
                return false;
            }


        }
        // If the user paid, continue. Else we would already have sent return
        // false
        try {
            BufferedWriter out = new BufferedWriter(
                    new FileWriter(plugin.getDataFolder() + File.separator + "lotteryPlayers.txt", true));
            for (int i = 0; i < numberOfTickets; i++) {
                out.write(player.getName());
                out.newLine();
            }
            out.close();

        } catch (IOException ignored) {
        }

        return true;
    }

    public Integer playerInList(Player player) {
        return playerInList(player.getName());
    }

    public Integer playerInList(String player) {
        int numberOfTickets = 0;
        try {
            BufferedReader in = new BufferedReader(
                    new FileReader(plugin.getDataFolder() + File.separator + "lotteryPlayers.txt"));

            String str;
            while ((str = in.readLine()) != null) {

                if (str.equalsIgnoreCase(player)) {
                    numberOfTickets++;
                }
            }
            in.close();
        } catch (IOException ignored) {
        }

        return numberOfTickets;
    }

    public ArrayList<String> playersInFile(String file) {
        ArrayList<String> players = new ArrayList<>();
        try {
            BufferedReader in = new BufferedReader(
                    new FileReader(plugin.getDataFolder() + File.separator + file));
            String str;
            while ((str = in.readLine()) != null) {
                // add players to array.
                players.add(str);
            }
            in.close();
        } catch (IOException ignored) {
        }
        return players;
    }

    public double winningAmount() {
        double amount;
        ArrayList<String> players = playersInFile("lotteryPlayers.txt");
        amount = players.size() * Utils.formatAmount(lConfig.getCost(), lConfig.useEconomy());
        lConfig.debugMsg("playerno: " + players.size() + " amount: " + amount);
        // Set the net payout as configured in the config.
        if (lConfig.getNetPayout() > 0) {
            amount = amount * lConfig.getNetPayout() / 100;
        }
        // Add extra money added by admins and mods?
        amount += lConfig.getExtraInPot();
        // Any money in jackpot?

        lConfig.debugMsg("using config store: " + lConfig.getJackpot());
        amount += lConfig.getJackpot();

        // format it once again.
        amount = Utils.formatAmount(amount, lConfig.useEconomy());

        return amount;
    }

    public double taxAmount() {
        double amount = 0;

        // we only have tax is the net payout is between 0 and 100.
        if (lConfig.getNetPayout() >= 100 || lConfig.getNetPayout() <= 0 || !lConfig.useEconomy()) {
            return amount;
        }

        ArrayList<String> players = playersInFile("lotteryPlayers.txt");
        amount = players.size() * Utils.formatAmount(lConfig.getCost(), lConfig.useEconomy());

        // calculate the tax.
        amount = amount * (1 - (lConfig.getNetPayout() / 100));

        // format it once again.
        amount = Utils.formatAmount(amount, lConfig.useEconomy());

        return amount;
    }

    public int ticketsSold() {
        int sold;
        ArrayList<String> players = playersInFile("lotteryPlayers.txt");
        sold = players.size();
        return sold;
    }

    public void removeFromClaimList(Player player) {
        // Do the player have something to claim?
        ArrayList<String> otherPlayersClaims = new ArrayList<>();
        ArrayList<String> claimArray = new ArrayList<>();
        try {
            BufferedReader in = new BufferedReader(
                    new FileReader(plugin.getDataFolder() + File.separator + "lotteryClaim.txt"));
            String str;
            while ((str = in.readLine()) != null) {
                String[] split = str.split(":");
                if (split[0].equals(player.getName())) {
                    // Adding this to player claim.
                    claimArray.add(str);
                } else {
                    otherPlayersClaims.add(str);
                }
            }
            in.close();
        } catch (IOException ignored) {
        }

        // Did the user have any claims?
        if (claimArray.isEmpty()) {
            sendMessage(player, "ErrorClaim");
        }
        // Do a bit payout.
        for (String aClaimArray : claimArray) {
            String[] split = aClaimArray.split(":");
            int claimAmount = Integer.parseInt(split[1]);
            Material claimMaterial = Material.valueOf(split[2]);
            player.getInventory().addItem(new ItemStack(claimMaterial, claimAmount));
            sendMessage(player, "PlayerClaim", Utils.formatMaterialName(claimMaterial));
        }

        // Add the other players claims to the file again.
        try {
            BufferedWriter out = new BufferedWriter(
                    new FileWriter(plugin.getDataFolder() + File.separator + "lotteryClaim.txt"));
            for (String otherPlayersClaim : otherPlayersClaims) {
                out.write(otherPlayersClaim);
                out.newLine();
            }

            out.close();

        } catch (IOException ignored) {
        }
    }

    public void addToClaimList(String playerName, int winningAmount, Material winningMaterial) {
        // Then first add new winner, and after that the old winners.
        try {
            BufferedWriter out = new BufferedWriter(
                    new FileWriter(plugin.getDataFolder() + File.separator + "lotteryClaim.txt", true));
            out.write(playerName + ":" + winningAmount + ":" + winningMaterial);
            out.newLine();
            out.close();
        } catch (IOException ignored) {
        }
    }

    public void addToWinnerList(String playerName, Double winningAmount, int winningCurrency, Material winningMaterial) {
        // This list should be 10 players long.
        ArrayList<String> winnerArray = new ArrayList<>();
        try {
            BufferedReader in = new BufferedReader(
                    new FileReader(plugin.getDataFolder() + File.separator + "lotteryWinners.txt"));
            String str;
            while ((str = in.readLine()) != null) {
                winnerArray.add(str);
            }
            in.close();
        } catch (IOException ignored) {
        }
        // Then first add new winner, and after that the old winners.
        try {
            BufferedWriter out = new BufferedWriter(
                    new FileWriter(plugin.getDataFolder() + File.separator + "lotteryWinners.txt"));
            out.write(playerName + ":" + winningAmount + ":" + (winningMaterial != null ? winningMaterial : winningCurrency));
            out.newLine();
            // How long is the array? We just want the top 9. Removing index 9
            // since its starting at 0.
            if (!winnerArray.isEmpty()) {
                if (winnerArray.size() > 9) {
                    winnerArray.remove(9);
                }
                // Go trough list and output lines.
                for (String aWinnerArray : winnerArray) {
                    out.write(aWinnerArray);
                    out.newLine();
                }
            }
            out.close();

        } catch (IOException ignored) {
        }
    }

    public long timeUntil() {
        long nextDraw = lConfig.getNextexec();
        return ((nextDraw - System.currentTimeMillis()) / 1000);
    }

    public String timeUntil(boolean mini) {
        long timeLeft = timeUntil();
        if (timeLeft < 0) {
            plugin.startTimerSchedule(true);
            return mini ? "Soon" : "Draw will occur soon!";
        }

        return Utils.timeUntil(timeLeft, mini, lConfig);
    }

    public boolean getWinner() {
        ArrayList<String> players = playersInFile("lotteryPlayers.txt");

        if (players.isEmpty()) {
            broadcastMessage("NoWinnerTickets");
            return false;
        } else {
            // Find rand. Do minus 1 since its a zero based array.
            int rand;

            // is max number of tickets 0? If not, include empty tickets not sold.
            if (lConfig.getTicketsAvailable() > 0 && ticketsSold() < lConfig.getTicketsAvailable()) {
                rand = new SecureRandom().nextInt(lConfig.getTicketsAvailable());
                // If it wasn't a player winning, then do some stuff. If it was a player, just continue below.
                if (rand > players.size() - 1) {
                    // No winner this time, pot goes on to jackpot!
                    double jackpot = winningAmount();

                    lConfig.setJackpot(jackpot);

                    addToWinnerList("Jackpot", jackpot, 0, lConfig.getMaterial());
                    lConfig.setLastwinner("Jackpot");
                    lConfig.setLastwinneramount(jackpot);
                    broadcastMessage("NoWinnerRollover", Utils.getCostMessage(jackpot, lConfig));
                    clearAfterGettingWinner();
                    return true;
                }
            } else {
                // Else just continue
                rand = new SecureRandom().nextInt(players.size());
            }


            lConfig.debugMsg("Rand: " + rand);
            double amount = winningAmount();
            OfflinePlayer player = Bukkit.getOfflinePlayer(players.get(rand));
            int ticketsBought = playerInList(players.get(rand));
            if (lConfig.useEconomy()) {
                if (!plugin.getEcon().hasAccount(player)) {
                    plugin.getEcon().createPlayerAccount(player);
                }
                plugin.getEcon().depositPlayer(player, amount);

                // Announce the winner:
                broadcastMessage("WinnerCongrat", players.get(rand), Utils.getCostMessage(amount, lConfig), ticketsBought, lConfig.getPlural("ticket", ticketsBought));
                addToWinnerList(players.get(rand), amount, 0, null);

                double taxAmount = taxAmount();
                if (taxAmount() > 0 && lConfig.getTaxTarget().length() > 0) {
                    OfflinePlayer target = Bukkit.getOfflinePlayer(lConfig.getTaxTarget());
                    if (!plugin.getEcon().hasAccount(target)) {
                        plugin.getEcon().createPlayerAccount(target);
                    }
                    plugin.getEcon().depositPlayer(target, taxAmount);
                }
            } else {
                // let's throw it to an int.
                int matAmount = (int) Utils.formatAmount(amount, lConfig.useEconomy());
                amount = matAmount;
                broadcastMessage("WinnerCongrat", players.get(rand), Utils.getCostMessage(amount, lConfig), ticketsBought, lConfig.getPlural("ticket", ticketsBought));
                broadcastMessage("WinnerCongratClaim");
                addToWinnerList(players.get(rand), amount, 0, lConfig.getMaterial());

                addToClaimList(players.get(rand), matAmount, lConfig.getMaterial());
            }
            broadcastMessage(
                    "WinnerSummary", Utils.realPlayersFromList(players).size(), lConfig.getPlural(
                            "player", Utils.realPlayersFromList(players).size()), players.size(), lConfig.getPlural("ticket", players.size()));

            // Add last winner to config.
            lConfig.setLastwinner(players.get(rand));
            lConfig.setLastwinneramount(amount);

            lConfig.setJackpot(0);


            clearAfterGettingWinner();
        }
        return true;
    }

    public void clearAfterGettingWinner() {

        // extra money in pot added by admins and mods?
        // Should this be removed?
        if (lConfig.clearExtraInPot()) {
            lConfig.setExtraInPot(0);
        }
        // Clear file.
        try {
            BufferedWriter out = new BufferedWriter(
                    new FileWriter(plugin.getDataFolder() + File.separator + "lotteryPlayers.txt", false));
            out.write("");
            out.close();

        } catch (IOException ignored) {
        }
    }

    public void broadcastMessage(String topic, Object... args) {
        try {
            for (String message : lConfig.getMessage(topic)) {
                String outMessage = formatCustomMessageLive(message, args);
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (player.hasMetadata("LotteryOptOut") && player.getMetadata("LotteryOptOut").get(0).asBoolean()) {
                        continue;
                    }
                    outMessage = outMessage.replaceAll("%player%", player.getDisplayName());
                    player.sendMessage(outMessage);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Invalid Translation Key: " + topic, e);
        }
    }

    public void sendMessage(CommandSender player, String topic, Object... args) {
        try {
            for (String message : lConfig.getMessage(topic)) {
                String outMessage = formatCustomMessageLive(message, args);
                if (player instanceof Player) {
                    outMessage = outMessage.replaceAll("%player%", Matcher.quoteReplacement(((Player) player).getDisplayName()));
                }
                player.sendMessage(outMessage);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Invalid Translation Key: " + topic, e);
        }
    }

    public String formatCustomMessageLive(String message, Object... args) throws Exception {
        //Lets give timeLeft back if user provie %draw%
        String outMessage = message.replaceAll("%draw%", Matcher.quoteReplacement(timeUntil(true)));

        //Lets give timeLeft with full words back if user provie %drawLong%
        outMessage = outMessage.replaceAll("%drawLong%", Matcher.quoteReplacement(timeUntil(false)));

        // %cost% = cost
        outMessage = outMessage.replaceAll("%cost%", Matcher.quoteReplacement(Utils.getCostMessage(lConfig.getCost(), lConfig)));

        // %pot%
        outMessage = outMessage.replaceAll("%pot%", Matcher.quoteReplacement(Utils.getCostMessage(winningAmount(), lConfig)));

        // %prefix%
        outMessage = outMessage.replaceAll("%prefix%", Matcher.quoteReplacement(lConfig.getMessage("prefix").get(0)));

        for (int i = 0; i < args.length; i++) {
            outMessage = outMessage.replaceAll("%" + i + "%", Matcher.quoteReplacement(args[i].toString()));
        }

        // Lets get some colors on this, shall we?
        outMessage = outMessage.replaceAll("(&([a-fk-or0-9]))", "\u00A7$2");
        return outMessage;
    }


}
