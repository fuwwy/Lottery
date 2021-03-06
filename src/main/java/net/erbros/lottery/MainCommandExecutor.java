package net.erbros.lottery;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;


public class MainCommandExecutor implements CommandExecutor {

    private final Lottery plugin;
    private final LotteryConfig lConfig;
    private final LotteryGame lGame;

    public MainCommandExecutor(Lottery plugin) {
        this.plugin = plugin;
        lConfig = plugin.getLotteryConfig();
        lGame = plugin.getLotteryGame();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Lets check if we have found a plugin for money.
        if (lConfig.useEconomy() && plugin.getEcon() == null) {
            lConfig.debugMsg("No money plugin found yet.");
            lGame.sendMessage(sender, "ErrorPlugin");
            return true;
        }

        // Can the player access the plugin?
        if (!sender.hasPermission("lottery.buy")) {
            lGame.sendMessage(sender, "ErrorAccess");
        }

        // If its just /lottery, and no args.
        if (args.length == 0) {
            commandNull(sender, args);
        } else if (args[0].equalsIgnoreCase("buy")) {
            commandBuy(sender, args);
        } else if (args[0].equalsIgnoreCase("claim")) {
            commandClaim(sender, args);
        } else if (args[0].equalsIgnoreCase("winners")) {
            commandWinners(sender, args);
        } else if (args[0].equalsIgnoreCase("messages")) {
            commandMessages(sender, args);
        } else if (args[0].equalsIgnoreCase("help")) {
            commandHelp(sender, args);
        } else if (args[0].equalsIgnoreCase("draw")) {
            if (sender.hasPermission("lottery.admin.draw")) {
                commandDraw(sender, args);
            } else {
                lGame.sendMessage(sender, "ErrorAccess");
            }
        } else if (args[0].equalsIgnoreCase("addtopot")) {
            if (sender.hasPermission("lottery.admin.addtopot")) {
                commandAddToPot(sender, args);
            } else {
                lGame.sendMessage(sender, "ErrorAccess");
            }
        } else if (args[0].equalsIgnoreCase("config")) {
            if (sender.hasPermission("lottery.admin.editconfig")) {
                commandConfig(sender, args);
            } else {
                lGame.sendMessage(sender, "ErrorAccess");
            }
        } else if (args[0].equalsIgnoreCase("settaxtarget")) {
            if (sender.hasPermission("lottery.admin.settaxtarget")) {
                commandSetTaxAcc(sender, args);
            } else {
                lGame.sendMessage(sender, "ErrorAccess");
            }
        } else {
            lGame.sendMessage(sender, "ErrorCommand");
        }

        return true;
    }

    public void commandNull(CommandSender sender, String[] args) {
        // Is this a console? If so, just tell that lottery is running and time until next draw.
        if (!(sender instanceof Player)) {
            sender.sendMessage("Hi Console - The Lottery plugin is running");
            lGame.sendMessage(sender, "DrawIn", lGame.timeUntil(false));
            return;
        }
        Player player = (Player) sender;

        // Check if we got any money/items in the pot.
        double amount = lGame.winningAmount();
        lConfig.debugMsg("pot current total: " + amount);
        // Send some messages:
        lGame.sendMessage(sender, "DrawIn", lGame.timeUntil(false));
        lGame.sendMessage(sender, "TicketCommand");
        lGame.sendMessage(sender, "PotAmount");
        if (lConfig.getMaxTicketsEachUser() > 1) {
            lGame.sendMessage(
                    player, "YourTickets", lGame.playerInList(player), lConfig.getPlural("ticket", lGame.playerInList(player)));
        }
        // Number of tickets available?
        if (lConfig.getTicketsAvailable() > 0) {
            lGame.sendMessage(
                    sender, "TicketRemaining", (lConfig.getTicketsAvailable() - lGame.ticketsSold()), lConfig.getPlural(
                            "ticket", lConfig.getTicketsAvailable() - lGame.ticketsSold()));
        }
        lGame.sendMessage(sender, "CommandHelp");

        // Does lastwinner exist and != null? Show.
        // Show different things if we are using iConomy over
        // material.
        if (lConfig.getLastwinner() != null) {
            lGame.sendMessage(sender, "LastWinner", lConfig.getLastwinner(), Utils.getCostMessage(lConfig.getLastwinneramount(), lConfig));
        }

        // if not iConomy, make players check for claims.
        if (!lConfig.useEconomy()) {
            lGame.sendMessage(sender, "CheckClaim");
        }
    }

