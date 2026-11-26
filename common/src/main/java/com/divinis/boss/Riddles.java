package com.divinis.boss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.RandomSource;

/**
 * The Sphinx's riddles: simple ones, many of them. Each has its answer and three wrong ones; asked, the four are
 * shuffled (ask) and the server keeps which one is right.
 */
public final class Riddles {
    private Riddles() {}

    public record Riddle(String question, String answer, String wrong1, String wrong2, String wrong3) {}

    /** One asked: the question, the four answers in their order, which of them is right. */
    public record Asked(String question, List<String> options, int right) {}

    public static Asked ask(int index, RandomSource random) {
        Riddle r = ALL.get(index);
        List<String> options = new ArrayList<>(List.of(r.answer(), r.wrong1(), r.wrong2(), r.wrong3()));
        Collections.shuffle(options, new java.util.Random(random.nextLong()));
        return new Asked(r.question(), options, options.indexOf(r.answer()));
    }

    private static Riddle r(String question, String answer, String w1, String w2, String w3) {
        return new Riddle(question, answer, w1, w2, w3);
    }

    public static final List<Riddle> ALL = List.of(
            r("What walks on four legs in the morning, two at noon and three in the evening?", "A man", "A dog", "A horse", "A spider"),
            r("What has keys but opens no locks?", "A piano", "A prison", "A chest", "A map"),
            r("The more of it you take, the more you leave behind. What is it?", "Footsteps", "Gold", "Water", "Bread"),
            r("What has a neck but no head?", "A bottle", "A snake", "A sword", "A tree"),
            r("What has hands but cannot clap?", "A clock", "A statue", "A glove", "A tree"),
            r("What gets wetter the more it dries?", "A towel", "Sand", "A sponge cake", "The sun"),
            r("What has one eye but cannot see?", "A needle", "A cyclops", "A storm", "A coin"),
            r("What can you catch but never throw?", "A cold", "A ball", "A fish", "A spear"),
            r("What has a head and a tail but no body?", "A coin", "A snake", "A comet", "A worm"),
            r("What goes up but never comes down?", "Your age", "A bird", "Smoke", "An arrow"),
            r("What has teeth but cannot bite?", "A comb", "A skull", "A shark", "A wolf"),
            r("What is full of holes but still holds water?", "A sponge", "A net", "A sieve", "A cave"),
            r("What belongs to you, but others use it more than you do?", "Your name", "Your sword", "Your house", "Your gold"),
            r("What runs but never walks, has a mouth but never talks?", "A river", "A horse", "A wolf", "The wind"),
            r("What has a bed but never sleeps?", "A river", "An inn", "A soldier", "A garden"),
            r("What can travel around the world while staying in a corner?", "A stamp", "The moon", "A spider", "A ship"),
            r("What has many rings but no fingers?", "A tree", "A king", "A bell tower", "A chain"),
            r("What is so fragile that saying its name breaks it?", "Silence", "Glass", "An egg", "A promise"),
            r("What comes once in a minute, twice in a moment, but never in a thousand years?", "The letter M", "The sun", "A comet", "Midnight"),
            r("What has cities but no houses, forests but no trees, and water but no fish?", "A map", "A desert", "A dream", "A painting"),
            r("The more there is, the less you see. What is it?", "Darkness", "Fog", "Sand", "Smoke"),
            r("What can fill a room but takes up no space?", "Light", "Air", "Water", "Sand"),
            r("What has to be broken before you can use it?", "An egg", "A sword", "A horse", "A door"),
            r("I am tall when I am young and short when I am old. What am I?", "A candle", "A tree", "A man", "A mountain"),
            r("What month of the year has 28 days?", "All of them", "February", "None of them", "Only the last"),
            r("What is always in front of you but cannot be seen?", "The future", "The air", "Your nose", "The horizon"),
            r("What goes through cities and fields but never moves?", "A road", "A river", "The wind", "A cart"),
            r("What has legs but does not walk?", "A table", "A snake", "A fish", "A cloud"),
            r("What can you keep after giving it to someone?", "Your word", "Your gold", "Your sword", "Your bread"),
            r("What has an eye but cannot see, and spins the sea and sand?", "A storm", "A needle", "A potato", "A hawk"),
            r("I shave every day, yet my beard stays the same. Who am I?", "A barber", "A king", "A priest", "A beggar"),
            r("What has words but never speaks?", "A book", "A parrot", "A mute", "A stone"),
            r("What runs all around a field but never moves?", "A fence", "A horse", "A river", "A shepherd"),
            r("What can you hold in your right hand but never in your left?", "Your left hand", "A sword", "A coin", "A cup"),
            r("What has thirteen hearts but no other organs?", "A deck of cards", "A hydra", "A king's court", "A beehive"),
            r("What is black when you buy it, red when you use it, and grey when you throw it away?", "Coal", "Iron", "Wine", "Ink"),
            r("Feed me and I live, give me a drink and I die. What am I?", "Fire", "A plant", "A camel", "A horse"),
            r("What flies without wings and cries without eyes?", "A cloud", "A bird", "A ghost", "An arrow"),
            r("What has a golden head and a golden tail, but no body?", "A gold coin", "A lion", "A snake", "The sun"),
            r("What goes up when the rain comes down?", "An umbrella", "A river", "A bird", "Smoke"),
            r("The man who made it does not want it, the man who bought it does not use it, the man who uses it does not know it. What is it?",
                    "A coffin", "A crown", "A sword", "A ship"),
            r("What has roots that nobody sees, is taller than trees, yet never grows?", "A mountain", "A tower", "A pyramid", "The sky"),
            r("What is light as a feather, yet the strongest man cannot hold it for long?", "His breath", "A bird", "Sand", "Smoke"),
            r("What lives without a body, hears without ears, speaks without a mouth?", "An echo", "A ghost", "The wind", "A god"),
            r("Two in a corner, one in a room, none in a house, but one in a shelter. What is it?", "The letter R", "A spider", "A chair", "A cat"),
            r("What has a ring but no finger?", "A bell", "A pond", "A tree", "A planet"),
            r("What is so light it floats, yet a thousand men cannot lift it from the water?", "A bubble", "A leaf", "A feather", "A boat"),
            r("What can run but never tires, has a bank but no money?", "A river", "A thief", "A horse", "A merchant"),
            r("What has one head, one foot and four legs?", "A bed", "A goat", "A table", "A chair"),
            r("What walks on its head all day?", "A nail in a horseshoe", "A bat", "A snake", "A juggler"),
            r("What kind of room has no doors or windows?", "A mushroom", "A tomb", "A cellar", "A cave"),
            r("What has a thumb and four fingers but is not alive?", "A glove", "A statue", "A skeleton", "A puppet"),
            r("Forward I am heavy, backward I am not. What am I?", "Ton", "Stone", "Gold", "Iron"),
            r("What is always coming but never arrives?", "Tomorrow", "The tide", "A storm", "The night"),
            r("What tastes better than it smells?", "A tongue", "Bread", "Honey", "Wine"),
            r("What can you see in still water, yet it never gets wet?", "Your reflection", "A fish", "The sand", "A stone"),
            r("The sun bakes them, the hand breaks them, the foot treads on them, the mouth tastes them. What are they?",
                    "Grapes", "Bricks", "Bread", "Stones"),
            r("What goes up and down but does not move?", "A staircase", "A bird", "The tide", "A bucket"),
            r("Which is heavier: a pound of feathers or a pound of gold?", "They weigh the same", "The gold", "The feathers", "Neither weighs anything"),
            r("What do you lose every time you stand up?", "Your lap", "Your shadow", "Your breath", "Your seat"),
            r("What follows you everywhere in the sun but vanishes in the dark?", "Your shadow", "A dog", "The wind", "A ghost"),
            r("What has golden sand that falls but never runs out, if you turn it?", "An hourglass", "A desert", "A river", "A dune"),
            r("What crosses the desert with water inside it but never drinks?", "A camel's hump", "A cloud", "A merchant", "A well"),
            r("I am the beginning of eternity and the end of time and space. What am I?", "The letter E", "The sun", "Death", "The sky"),
            r("What can you break without touching it?", "A promise", "A mirror", "A bone", "A wall"),
            r("A father's child, a mother's child, yet no one's son. Who is it?", "A daughter", "An orphan", "A king", "A slave"),
            r("What is yours, yet you never use it - others say it to call you?", "Your name", "Your house", "Your gold", "Your shadow"),
            r("What rises in the east and sets in the west, and every sphinx has watched it a thousand thousand times?",
                    "The sun", "The moon", "A star", "A river")
    );
}
