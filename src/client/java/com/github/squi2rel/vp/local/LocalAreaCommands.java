package com.github.squi2rel.vp.local;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Vector3f;

import java.util.Objects;
import java.util.stream.Collectors;

public class LocalAreaCommands {
    public static LiteralArgumentBuilder<FabricClientCommandSource> register() {
        return ClientCommandManager.literal("local")
                .then(areaCommands())
                .then(screenCommands());
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> areaCommands() {
        return ClientCommandManager.literal("area")
                .then(areaCreateCommand())
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                .executes(s -> removeArea(s.getSource(), s.getArgument("name", String.class)))))
                .then(ClientCommandManager.literal("list")
                        .executes(s -> listAreas(s.getSource())));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> areaCreateCommand() {
        RequiredArgumentBuilder<FabricClientCommandSource, Float> z2 = ClientCommandManager.argument("z2", FloatArgumentType.floatArg())
                .executes(s -> createArea(s.getSource(), s.getArgument("name", String.class), vec(s, "1"), vec(s, "2")));
        RequiredArgumentBuilder<FabricClientCommandSource, Float> y2 = ClientCommandManager.argument("y2", FloatArgumentType.floatArg()).then(z2);
        RequiredArgumentBuilder<FabricClientCommandSource, Float> x2 = ClientCommandManager.argument("x2", FloatArgumentType.floatArg()).then(y2);
        RequiredArgumentBuilder<FabricClientCommandSource, Float> z1 = ClientCommandManager.argument("z1", FloatArgumentType.floatArg()).then(x2);
        RequiredArgumentBuilder<FabricClientCommandSource, Float> y1 = ClientCommandManager.argument("y1", FloatArgumentType.floatArg()).then(z1);
        RequiredArgumentBuilder<FabricClientCommandSource, Float> x1 = ClientCommandManager.argument("x1", FloatArgumentType.floatArg()).then(y1);
        return ClientCommandManager.literal("create")
                .then(ClientCommandManager.argument("name", StringArgumentType.string()).then(x1));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> screenCommands() {
        return ClientCommandManager.literal("screen")
                .then(screenCreateCommand())
                .then(ClientCommandManager.literal("remove")
                        .then(ClientCommandManager.argument("area", StringArgumentType.string())
                                .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                        .executes(s -> removeScreen(s.getSource(), s.getArgument("area", String.class), s.getArgument("name", String.class))))))
                .then(ClientCommandManager.literal("list")
                        .then(ClientCommandManager.argument("area", StringArgumentType.string())
                                .executes(s -> listScreens(s.getSource(), s.getArgument("area", String.class)))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> screenCreateCommand() {
        RequiredArgumentBuilder<FabricClientCommandSource, Float> z4 = ClientCommandManager.argument("z4", FloatArgumentType.floatArg())
                .executes(s -> createScreen(s.getSource(), s.getArgument("area", String.class), s.getArgument("name", String.class), vec(s, "1"), vec(s, "2"), vec(s, "3"), vec(s, "4")));
        RequiredArgumentBuilder<FabricClientCommandSource, Float> y4 = ClientCommandManager.argument("y4", FloatArgumentType.floatArg()).then(z4);
        RequiredArgumentBuilder<FabricClientCommandSource, Float> x4 = ClientCommandManager.argument("x4", FloatArgumentType.floatArg()).then(y4);
        RequiredArgumentBuilder<FabricClientCommandSource, Float> z3 = ClientCommandManager.argument("z3", FloatArgumentType.floatArg()).then(x4);
        RequiredArgumentBuilder<FabricClientCommandSource, Float> y3 = ClientCommandManager.argument("y3", FloatArgumentType.floatArg()).then(z3);
        RequiredArgumentBuilder<FabricClientCommandSource, Float> x3 = ClientCommandManager.argument("x3", FloatArgumentType.floatArg()).then(y3);
        RequiredArgumentBuilder<FabricClientCommandSource, Float> z2 = ClientCommandManager.argument("z2", FloatArgumentType.floatArg()).then(x3);
        RequiredArgumentBuilder<FabricClientCommandSource, Float> y2 = ClientCommandManager.argument("y2", FloatArgumentType.floatArg()).then(z2);
        RequiredArgumentBuilder<FabricClientCommandSource, Float> x2 = ClientCommandManager.argument("x2", FloatArgumentType.floatArg()).then(y2);
        RequiredArgumentBuilder<FabricClientCommandSource, Float> z1 = ClientCommandManager.argument("z1", FloatArgumentType.floatArg()).then(x2);
        RequiredArgumentBuilder<FabricClientCommandSource, Float> y1 = ClientCommandManager.argument("y1", FloatArgumentType.floatArg()).then(z1);
        RequiredArgumentBuilder<FabricClientCommandSource, Float> x1 = ClientCommandManager.argument("x1", FloatArgumentType.floatArg()).then(y1);
        return ClientCommandManager.literal("create")
                .then(ClientCommandManager.argument("area", StringArgumentType.string())
                        .then(ClientCommandManager.argument("name", StringArgumentType.string()).then(x1)));
    }

    private static int createArea(FabricClientCommandSource source, String name, Vector3f p1, Vector3f p2) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            source.sendFeedback(Text.literal("当前未进入世界，无法创建本地区域").formatted(Formatting.RED));
            return 0;
        }
        if (LocalAreaManager.getArea(name) != null) {
            source.sendFeedback(Text.literal("已存在名为 " + name + " 的本地区域").formatted(Formatting.RED));
            return 0;
        }

        LocalAreaEntry area = new LocalAreaEntry();
        area.server = LocalAreaManager.getCurrentServerKey();
        area.dimension = client.world.getRegistryKey().getValue().toString();
        area.name = name;
        area.min.set(Math.min(p1.x, p2.x), Math.min(p1.y, p2.y), Math.min(p1.z, p2.z));
        area.max.set(Math.max(p1.x, p2.x), Math.max(p1.y, p2.y), Math.max(p1.z, p2.z));
        LocalAreaManager.addArea(area);
        LocalAreaManager.reloadArea(name);
        source.sendFeedback(Text.literal("已创建本地区域 " + name).formatted(Formatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int removeArea(FabricClientCommandSource source, String name) {
        if (LocalAreaManager.getArea(name) == null) {
            source.sendFeedback(Text.literal("没有名为 " + name + " 的本地区域").formatted(Formatting.RED));
            return 0;
        }
        LocalAreaManager.removeArea(name);
        source.sendFeedback(Text.literal("已删除本地区域 " + name).formatted(Formatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int listAreas(FabricClientCommandSource source) {
        String areas = LocalAreaManager.getConfig().areas.stream()
                .map(area -> "%s [%s @ %s] 屏幕: %d".formatted(area.name, area.server, area.dimension, area.screens.size()))
                .collect(Collectors.joining("\n"));
        source.sendFeedback(Text.literal(areas.isEmpty() ? "没有本地区域" : "本地区域列表:\n" + areas).formatted(Formatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private static int createScreen(FabricClientCommandSource source, String areaName, String name, Vector3f p1, Vector3f p2, Vector3f p3, Vector3f p4) {
        LocalAreaEntry area = LocalAreaManager.getArea(areaName);
        if (area == null) {
            source.sendFeedback(Text.literal("没有名为 " + areaName + " 的本地区域").formatted(Formatting.RED));
            return 0;
        }
        if (area.screens.stream().anyMatch(screen -> Objects.equals(screen.name, name))) {
            source.sendFeedback(Text.literal("区域 " + areaName + " 中已存在名为 " + name + " 的本地屏幕").formatted(Formatting.RED));
            return 0;
        }

        LocalScreenEntry screen = new LocalScreenEntry();
        screen.name = name;
        screen.p1 = p1;
        screen.p2 = p2;
        screen.p3 = p3;
        screen.p4 = p4;
        area.screens.add(screen);
        LocalAreaManager.save();
        LocalAreaManager.reloadArea(areaName);
        source.sendFeedback(Text.literal("已在本地区域 " + areaName + " 创建屏幕 " + name).formatted(Formatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int removeScreen(FabricClientCommandSource source, String areaName, String name) {
        LocalAreaEntry area = LocalAreaManager.getArea(areaName);
        if (area == null) {
            source.sendFeedback(Text.literal("没有名为 " + areaName + " 的本地区域").formatted(Formatting.RED));
            return 0;
        }
        boolean removed = area.screens.removeIf(screen -> Objects.equals(screen.name, name));
        if (!removed) {
            source.sendFeedback(Text.literal("区域 " + areaName + " 中没有名为 " + name + " 的本地屏幕").formatted(Formatting.RED));
            return 0;
        }
        LocalAreaManager.save();
        LocalAreaManager.reloadArea(areaName);
        source.sendFeedback(Text.literal("已从本地区域 " + areaName + " 删除屏幕 " + name).formatted(Formatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private static int listScreens(FabricClientCommandSource source, String areaName) {
        LocalAreaEntry area = LocalAreaManager.getArea(areaName);
        if (area == null) {
            source.sendFeedback(Text.literal("没有名为 " + areaName + " 的本地区域").formatted(Formatting.RED));
            return 0;
        }
        String screens = area.screens.stream()
                .map(screen -> screen.name)
                .collect(Collectors.joining("\n"));
        source.sendFeedback(Text.literal(screens.isEmpty() ? "区域 " + areaName + " 没有本地屏幕" : "区域 " + areaName + " 的本地屏幕:\n" + screens).formatted(Formatting.GOLD));
        return Command.SINGLE_SUCCESS;
    }

    private static Vector3f vec(CommandContext<FabricClientCommandSource> context, String suffix) {
        return new Vector3f(
                context.getArgument("x" + suffix, Float.class),
                context.getArgument("y" + suffix, Float.class),
                context.getArgument("z" + suffix, Float.class)
        );
    }
}