    public void commandMessages(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            lGame.sendMessage(sender, "ErrorConsole3");
            return;
        }
        Player player = (Player) sender;

        if (player.hasMetadata("LotteryOptOut") && player.getMetadata("LotteryOptOut").get(0).asBoolean()) {
            player.setMetadata("LotteryOptOut", new FixedMetadataValue(plugin, false));
            lGame.sendMessage(sender, "MessagesEnabled");
        } else {
            player.setMetadata("LotteryOptOut", new FixedMetadataValue(plugin, true));
            lGame.sendMessage(sender, "MessagesDisabled");
        }
    }

    public void commandHelp(CommandSender sender, String[] args) {
        lGame.sendMessage(sender, "Help");
        // Are we dealing with admins?
        if (sender.hasPermission("lottery.admin.draw") || sender.hasPermission("lottery.admin.addtopot") || sender.hasPermission("lottery.admin.editconfig")) {
            lGame.sendMessage(sender, "HelpAdmin");
        }
    }

    public void commandBuy(CommandSender sender, String[] args) {
        // Is this a console? If so, just tell that lottery is running and time until next draw.
        if (!(sender instanceof Player)) {
            lGame.sendMessage(sender, "ErrorConsole");
            return;
        }
        Player player = (Player) sender;

        int buyTickets = 1;
        if (args.length > 1) {
            // How many tickets do the player want to buy?
            buyTickets = Utils.parseInt(args[1]);

            if (buyTickets < 1) {
                buyTickets = 1;
            }
        }

        int allowedTickets = lConfig.getMaxTicketsEachUser() - lGame.playerInList(player);

        if (buyTickets > allowedTickets && allowedTickets > 0) {
            buyTickets = allowedTickets;
        }

        // Have the admin entered a max number of tickets in the lottery?
        if (lConfig.getTicketsAvailable() > 0) {
            // If so, can this user buy the selected amount?
            if (lGame.ticketsSold() + buyTickets > lConfig.getTicketsAvailable()) {
                if (lGame.ticketsSold() >= lConfig.getTicketsAvailable()) {
                    lGame.sendMessage(sender, "ErrorNoAvailable");
                    return;
                } else {
                    buyTickets = lConfig.getTicketsAvailable() - lGame.ticketsSold();
                }
            }
        }

        if (lConfig.getMaxTicketsEachUser() > 0 && lGame.playerInList(
                player) + buyTickets > lConfig.getMaxTicketsEachUser()) {
            lGame.sendMessage(sender, "ErrorAtMax", lConfig.getMaxTicketsEachUser(), lConfig.getPlural("ticket", lConfig.getMaxTicketsEachUser()));
            return;
        }

        if (lGame.addPlayer(player, buyTickets)) {
            // You got your ticket.
            lGame.sendMessage(
                    sender, "BoughtTicket", buyTickets, lConfig.getPlural("ticket", buyTickets), Utils.getCostMessage(lConfig.getCost() * buyTickets, lConfig));

            // Can a user buy more than one ticket? How many
            // tickets have he bought now?
            if (lConfig.getMaxTicketsEachUser() > 1) {
                lGame.sendMessage(
                        sender, "BoughtTickets", lGame.playerInList(player), lConfig.getPlural("ticket", lGame.playerInList(player)));
            }
            if (lConfig.isBuyingExtendDeadline() && lGame.timeUntil() < lConfig.getBuyingExtendRemaining()) {
                long timeBonus = (long) (lConfig.getBuyingExtendBase() + (lConfig.getBuyingExtendMultiplier() * Math.sqrt(
                        buyTickets)));
                lConfig.setNextexec(lConfig.getNextexec() + (timeBonus * 1000));
            }
            if (lConfig.useBroadcastBuying()) {
                if (lGame.timeUntil() < lConfig.getBroadcastBuyingTime()) {
                    lGame.broadcastMessage(
                            "BoughtAnnounceDraw", player.getDisplayName(), buyTickets, lConfig.getPlural("ticket", buyTickets), lGame.timeUntil(true));
                } else {
                    lGame.broadcastMessage(
                            "BoughtAnnounce", player.getDisplayName(), buyTickets, lConfig.getPlural("ticket", buyTickets));
                }
            }

        } else {
            // Something went wrong.
            lGame.sendMessage(sender, "ErrorNotAfford");
        }

    }

    public void commandClaim(CommandSender sender, String[] args) {
        // Is this a console? If so, just tell that lottery is running and time until next draw.
        if (!(sender instanceof Player)) {
            lGame.sendMessage(sender, "ErrorConsole2");
            return;
        }

        lGame.removeFromClaimList((Player) sender);
    }

    public void commandDraw(CommandSender sender, String[] args) {
        // Start a timer that ends in 3 secs.
        lGame.sendMessage(sender, "DrawNow");
        plugin.startTimerSchedule(true);
    }

    public void commandWinners(CommandSender sender, String[] args) {
        // Get the winners.
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
        String[] split;
        String winListPrice;
        for (int i = 0; i < winnerArray.size(); i++) {
            split = winnerArray.get(i).split(":");
            if (split[2].equalsIgnoreCase("0")) {
                winListPrice = plugin.getEcon().format(Double.parseDouble(split[1]));
            } else {
                winListPrice = split[1] + " " + Utils.formatMaterialName(
                        Material.valueOf(split[2]));
            }
            sender.sendMessage((i + 1) + ". " + split[0] + " " + winListPrice);
        }
    }

    public void commandAddToPot(CommandSender sender, String[] args) {
        if (args.length < 2) {
            lGame.sendMessage(sender, "HelpPot");
            return;
        }

        double addToPot = Utils.parseDouble(args[1]);

        if (addToPot == 0) {
            lGame.sendMessage(sender, "ErrorNumber");
            return;
        }
        lConfig.addExtraInPot(addToPot);
        lGame.sendMessage(sender, "AddToPot", addToPot, lConfig.getExtraInPot());
    }

    public void commandConfig(CommandSender sender, String[] args) {
        if (args.length == 1) {
            lGame.sendMessage(sender, "HelpConfig");
            return;
        } else if (args.length > 2) {
            if (args[1].equalsIgnoreCase("cost")) {
                double newCoin = Utils.parseDouble(args[2]);
                if (newCoin <= 0) {
                    lGame.sendMessage(sender, "ErrorNumber");
                } else {
                    lGame.sendMessage(sender, "ConfigCost", newCoin);
                    lConfig.setCost(newCoin);
                }
            } else if (args[1].equalsIgnoreCase("hours")) {
                double newHours = Utils.parseDouble(args[2]);
                if (newHours <= 0) {
                    lGame.sendMessage(sender, "ErrorNumber");
                } else {
                    lGame.sendMessage(sender, "ConfigHours", newHours);
                    lConfig.setHours(newHours);
                }

            } else if (args[1].equalsIgnoreCase("maxTicketsEachUser") || args[1].equalsIgnoreCase("max")) {
                int newMaxTicketsEachUser = Utils.parseInt(args[2]);
                lGame.sendMessage(sender, "ConfigMax", newMaxTicketsEachUser);
                lConfig.setMaxTicketsEachUser(newMaxTicketsEachUser);
            }
        }
        // Lets just reload the config.
        lConfig.loadConfig();
        lGame.sendMessage(sender, "ConfigReload");
    }

    public void commandSetTaxAcc(CommandSender sender, String[] args) {
        if (args.length < 2) {
            lGame.sendMessage(sender, "HelpTaxTarget");
            return;
        }

        String acc = args[1];

        double hours = 0;
        if (args.length > 2) {
            try {
                hours = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                lGame.sendMessage(sender, "ErrorNumber");
            }
        }

        lConfig.setTaxTarget(acc);
        lConfig.setTaxTargetUntil((long) (System.currentTimeMillis() + (hours * 60 * 60 * 1000)));
        lGame.sendMessage(sender, "SetTaxTarget", acc, hours <= 0 ? "\u221E" : hours);
    }
}
