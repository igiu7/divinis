package com.divinis.skill;

import java.util.function.Supplier;

import com.divinis.Divinis;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class SkillAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Divinis.MODID);

    /** The old professions' XP: only read once, to bring it into the character (CharacterService.migrate). */
    public static final Supplier<AttachmentType<SkillData>> SKILLS = ATTACHMENTS.register("skills",
            () -> AttachmentType.builder(SkillData::new).serialize(SkillData.CODEC).copyOnDeath().build());
    /** The character: level, skill points, skill book. */
    public static final Supplier<AttachmentType<CharacterData>> CHARACTER = ATTACHMENTS.register("character",
            () -> AttachmentType.builder(CharacterData::new).serialize(CharacterData.CODEC).copyOnDeath().build());

    private SkillAttachments() {}

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
