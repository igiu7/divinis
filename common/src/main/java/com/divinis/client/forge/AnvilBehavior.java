package com.divinis.client.forge;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.diegesis.api.client.DiegesisParticles;
import com.pockyl.diegesis.api.client.DiegesisSounds;
import com.pockyl.diegesis.api.client.NodePainter;
import com.pockyl.diegesis.api.client.StationBehavior;
import com.pockyl.diegesis.api.client.StationView;
import com.pockyl.diegesis.api.event.SceneEvent;
import com.divinis.client.ClientForge;
import com.divinis.forge.AnvilForge;
import com.divinis.forge.ForgeShapes;
import com.divinis.Divinis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * The anvil at work (forge.AnvilForge), on this client. The book beside it (AnvilBook) holds the recipes - the weapon
 * parts, each with its outline; the player lays ingots on the anvil from the inventory below, and their metal is the
 * part's. A recipe picked and "Forge" pressed, the camera glances down at the anvil's face, where the ingots are a hot
 * bar of pixels. The player hammers it flat: holding the left button
 * over the anvil takes the hammer, which follows the cursor; lifted and brought down hard onto the ingot it strikes,
 * and each of the three strokes spreads the ingot out, until it is a flat blank of the part's shape and a margin round it.
 * The blank is then cut to the part's outline: clicking or dragging over its pixels cuts them away. "Done"
 * (AnvilScreenHooks) sends the cut to the server, which gives the part. Cutting a pixel of the part itself ruins the
 * metal at once: the server takes the ingots and the work ends. Escape while forging goes back to the book.
 */
