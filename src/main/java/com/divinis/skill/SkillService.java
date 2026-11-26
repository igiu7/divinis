package com.divinis.skill;

import com.divinis.Config;

import net.minecraft.server.level.ServerPlayer;

public final class SkillService {
    private SkillService() {}

    private static double multiplier(Skill skill) {
        return switch (skill) {
            case BLACKSMITH -> Config.XP_MULT_BLACKSMITH.get();
            case FARMER -> Config.XP_MULT_FARMER.get();
            case MINER -> Config.XP_MULT_MINER.get();
            case HUNTER -> Config.XP_MULT_HUNTER.get();
        };
    }

    /** Adds XP of the work to the character, scaled by the config multiplier of its source. */
    public static void addXp(ServerPlayer player, Skill skill, double baseAmount) {
        if (baseAmount <= 0 || player.isCreative() || player.isSpectator()) return;
        double amount = baseAmount * multiplier(skill) * Perks.xpMultiplier(player);
        if (amount > 0) CharacterService.addXp(player, amount, skill.title);
    }
}
