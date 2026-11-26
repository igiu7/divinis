package com.divinis.net;

import com.divinis.client.ClientHandlers;

import net.minecraft.server.level.ServerPlayer;
import com.divinis.platform.net.PacketDistributor;
import com.divinis.platform.net.PayloadRegistrar;

public final class DivinisNet {
    private DivinisNet() {}

    public static void register() {
        PayloadRegistrar registrar = new PayloadRegistrar();
        registrar.playToServer(DrawPayloads.Submit.TYPE, DrawPayloads.Submit.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.level().getEntity(payload.boss()) instanceof com.divinis.boss.Nocthariel boss) {
                boss.drawingSubmitted(player, payload.strokes());
            }
        }));
        registrar.playToServer(RiddlePayloads.Answer.TYPE, RiddlePayloads.Answer.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player && player.level().getEntity(payload.boss()) instanceof com.divinis.boss.Sphinx boss) {
                boss.answered(player, payload.choice());
            }
        }));
        registrar.playToServer(SkillPayloads.Use.TYPE, SkillPayloads.Use.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) com.divinis.skill.Dash.use(player);
        }));
        registrar.playToServer(FishingPayloads.Press.TYPE, FishingPayloads.Press.CODEC, (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) com.divinis.fishing.FishingGame.press(player, payload.hook(), payload.hit(), payload.perfect());
        }));
        if (com.divinis.platform.Services.dist().isClient()) {
            registrar.playToClient(CharacterPayload.TYPE, CharacterPayload.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> com.divinis.client.ClientCharacter.receive(payload)));
            registrar.playToClient(ForgeKnownPayload.TYPE, ForgeKnownPayload.CODEC, ClientHandlers::onForgeKnown);
            registrar.playToClient(GuildMasterOpenPayload.TYPE, GuildMasterOpenPayload.CODEC, ClientHandlers::onGuildMasterOpen);
            registrar.playToClient(QuestPayload.TYPE, QuestPayload.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> com.divinis.client.ClientQuests.receive(payload)));
            registrar.playToClient(TelegraphPayload.TYPE, TelegraphPayload.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> com.divinis.client.TelegraphRenderer.receive(payload)));
            registrar.playToClient(FireFxPayload.TYPE, FireFxPayload.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> com.divinis.client.FireFx.receive(payload)));
            registrar.playToClient(SlashPayload.TYPE, SlashPayload.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> com.divinis.client.SlashFx.receive(payload)));
            registrar.playToClient(ScreenFxPayload.TYPE, ScreenFxPayload.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> com.divinis.client.ScreenFxClient.receive(payload)));
            registrar.playToClient(BeamPayload.TYPE, BeamPayload.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> com.divinis.client.BeamFx.receive(payload)));
            registrar.playToClient(BulletPayload.TYPE, BulletPayload.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> com.divinis.client.BulletFx.receive(payload)));
            registrar.playToClient(CreatorFxPayload.TYPE, CreatorFxPayload.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> com.divinis.client.CreatorFx.receive(payload)));
            registrar.playToClient(DrawPayloads.Open.TYPE, DrawPayloads.Open.CODEC, ClientHandlers::onDrawOpen);
            registrar.playToClient(RiddlePayloads.Open.TYPE, RiddlePayloads.Open.CODEC, ClientHandlers::onRiddleOpen);
            registrar.playToClient(RiddlePayloads.Close.TYPE, RiddlePayloads.Close.CODEC, ClientHandlers::onRiddleClose);
            registrar.playToClient(SkillPayloads.Points.TYPE, SkillPayloads.Points.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> com.divinis.client.DashClient.receivePoints(payload.points(), payload.book())));
            registrar.playToClient(FishingPayloads.Start.TYPE, FishingPayloads.Start.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> com.divinis.client.FishingHud.start(payload)));
            registrar.playToClient(FishingPayloads.End.TYPE, FishingPayloads.End.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> com.divinis.client.FishingHud.end(payload)));
            registrar.playToClient(SkillFxPayload.TYPE, SkillFxPayload.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> com.divinis.client.SkillFx.receive(payload)));
            registrar.playToClient(FishingPayloads.Caught.TYPE, FishingPayloads.Caught.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> com.divinis.client.CatchPopups.receive(payload.stacks())));
            registrar.playToClient(FishingPayloads.Foam.TYPE, FishingPayloads.Foam.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> com.divinis.client.FishingFoam.receive(payload)));
            registrar.playToClient(SkillPayloads.Dash.TYPE, SkillPayloads.Dash.CODEC,
                    (payload, ctx) -> ctx.enqueueWork(() -> com.divinis.client.DashClient.receiveDash(payload.entity())));
        } else {
            registrar.playToClient(CharacterPayload.TYPE, CharacterPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(ForgeKnownPayload.TYPE, ForgeKnownPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(GuildMasterOpenPayload.TYPE, GuildMasterOpenPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(QuestPayload.TYPE, QuestPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(TelegraphPayload.TYPE, TelegraphPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(FireFxPayload.TYPE, FireFxPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(SlashPayload.TYPE, SlashPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(ScreenFxPayload.TYPE, ScreenFxPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(BeamPayload.TYPE, BeamPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(BulletPayload.TYPE, BulletPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(CreatorFxPayload.TYPE, CreatorFxPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(DrawPayloads.Open.TYPE, DrawPayloads.Open.CODEC, (payload, ctx) -> {});
            registrar.playToClient(RiddlePayloads.Open.TYPE, RiddlePayloads.Open.CODEC, (payload, ctx) -> {});
            registrar.playToClient(RiddlePayloads.Close.TYPE, RiddlePayloads.Close.CODEC, (payload, ctx) -> {});
            registrar.playToClient(SkillPayloads.Points.TYPE, SkillPayloads.Points.CODEC, (payload, ctx) -> {});
            registrar.playToClient(FishingPayloads.Start.TYPE, FishingPayloads.Start.CODEC, (payload, ctx) -> {});
            registrar.playToClient(FishingPayloads.End.TYPE, FishingPayloads.End.CODEC, (payload, ctx) -> {});
            registrar.playToClient(SkillFxPayload.TYPE, SkillFxPayload.CODEC, (payload, ctx) -> {});
            registrar.playToClient(FishingPayloads.Caught.TYPE, FishingPayloads.Caught.CODEC, (payload, ctx) -> {});
            registrar.playToClient(FishingPayloads.Foam.TYPE, FishingPayloads.Foam.CODEC, (payload, ctx) -> {});
            registrar.playToClient(SkillPayloads.Dash.TYPE, SkillPayloads.Dash.CODEC, (payload, ctx) -> {});
        }
    }

    /** The player's character, for the skill trees (sent whenever it is saved, and on login). */
    public static void sendCharacter(ServerPlayer player) {
        com.divinis.skill.CharacterData c = com.divinis.skill.CharacterService.data(player);
        int level = c.level();
        PacketDistributor.sendToPlayer(player, new CharacterPayload(level, (int) com.divinis.skill.SkillCurve.progressInLevel(c.xp),
                level >= com.divinis.skill.SkillCurve.MAX_LEVEL ? 0 : (int) com.divinis.skill.SkillCurve.needFor(level),
                com.divinis.skill.CharacterService.freePoints(c), c.points.clone(), c.book,
                (int) ((com.divinis.skill.CharacterService.resetWait(c) + 999) / 1000)));
    }
}