public final class AnvilBehavior implements StationBehavior {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "block/forge_white");
    /** Hot metal: 16 heats across, 16 variants down, animated (tools/build_forge_textures.py); a pixel of metal takes one texel. */
    private static final ResourceLocation HOT = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "block/forge_hot");
    /** The hammer's texture, 32x32: regions {x, y, width, height}. */
    private static final ResourceLocation HAMMER = ResourceLocation.fromNamespaceAndPath(Divinis.MODID, "block/forge_hammer");
    private static final int[] IRON_SIDE = {0, 0, 8, 14}, IRON_FACE = {8, 0, 8, 8}, IRON_BAND = {8, 8, 8, 4},
            HAFT = {16, 0, 4, 32}, GRIP = {20, 0, 4, 32};
    private static final List<String> PARTS = List.copyOf(ForgeShapes.all().keySet());
    /** A pixel of the metal on the anvil, scene pixels, and how thick one layer of it is. */
    private static final float CELL = 0.6F;
    private static final float LAYER = 0.45F;
    private static final int STRIKES = 3;
    /** A stroke: the hammer lifted at least this high over the metal, then brought down onto it within this many seconds. */
    private static final float STRIKE_FROM = 2.5F;
    private static final float STRIKE_WITHIN = 0.6F;

    /** Readable ink for each era on the book's paper. */
    private static final Map<String, Integer> INK = Map.of("blacksteel", 0xFF2A2C34, "moonsilver", 0xFF4A5C84,
            "crimsonite", 0xFF8A1420, "mithril", 0xFF4A6A8A, "etherite", 0xFF1A7A86, "adamantite", 0xFF1C6A40,
            "dragonsteel", 0xFF8A3418, "divinite", 0xFF8A6A20, "valorium", 0xFF2A44A8, "voidsteel", 0xFF5A2A90);

    private enum Stage { BROWSE, FLATTEN, CUT }

    /** The station the local player works at, for the Done button over the screen. */
    static AnvilBehavior active;
    static StationView activeView;

    private final AnvilBook book = new AnvilBook(this);
    private String part;
    private int hoveredRow = -1;
    private StationView view;
    private boolean overButton;
    private Stage stage = Stage.BROWSE;

    // forging
    private int strikes;
    private float flat;              // the flattening shown, easing to strikes
    private int[][][] layers;        // [stage][row][column]: layers of metal at each stage of flattening
    private boolean[][] kept;
    private float sinceCut;
    private float shake;
    private float time;
    // the hammer, in the work node's pixels: x across the screen (+ to the left), its face's height over the metal
    private float hx = -6.5F, hh = 3.0F, hz = -1.5F;
    private boolean holding;
    /** The stroke that struck last still goes on: it cuts nothing until the button is let go and pressed again. */
    private boolean waitRelease;
    private float wantX, wantH;
    private float liftedAt = -10;

    public static void register() {
        DiegesisClient.registerBehavior(AnvilForge.ID, view -> new AnvilBehavior());
    }

    // ------------------------------------------------------------------ what the book reads

    /** The metal of the ingots laid on the anvil, or null. */
    String metal() { return view == null ? null : AnvilForge.hotMetalOf(laid()); }
    int laidCount() { return laid().getCount(); }
    String part() { return part; }
    int hoveredRow() { return hoveredRow; }

    private net.minecraft.world.item.ItemStack laid() {
        return view == null ? net.minecraft.world.item.ItemStack.EMPTY
                : view.state().getItem(com.pockyl.diegesis.api.state.SlotRef.of(AnvilForge.SLOT));
    }
    boolean overButton() { return overButton; }

    static int eraColour(String metal) {
        return INK.getOrDefault(metal, 0xFF4A3418);
    }

    static boolean creative() {
        Player p = Minecraft.getInstance().player;
        return p != null && p.isCreative();
    }

    static boolean knows(String metal) {
        return creative() || ClientForge.knows(ForgeShapes.tech(metal));
    }


    // ------------------------------------------------------------------ what the Done button reads

    boolean cutting() { return stage == Stage.CUT; }

    /** Pixels of metal still outside the outline. */
    int left() {
        if (kept == null || part == null) return 0;
        boolean[][] shape = ForgeShapes.shape(part).pixels();
        int n = 0;
        for (int y = 0; y < ForgeShapes.SIZE; y++)
            for (int x = 0; x < ForgeShapes.SIZE; x++)
                if (kept[y][x] && !shape[y][x]) n++;
        return n;
    }

    // ------------------------------------------------------------------ the station

    @Override
    public void onSceneReady(StationView view) {
        this.view = view;
        view.node("book_open").ifPresent(n -> n.setPainter(book));
        view.node("work").ifPresent(n -> n.setPainter(new Work()));
        show(view);
    }

    @Override
    public void onOpen(StationView view) {
        this.view = view;
        view.setInventoryFilter(stack -> AnvilForge.hotMetalOf(stack) != null);
        active = this;
        activeView = view;
        browse(view);
    }

    @Override
    public void onClose(StationView view) {
        if (active == this) {
            active = null;
            activeView = null;
        }
        stage = Stage.BROWSE;
        holding = false;
    }

    private void browse(StationView view) {
        stage = Stage.BROWSE;
        holding = false;
        if (view.currentGlance().isPresent()) view.glanceBack();
        show(view);
    }

    private void show(StationView view) {
        boolean browsing = stage == Stage.BROWSE;
        view.node("book_open").ifPresent(n -> n.setVisible(browsing));
        view.node("work").ifPresent(n -> n.setVisible(!browsing));
        view.node("ingots").ifPresent(n -> n.setVisible(browsing));
    }

    @Override
    public void frame(StationView view, float dt) {
        time += dt;
        book.setAppear(Mth.clamp((DiegesisClient.arrival() - 0.25F) / 0.75F, 0, 1));
        if (stage == Stage.BROWSE) {
            hover(view);
            return;
        }
        flat += (strikes - flat) * Math.min(1, dt * 14);
        shake = Math.max(0, shake - dt * 3);
        sinceCut += dt;
        if (stage == Stage.FLATTEN) hammer(view, dt);
    }

    private void hover(StationView view) {
        hoveredRow = -1;
        overButton = false;
        float[] p = AnvilBook.pointer(view);
        if (p == null) return;
        if (p[0] == 1) {
            int row = (int) Math.floor((p[2] - AnvilBook.ROW_Y + 0.5F) / AnvilBook.ROW_PITCH);
            if (row >= 0 && row < PARTS.size() && p[1] >= AnvilBook.ROW_X - 1 && p[1] <= AnvilBook.ROW_X + AnvilBook.ROW_WIDE) hoveredRow = row;
        } else {
            overButton = p[1] >= AnvilBook.BUTTON_X && p[1] <= AnvilBook.BUTTON_X + AnvilBook.BUTTON_WIDE
                    && p[2] >= AnvilBook.BUTTON_Y && p[2] <= AnvilBook.BUTTON_Y + AnvilBook.BUTTON_HIGH;
        }
    }

    @Override
    public void onEvent(StationView view, SceneEvent event) {
        if (event instanceof SceneEvent.Click click && click.button() == 0 && stage == Stage.BROWSE && click.node().equals("book_open")) {
            clickBook(view);
        } else if (event instanceof SceneEvent.Stroke stroke && stroke.node().equals("work")) {
            stroke(view, stroke);
        }
    }

    private void clickBook(StationView view) {
        hover(view);
        if (hoveredRow >= 0) {
            part = PARTS.get(hoveredRow);
            view.playSound(SoundEvents.BOOK_PAGE_TURN, "book_open", 0.6F, 1.1F);
        } else if (overButton && part != null) {
            start(view);
        }
    }

    private void start(StationView view) {
        String metal = metal();
        ForgeShapes.Shape shape = ForgeShapes.shape(part);
        if (metal == null) {
            view.showCaption(Component.literal("Lay hot ingots on the anvil: heat them in a Smelting Forge first."), 3);
            return;
        }
        if (!knows(metal)) {
            view.showCaption(Component.literal("Your city needs " + ClientForge.techName(ForgeShapes.tech(metal)) + "."), 3);
            return;
        }
        if (laidCount() < shape.ingots()) {
            view.showCaption(Component.literal("This part takes " + shape.ingots() + " ingots: lay " + (shape.ingots() - laidCount())
                    + " more on the anvil."), 3);
            return;
        }
        stage = Stage.FLATTEN;
        strikes = 0;
        flat = 0;
        layers = flattening(shape);
        kept = null;
        hx = -6.5F;
        hh = 3.0F;
        holding = false;
        waitRelease = false;
        liftedAt = -10;
        show(view);
        view.glance("forge");
        view.playSound(SoundEvents.FIRECHARGE_USE, "work", 0.4F, 1.4F);
        view.spawnParticles(DiegesisParticles.FLAME, "work", 6, 0);
        flattenCaption(view);
    }

    private void flattenCaption(StationView view) {
        view.showCaption(Component.literal("Hold the left button to take the hammer, lift it and bring it down hard on the hot ingot ("
                + strikes + "/" + STRIKES + ")"), 0);
    }

    @Override
    public boolean onKey(StationView view, int keyCode, int scanCode, int modifiers) {
        if (stage != Stage.BROWSE && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            view.showCaption(Component.literal(""), 0.01F);
            browse(view);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ the ingot: from a bar to the blank, in pixels

    /**
     * The metal at each stage: [0] the hot bar (seven pixels by three, two layers and a ridge, on the middle of the blank),
     * [1] and [2] spreading out from it over the blank (the nearest pixels first), its middle still thicker, [3] the blank,
     * one layer. Pixels of the bar that lie outside the blank are hammered into it.
     */
    private static int[][][] flattening(ForgeShapes.Shape shape) {
        boolean[][] blank = shape.blank();
        int n = ForgeShapes.SIZE;
        float sx = 0, sy = 0;
        int count = 0;
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++)
                if (blank[y][x]) {
                    sx += x;
                    sy += y;
                    count++;
                }
        int cx = Math.round(sx / Math.max(1, count)), cy = Math.round(sy / Math.max(1, count));
        int[][][] out = new int[STRIKES + 1][n][n];
        boolean[][] bar = new boolean[n][n];
        for (int y = cy - 1; y <= cy + 1; y++)
            for (int x = cx - 3; x <= cx + 3; x++)
                if (x >= 0 && y >= 0 && x < n && y < n) bar[y][x] = true;
        // how far each pixel of the blank is from the bar, going through the blank
        int[][] dist = new int[n][n];
        for (int[] row : dist) java.util.Arrays.fill(row, Integer.MAX_VALUE);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++)
                if (bar[y][x]) {
                    dist[y][x] = 0;
                    queue.add(new int[] {x, y});
                }
        int far = 1;
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = c[0] + d[0], y = c[1] + d[1];
                if (x < 0 || y < 0 || x >= n || y >= n || dist[y][x] != Integer.MAX_VALUE || !(blank[y][x] || bar[y][x])) continue;
                dist[y][x] = dist[c[1]][c[0]] + 1;
                if (blank[y][x]) far = Math.max(far, dist[y][x]);
                queue.add(new int[] {x, y});
            }
        }
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                // the bar as an ingot: two layers, a narrower third along its middle
                out[0][y][x] = !bar[y][x] ? 0 : y == cy && Math.abs(x - cx) <= 2 ? 3 : 2;
                boolean in = blank[y][x];
                int d = dist[y][x];
                out[1][y][x] = bar[y][x] ? 2 : in && d <= far * 0.4F ? 1 : 0;
                out[2][y][x] = bar[y][x] && in ? 2 : in && d <= far * 0.75F ? 1 : 0;
                out[3][y][x] = in ? 1 : 0;
            }
        }
        return out;
    }

    /** The metal's height at a pixel now, in layers (0: none), easing from one stage to the next. */
    private float height(int x, int y) {
        if (kept != null) return kept[y][x] ? 1 : 0;
        int from = Mth.clamp((int) Math.floor(flat), 0, STRIKES), to = Mth.clamp(from + 1, 0, STRIKES);
        float t = Mth.clamp(flat - from, 0, 1);
        int a = layers[from][y][x], b = layers[to][y][x];
        if (a == 0 && b == 0) return 0;
        if (a == 0) return t < 0.5F ? 0 : b;
        if (b == 0) return t < 0.5F ? a : 0;
        return Mth.lerp(t, a, b);
    }

    /** Where the metal lies across the screen now, the work node's x: {from, to}. */
    private float[] metalSpan() {
        float lo = 99, hi = -99;
        for (int y = 0; y < ForgeShapes.SIZE; y++)
            for (int x = 0; x < ForgeShapes.SIZE; x++)
                if (height(x, y) > 0) {
                    lo = Math.min(lo, cellX(x) - CELL / 2);
                    hi = Math.max(hi, cellX(x) + CELL / 2);
                }
        return new float[] {lo, hi};
    }

    // ------------------------------------------------------------------ the hammer

    private void hammer(StationView view, float dt) {
        float tx = holding ? Mth.clamp(wantX, -6.5F, 6.5F) : -6.5F;
        float th = holding ? Mth.clamp(wantH, 0, 10) : 3.0F;
        hx += (tx - hx) * Math.min(1, dt * 30);
        hz += ((holding ? 0 : -1.5F) - hz) * Math.min(1, dt * 20);
        hh += (th - hh) * Math.min(1, dt * 40);
        if (!holding) return;
        if (wantH >= STRIKE_FROM) liftedAt = time;
        // the cursor brought down onto the metal soon after the hammer was up: a stroke
        if (wantH <= 0.4F && time - liftedAt <= STRIKE_WITHIN) {
            liftedAt = -10;
            hh = 0;
            float[] span = metalSpan();
            strike(view, hx >= span[0] - 1.0F && hx <= span[1] + 1.0F);
        }
    }

    private void strike(StationView view, boolean onMetal) {
        if (!onMetal) {
            view.playSound(SoundEvents.ANVIL_LAND, "work", 0.15F, 1.8F);
            return;
        }
        strikes = Math.min(STRIKES, strikes + 1);
        shake = 1;
        Vector3f at = toScene(view, hx, 0.8F, 0);
        sparks(view, at);
        view.playSound(SoundEvents.ANVIL_USE, "work", 0.45F, 1.2F + 0.1F * strikes);
        if (strikes >= STRIKES) {
            stage = Stage.CUT;
            kept = copy(ForgeShapes.shape(part).blank());
            holding = false;
            waitRelease = true;
            view.showCaption(Component.literal("Cut away the metal outside the outline with the left button, then press Done"), 0);
        } else {
            flattenCaption(view);
        }
    }

    /**
     * Sparks flying off the hot metal where the hammer struck: a burst of the scene's own sparks, and the world's -
     * glowing bits thrown out all round, some falling as embers - so they are seen however the scene draws.
     */
    private static void sparks(StationView view, Vector3f at) {
        view.spawnParticles(DiegesisParticles.SPARK, at, new Vector3f(0, 10, 0), 7.0F, 40, 0xFFFFB040);
        view.spawnParticles(DiegesisParticles.SPARK, at, new Vector3f(0, 5, 0), 4.0F, 20, 0xFFFFE080);
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        net.minecraft.world.phys.Vec3 w = view.toWorld(at.x, at.y, at.z);
        var rnd = level.getRandom();
        for (int i = 0; i < 18; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, speed = 0.08 + rnd.nextDouble() * 0.14;
            level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, w.x, w.y, w.z,
                    Math.cos(a) * speed, 0.05 + rnd.nextDouble() * 0.15, Math.sin(a) * speed);
        }
        for (int i = 0; i < 6; i++) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.LAVA, w.x, w.y, w.z, 0, 0, 0);
        }
        for (int i = 0; i < 5; i++) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, w.x + (rnd.nextDouble() - 0.5) * 0.15, w.y,
                    w.z + (rnd.nextDouble() - 0.5) * 0.15, (rnd.nextDouble() - 0.5) * 0.04, 0.02, (rnd.nextDouble() - 0.5) * 0.04);
        }
    }

    /** A point of the work node (its own pixels) in scene pixels. */
    private static Vector3f toScene(StationView view, float x, float y, float z) {
        return view.node("work").map(n -> new Matrix4f(n.sceneMatrix()).transformPosition(new Vector3f(x, y, z)))
                .orElse(new Vector3f(8, 16.5F, 8));
    }

    // ------------------------------------------------------------------ strokes: holding the hammer, cutting

    private void stroke(StationView view, SceneEvent.Stroke stroke) {
        SceneEvent.Stroke.Phase phase = stroke.phase();
        if (phase == SceneEvent.Stroke.Phase.HOVER || stroke.button() != 0) return;
        if (waitRelease) {
            if (phase == SceneEvent.Stroke.Phase.END) waitRelease = false;
            return;
        }
        if (stage == Stage.FLATTEN) {
            if (phase == SceneEvent.Stroke.Phase.END) {
                holding = false;
                return;
            }
            // the cursor on the upright plane facing the camera, in the work node's own pixels
            Vector3f local = view.node("work").map(n -> new Matrix4f(n.sceneMatrix()).invert()
                    .transformPosition(new Vector3f(stroke.point()))).orElse(null);
            if (local == null) return;
            if (!holding) liftedAt = -10;
            holding = true;
            wantX = local.x;
            wantH = local.y - topOfMetal();
        } else if (stage == Stage.CUT && phase != SceneEvent.Stroke.Phase.END) {
            cut(view);
        }
    }

    private float topOfMetal() {
        float top = 0;
        for (int y = 0; y < ForgeShapes.SIZE; y++)
            for (int x = 0; x < ForgeShapes.SIZE; x++)
                top = Math.max(top, height(x, y));
        return 0.02F + top * LAYER;
    }

    private void cut(StationView view) {
        Vector3f p = view.pointerOn("work", 0.02F + LAYER).orElse(null);
        if (p == null || kept == null) return;
        int gx = (int) Math.floor(8 - p.x / CELL);
        int gy = (int) Math.floor(8 - p.z / CELL);
        if (gx < 0 || gy < 0 || gx >= ForgeShapes.SIZE || gy >= ForgeShapes.SIZE || !kept[gy][gx]) return;
        kept[gy][gx] = false;
        if (ForgeShapes.shape(part).pixels()[gy][gx]) {
            ruin(view);
            return;
        }
        view.spawnParticles(DiegesisParticles.SPARK, toScene(view, cellX(gx), 0.5F, cellZ(gy)), new Vector3f(0, 3, 0), 1.5F, 3, 0xFFFFC060);
        if (sinceCut > 0.05F) {
            view.playSound(DiegesisSounds.SPARK, "work", 0.35F, 1.4F + (float) Math.random() * 0.4F);
            sinceCut = 0;
        }
    }

    /** A pixel of the part was cut: the metal is ruined, the server takes the ingots. */
    private void ruin(StationView view) {
        CompoundTag data = new CompoundTag();
        data.putString("part", part);
        view.sendAction(AnvilForge.RUIN, data);
        view.spawnParticles(DiegesisParticles.SMOKE, "work", 14, 0);
        view.spawnParticles(DiegesisParticles.SPARK, "work", 20, 0xFFFF7020);
        view.showCaption(Component.literal("You cut into the part: the metal is ruined"), 4);
        browse(view);
    }

    /** The Done button was pressed. */
    void done(StationView view) {
        int left = left();
        if (left > 0) {
            view.showCaption(Component.literal(left + (left == 1 ? " pixel" : " pixels") + " of metal still to cut away"), 3);
            return;
        }
        CompoundTag data = new CompoundTag();
        data.putString("part", part);
        data.putLongArray("kept", ForgeShapes.pack(kept));
        view.sendAction(AnvilForge.FORGE, data);
        view.spawnParticles(DiegesisParticles.GLINT, "work", 12, 0);
        view.showCaption(Component.literal(""), 0.01F);
        browse(view);
    }

    private static boolean[][] copy(boolean[][] in) {
        boolean[][] out = new boolean[in.length][];
        for (int i = 0; i < in.length; i++) out[i] = in[i].clone();
        return out;
    }

    /** Where a pixel of the metal lies, in the work node's pixels: the icon's left on the screen's left (+x). */
    private static float cellX(int gx) { return -(gx - 7.5F) * CELL; }
    private static float cellZ(int gy) { return (7.5F - gy) * CELL; }

    // ------------------------------------------------------------------ drawing

    /** Boxes in a node's pixels, textured from sprites of the block atlas, each face its own region. */
    static final class Boxes {
        private static final int UP = 0, DOWN = 1, NORTH = 2, SOUTH = 3;
        private static final int[] SHADE = {0xFFFFFFFF, 0xFF808080, 0xFFD8D8D8, 0xFFB0B0B0, 0xFFC4C4C4, 0xFF9C9C9C};
        private final VertexConsumer vc;
        private final PoseStack.Pose pose;

        Boxes(PoseStack poseStack, MultiBufferSource buffers) {
            vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
            pose = poseStack.last();
        }

        static TextureAtlasSprite sprite(ResourceLocation id) {
            return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id);
        }

        /**
         * A box, each face showing its region of the sprite ({x, y, width, height} in pixels of a {@code size} texture;
         * width and height 0 for one texel); {@code along}: the long faces show their region's length along the box's x
         * (a haft). Faces turned away from the light are shaded as a block's.
         */
        void box(float x0, float y0, float z0, float x1, float y1, float z1, TextureAtlasSprite s, int size, int[][] regions,
                 boolean along, int light) {
            float[][][] corners = {
                    {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}},
                    {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}},
                    {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}},
                    {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
                    {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}},
                    {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}}};
            float[][][] uvs = {
                    {{0, 0}, {0, 1}, {1, 1}, {1, 0}}, {{0, 0}, {1, 0}, {1, 1}, {0, 1}},
                    {{0, 1}, {0, 0}, {1, 0}, {1, 1}}, {{0, 1}, {1, 1}, {1, 0}, {0, 0}},
                    {{0, 1}, {1, 1}, {1, 0}, {0, 0}}, {{0, 1}, {0, 0}, {1, 0}, {1, 1}}};
            float[][] normals = {{0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
            for (int f = 0; f < 6; f++) {
                int[] r = regions[f];
                boolean swap = along && (f == UP || f == DOWN || f == NORTH || f == SOUTH);
                for (int i = 0; i < 4; i++) {
                    float u = uvs[f][i][0], v = uvs[f][i][1];
                    if (swap) {
                        float t = u;
                        u = v;
                        v = t;
                    }
                    float[] c = corners[f][i];
                    float pu = r[2] == 0 ? r[0] + 0.5F : r[0] + u * r[2];
                    float pv = r[3] == 0 ? r[1] + 0.5F : r[1] + v * r[3];
                    vertex(c[0], c[1], c[2], SHADE[f], s.getU(pu / size), s.getV(pv / size), normals[f], light);
                }
            }
        }

        /** A pixel of hot metal: one texel of the hot sprite (its heat across, a variant down), glowing. */
        void pixel(float x0, float y0, float z0, float x1, float y1, float z1, TextureAtlasSprite s, int heat, int variant) {
            int[] r = {heat, variant, 0, 0};
            box(x0, y0, z0, x1, y1, z1, s, 16, new int[][] {r, r, r, r, r, r}, false, LightTexture.FULL_BRIGHT);
        }

        /** A box of plain white, glowing (the outline to cut along). */
        void flat(float x0, float y0, float z0, float x1, float y1, float z1) {
            int[] r = {8, 8, 0, 0};
            box(x0, y0, z0, x1, y1, z1, sprite(WHITE), 16, new int[][] {r, r, r, r, r, r}, false, LightTexture.FULL_BRIGHT);
        }

        private void vertex(float x, float y, float z, int c, float u, float v, float[] n, int light) {
            vc.addVertex(pose, x, y, z).setColor(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24).setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n[0], n[1], n[2]);
        }
    }

    /** The work on the anvil's face: the hot metal, pixel by pixel, the outline to cut along, the hammer. */
    private final class Work implements NodePainter {
        @Override
        public void paint(PoseStack poseStack, MultiBufferSource buffers, int light) {
            if (stage == Stage.BROWSE || part == null || layers == null) return;
            poseStack.pushPose();
            if (shake > 0) poseStack.translate(0, shake * 0.08F * Mth.sin(time * 90), 0);
            Boxes metal = new Boxes(poseStack, buffers);
            TextureAtlasSprite hot = Boxes.sprite(HOT);
            boolean[][] shape = ForgeShapes.shape(part).pixels();
            float h = CELL / 2;
            for (int y = 0; y < ForgeShapes.SIZE; y++) {
                for (int x = 0; x < ForgeShapes.SIZE; x++) {
                    float layers = height(x, y);
                    if (layers <= 0) continue;
                    // hotter where the metal is thick, cooler at its edges; cutting, the part glows brighter than the
                    // margin to be cut away
                    boolean edge = x == 0 || y == 0 || x == 15 || y == 15 || height(x - 1, y) <= 0 || height(x + 1, y) <= 0
                            || height(x, y - 1) <= 0 || height(x, y + 1) <= 0;
                    int heat = stage == Stage.CUT ? (shape[y][x] ? 13 : 9) : 10 + Math.round(layers);
                    if (edge) heat -= 2;
                    metal.pixel(cellX(x) - h, 0.02F, cellZ(y) - h, cellX(x) + h, 0.02F + layers * LAYER, cellZ(y) + h, hot,
                            Mth.clamp(heat, 0, 15), (x * 7 + y * 13) % 16);
                }
            }
            if (stage == Stage.CUT) {
                // the outline of the part, along every edge of its pixels that has no pixel of it beside it
                float t = 0.06F, top = 0.02F + LAYER + 0.02F;
                for (int y = 0; y < ForgeShapes.SIZE; y++) {
                    for (int x = 0; x < ForgeShapes.SIZE; x++) {
                        if (!shape[y][x]) continue;
                        float cx = cellX(x), cz = cellZ(y);
                        if (y == 0 || !shape[y - 1][x]) metal.flat(cx - h, top, cz + h - t, cx + h, top + 0.03F, cz + h + t);
                        if (y == 15 || !shape[y + 1][x]) metal.flat(cx - h, top, cz - h - t, cx + h, top + 0.03F, cz - h + t);
                        if (x == 0 || !shape[y][x - 1]) metal.flat(cx + h - t, top, cz - h, cx + h + t, top + 0.03F, cz + h);
                        if (x == 15 || !shape[y][x + 1]) metal.flat(cx - h - t, top, cz - h, cx - h + t, top + 0.03F, cz + h);
                    }
                }
            }
            poseStack.popPose();
            if (stage == Stage.FLATTEN) {
                // the smith's hammer: an upright iron head striking with its bottom face, the haft through its middle
                // running off to the screen's right, a leather grip at its end
                poseStack.pushPose();
                poseStack.translate(hx, topOfMetal() + hh, hz);
                poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-6 - hh * 3));
                Boxes hammer = new Boxes(poseStack, buffers);
                TextureAtlasSprite tex = Boxes.sprite(HAMMER);
                int lit = LightTexture.pack(Math.max(LightTexture.block(light), 11), Math.max(LightTexture.sky(light), 11));
                int[][] head = {IRON_FACE, IRON_FACE, IRON_SIDE, IRON_SIDE, IRON_SIDE, IRON_SIDE};
                hammer.box(-1.3F, 0.3F, -1.3F, 1.3F, 4.4F, 1.3F, tex, 32, head, false, lit);
                hammer.box(-1.15F, 0.0F, -1.15F, 1.15F, 0.3F, 1.15F, tex, 32, head, false, lit);    // the striking face
                hammer.box(-1.0F, 4.4F, -1.0F, 1.0F, 5.0F, 1.0F, tex, 32, head, false, lit);        // the peen
                int[][] haft = {HAFT, HAFT, HAFT, HAFT, IRON_BAND, IRON_BAND};
                hammer.box(-10.6F, 1.75F, -0.5F, 1.7F, 2.75F, 0.5F, tex, 32, haft, true, lit);
                int[][] grip = {GRIP, GRIP, GRIP, GRIP, GRIP, GRIP};
                hammer.box(-11.0F, 1.6F, -0.65F, -7.0F, 2.9F, 0.65F, tex, 32, grip, true, lit);
                int[][] band = {IRON_BAND, IRON_BAND, IRON_BAND, IRON_BAND, IRON_BAND, IRON_BAND};
                hammer.box(-2.2F, 1.5F, -0.75F, -1.3F, 3.0F, 0.75F, tex, 32, band, false, lit);      // the iron band
                poseStack.popPose();
            }
        }
    }
}
