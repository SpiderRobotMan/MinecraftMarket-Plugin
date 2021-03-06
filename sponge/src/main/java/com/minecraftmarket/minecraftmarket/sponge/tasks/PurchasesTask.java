package com.minecraftmarket.minecraftmarket.sponge.tasks;

import com.minecraftmarket.minecraftmarket.common.api.MCMarketApi;
import com.minecraftmarket.minecraftmarket.common.api.models.Command;
import com.minecraftmarket.minecraftmarket.sponge.MCMarket;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.inventory.type.CarriedInventory;
import org.spongepowered.api.scheduler.Task;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class PurchasesTask implements Runnable {
    private final MCMarket plugin;
    private final List<MCMarketApi.CommandType> commandTypes = Arrays.asList(
            MCMarketApi.CommandType.EXPIRY,
            MCMarketApi.CommandType.CHARGEBACK,
            MCMarketApi.CommandType.REFUND,
            MCMarketApi.CommandType.INITIAL,
            MCMarketApi.CommandType.RENEWAL
    );

    public PurchasesTask(MCMarket plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        updatePurchases();
    }

    public void updatePurchases() {
        if (MCMarket.isAuthenticated()) {
            for (MCMarketApi.CommandType commandType : commandTypes) {
                for (Command command : MCMarket.getApi().getCommands(commandType, 1, 2)) {
                    runCommand(command);
                }
            }
        }
    }

    private void runCommand(Command command) {
        if (Sponge.isServerAvailable()) {
            Optional<Player> player = Sponge.getServer().getPlayer(command.getPlayer().getName());
            if (command.isRequiredOnline() && !player.isPresent()) {
                return;
            }

            if (command.getRequiredSlots() > 0 && player.isPresent()) {
                if (getEmptySlots(player.get().getInventory()) < command.getRequiredSlots()) {
                    return;
                }
            }

            if (MCMarket.getApi().setExecuted(command.getId())) {
                Sponge.getScheduler().createTaskBuilder().async().delayTicks(command.getDelay() > 0 ? 20 * command.getDelay() : 1).execute(() -> {
                    Sponge.getScheduler().createTaskBuilder().execute(() -> {
                        Optional<CommandSource> commandSource = Sponge.getServer().getConsole().getCommandSource();
                        commandSource.ifPresent(source -> Sponge.getGame().getCommandManager().process(source, command.getCommand()));
                    }).submit(plugin);

                    if (command.isRepeat()) {
                        long period = command.getRepeatPeriod() > 0 ? 20 * 60 * 60 * command.getRepeatPeriod() : 1;
                        Sponge.getScheduler().createTaskBuilder().intervalTicks(period).execute(new Consumer<Task>() {
                            int executed = 0;

                            @Override
                            public void accept(Task task) {
                                Optional<CommandSource> commandSource = Sponge.getServer().getConsole().getCommandSource();
                                commandSource.ifPresent(source -> Sponge.getGame().getCommandManager().process(source, command.getCommand()));
                                executed++;

                                if (executed >= command.getRepeatCycles()) {
                                    task.cancel();
                                }
                            }
                        }).submit(plugin);
                    }
                }).submit(plugin);
            }
        }
    }

    private int getEmptySlots(CarriedInventory inventory) {
        return Math.max(0, 36 - inventory.size());
    }
}