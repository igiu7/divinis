package com.divinis.client.forge;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.divinis.Divinis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import com.divinis.platform.Dist;
import com.divinis.platform.bus.SubscribeEvent;
import com.divinis.platform.bus.EventBusSubscriber;
import com.divinis.platform.event.client.ScreenEvent;

/** The anvil's Done button, low on the right of the screen (over the inventory) while the player cuts the metal (AnvilBehavior). */
@EventBusSubscriber(modid = Divinis.MODID, value = Dist.CLIENT)
public final class AnvilScreenHooks {
    private AnvilScreenHooks() {}

    private static final int WIDE = 110, HIGH = 20;

    private static boolean shown(Screen screen) {
        return AnvilBehavior.active != null && AnvilBehavior.activeView != null && AnvilBehavior.active.cutting()
                && DiegesisClient.isStationScreen(screen);
    }

    private static int x(Screen s) { return s.width - WIDE - 16; }
    private static int y(Screen s) { return s.height - HIGH - 64; }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        Screen s = event.getScreen();
        if (!shown(s)) return;
        GuiGraphics g = event.getGuiGraphics();
        int x = x(s), y = y(s);
        boolean over = event.getMouseX() >= x && event.getMouseX() < x + WIDE && event.getMouseY() >= y && event.getMouseY() < y + HIGH;
        int left = AnvilBehavior.active.left();
        boolean ready = left == 0;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x - 1, y - 1, x + WIDE + 1, y + HIGH + 1, 0xFF2A1A0C);
        g.fill(x, y, x + WIDE, y + HIGH, ready ? (over ? 0xFFC0802E : 0xFF9A6A30) : (over ? 0xFF6A5A48 : 0xFF54483A));
        g.fill(x, y + HIGH - 2, x + WIDE, y + HIGH, 0x40000000);
        g.drawCenteredString(Minecraft.getInstance().font, "Done", x + WIDE / 2, y + 6, ready ? 0xFFFFF0D8 : 0xFFC8BCA8);
        if (!ready) {
            g.drawCenteredString(Minecraft.getInstance().font, left + (left == 1 ? " pixel" : " pixels") + " left to cut",
                    x + WIDE / 2, y - 11, 0xFFE0D0B0);
        }
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen s = event.getScreen();
        if (!shown(s) || event.getButton() != 0) return;
        int x = x(s), y = y(s);
        if (event.getMouseX() >= x && event.getMouseX() < x + WIDE && event.getMouseY() >= y && event.getMouseY() < y + HIGH) {
            event.setCanceled(true);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            AnvilBehavior.active.done(AnvilBehavior.activeView);
        }
    }
}
