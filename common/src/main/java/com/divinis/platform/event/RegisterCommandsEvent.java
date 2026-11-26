package com.divinis.platform.event;

import com.divinis.platform.bus.Event;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class RegisterCommandsEvent extends Event {
    private final CommandDispatcher<CommandSourceStack> dispatcher;
    private final CommandBuildContext context;
    private final Commands.CommandSelection selection;

    public RegisterCommandsEvent(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context,
                                 Commands.CommandSelection selection) {
        this.dispatcher = dispatcher;
        this.context = context;
        this.selection = selection;
    }

    public CommandDispatcher<CommandSourceStack> getDispatcher() { return dispatcher; }
    public CommandBuildContext getBuildContext() { return context; }
    public Commands.CommandSelection getCommandSelection() { return selection; }
}
