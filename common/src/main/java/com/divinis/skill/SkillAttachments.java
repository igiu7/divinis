package com.divinis.skill;

import com.divinis.Divinis;
import com.divinis.platform.DataKey;

import net.minecraft.resources.ResourceLocation;

public final class SkillAttachments {
    /** The old professions' XP: only read once, to bring it into the character (CharacterService.migrate). */
    public static DataKey<SkillData> SKILLS;
    /** The character: level, skill points, skill book. */
    public static DataKey<CharacterData> CHARACTER;

    private SkillAttachments() {}

    /** During the mod's start-up (NeoForge's attachment types must be registered then). */
    public static void register() {
        SKILLS = DataKey.create(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "skills"), SkillData::new, SkillData.CODEC, true);
        CHARACTER = DataKey.create(ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "character"), CharacterData::new,
                CharacterData.CODEC, true);
    }
}
