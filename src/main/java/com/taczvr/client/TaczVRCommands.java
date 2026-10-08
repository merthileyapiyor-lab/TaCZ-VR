package com.taczvr.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.taczvr.TaczVRConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Locale;

/**
 * Client commands to tune how the gun sits in the hand without leaving VR:
 * /taczvr scale 0.3, /taczvr grip 0 -0.01 0.03, /taczvr pitch 10, /taczvr aimline, /taczvr info, /taczvr reset
 */
public final class TaczVRCommands {
    private TaczVRCommands() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        TaczVRConfig.Client cfg = TaczVRConfig.CLIENT;
        dispatcher.register(Commands.literal("taczvr")
                .then(Commands.literal("scale")
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.02, 2.0))
                                .executes(ctx -> {
                                    set(cfg.gunScale, DoubleArgumentType.getDouble(ctx, "value"));
                                    return info(ctx);
                                })))
                .then(Commands.literal("grip")
                        .then(Commands.argument("x", DoubleArgumentType.doubleArg(-0.5, 0.5))
                                .then(Commands.argument("y", DoubleArgumentType.doubleArg(-0.5, 0.5))
                                        .then(Commands.argument("z", DoubleArgumentType.doubleArg(-0.5, 0.5))
                                                .executes(ctx -> {
                                                    set(cfg.gripOffsetX, DoubleArgumentType.getDouble(ctx, "x"));
                                                    set(cfg.gripOffsetY, DoubleArgumentType.getDouble(ctx, "y"));
                                                    set(cfg.gripOffsetZ, DoubleArgumentType.getDouble(ctx, "z"));
                                                    return info(ctx);
                                                })))))
                .then(Commands.literal("pitch")
                        .then(Commands.argument("degrees", DoubleArgumentType.doubleArg(-90.0, 90.0))
                                .executes(ctx -> {
                                    set(cfg.gripPitch, DoubleArgumentType.getDouble(ctx, "degrees"));
                                    return info(ctx);
                                })))
                .then(Commands.literal("aimline")
                        .executes(ctx -> {
                            cfg.showAimLine.set(!cfg.showAimLine.get());
                            cfg.showAimLine.save();
                            return info(ctx);
                        }))
                .then(Commands.literal("reset")
                        .executes(ctx -> {
                            reset(cfg.gunScale);
                            reset(cfg.gripOffsetX);
                            reset(cfg.gripOffsetY);
                            reset(cfg.gripOffsetZ);
                            reset(cfg.gripPitch);
                            return info(ctx);
                        }))
                .then(Commands.literal("info")
                        .executes(TaczVRCommands::info))
                .executes(TaczVRCommands::info));
    }

    private static void set(ForgeConfigSpec.DoubleValue value, double v) {
        value.set(v);
        value.save();
    }

    private static void reset(ForgeConfigSpec.DoubleValue value) {
        set(value, value.getDefault());
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        TaczVRConfig.Client cfg = TaczVRConfig.CLIENT;
        String text = String.format(Locale.ROOT,
                "TACZ VR: scale=%.3f grip=(%.3f, %.3f, %.3f) pitch=%.1f aimline=%s",
                cfg.gunScale.get(), cfg.gripOffsetX.get(), cfg.gripOffsetY.get(), cfg.gripOffsetZ.get(),
                cfg.gripPitch.get(), cfg.showAimLine.get() ? "on" : "off");
        ctx.getSource().sendSuccess(Component.literal(text), false);
        return 1;
    }
}
