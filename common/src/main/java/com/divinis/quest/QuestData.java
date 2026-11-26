package com.divinis.quest;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A player's quests, kept on death: the quests taken (id -> mobs slain so far; 0 for a DELIVER quest, whose items
 * are counted in the inventory) and how often each quest has been turned in.
 */
public final class QuestData {
    public static final Codec<QuestData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("active", Map.of()).forGetter(d -> d.active),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("done", Map.of()).forGetter(d -> d.done)
    ).apply(i, QuestData::new));

    public final Map<String, Integer> active = new LinkedHashMap<>();
    public final Map<String, Integer> done = new LinkedHashMap<>();

    public QuestData() {}

    private QuestData(Map<String, Integer> active, Map<String, Integer> done) {
        active.forEach((k, v) -> { if (Quests.byId(k).isPresent()) this.active.put(k, v); });
        this.done.putAll(done);
    }

    public int totalDone() {
        int sum = 0;
        for (int n : done.values()) sum += n;
        return sum;
    }
}
