package com.divinis.client.gui;

import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.GuiHelper;
import dev.ftb.mods.ftblibrary.ui.ModalPanel;
import dev.ftb.mods.ftblibrary.ui.TextBox;
import dev.ftb.mods.ftblibrary.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/** Modal dialogs in the frame's look: confirmation and name input. */
final class Dialogs {
    private static final int W = 244;
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} '_-]{3,24}");

    private Dialogs() {}

    static void confirm(BaseScreen gui, String title, String message, Color4I accent, Runnable onYes) {
        ConfirmDialog dialog = new ConfirmDialog(gui, title, message, accent, onYes);
        dialog.setPos((gui.getWidth() - dialog.getWidth()) / 2, (gui.getHeight() - dialog.getHeight()) / 2);
        gui.pushModalPanel(dialog);
    }

    static void prompt(BaseScreen gui, String title, String hint, Color4I accent, Consumer<String> onAccept) {
        prompt(gui, title, hint, accent, "Create", "", onAccept);
    }

    /** Text input with a custom button label and a starting text (e.g. the current name when renaming). */
    static void prompt(BaseScreen gui, String title, String hint, Color4I accent, String button, String initial,
                       Consumer<String> onAccept) {
        InputDialog dialog = new InputDialog(gui, title, hint, accent, button, initial, onAccept);
        dialog.setPos((gui.getWidth() - dialog.getWidth()) / 2, (gui.getHeight() - dialog.getHeight()) / 2);
        gui.pushModalPanel(dialog);
        dialog.focusInput();
    }

    // ------------------------------------------------------------------ shared look

    private abstract static class BaseDialog extends ModalPanel {
        protected final String title;
        protected final Color4I accent;

        BaseDialog(BaseScreen gui, String title, Color4I accent) {
            super(gui);
            this.title = title;
            this.accent = accent;
        }

        protected void close() {
            getGui().closeModalPanel(this);
        }

        @Override
        public void draw(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            GuiHelper.setupDrawing();
            g.pose().pushPose();
            g.pose().translate(0, 0, 900);
            // dim everything behind the dialog
            Color4I.BLACK.withAlpha(130).draw(g, 0, 0, getWindow().getGuiScaledWidth(), getWindow().getGuiScaledHeight());
            super.draw(g, theme, x, y, w, h);
            g.pose().popPose();
        }

        @Override
        public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            Color4I.BLACK.withAlpha(60).draw(g, x + 3, y + 3, w, h);
            Ui.box(g, x, y, w, h, Ui.HEADER, Ui.BORDER);
            accent.draw(g, x + 1, y + 1, w - 2, 2);
            Ui.text(g, theme, Component.literal(title).withStyle(ChatFormatting.BOLD), x + 12, y + 10, Ui.TEXT, true);
        }
    }

    // ------------------------------------------------------------------ confirm

    private static final class ConfirmDialog extends BaseDialog {
        private final List<FormattedText> lines;
        private final Runnable onYes;
        private Buttons.ChipButton yes;
        private Buttons.ChipButton no;

        ConfirmDialog(BaseScreen gui, String title, String message, Color4I accent, Runnable onYes) {
            super(gui, title, accent);
            this.onYes = onYes;
            this.lines = gui.getTheme().listFormattedStringToWidth(Component.literal(message), W - 24);
            setSize(W, 28 + lines.size() * 10 + 10 + 26);
        }

        @Override
        public void addWidgets() {
            yes = new Buttons.ChipButton(this, 78, "Confirm", accent, () -> {
                close();
                onYes.run();
            });
            no = new Buttons.ChipButton(this, 78, "Cancel", Ui.GRAY, this::close);
            add(yes);
            add(no);
        }

        @Override
        public void alignWidgets() {
            int y = height - 26;
            yes.setPos(width / 2 - 82, y);
            no.setPos(width / 2 + 4, y);
        }

        @Override
        public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            super.drawBackground(g, theme, x, y, w, h);
            int ty = y + 28;
            for (FormattedText line : lines) {
                Ui.text(g, theme, line, x + 12, ty, Ui.MUTED, false);
                ty += 10;
            }
        }
    }

    // ------------------------------------------------------------------ name input

    private static final class InputDialog extends BaseDialog {
        private final String hint;
        private final String button;
        private final String initial;
        private final Consumer<String> onAccept;
        private TextBox box;
        private Buttons.ChipButton ok;
        private Buttons.ChipButton cancel;

        InputDialog(BaseScreen gui, String title, String hint, Color4I accent, String button, String initial,
                    Consumer<String> onAccept) {
            super(gui, title, accent);
            this.hint = hint;
            this.button = button;
            this.initial = initial;
            this.onAccept = onAccept;
            setSize(W, 104);
        }

        @Override
        public void addWidgets() {
            box = new TextBox(this) {
                @Override
                public void onEnterPressed() {
                    submit();
                }
            };
            box.ghostText = hint;
            box.setMaxLength(24);
            box.setText(initial, false);
            add(box);
            ok = new Buttons.ChipButton(this, 78, button, accent, this::submit);
            add(ok);
            cancel = new Buttons.ChipButton(this, 78, "Cancel", Ui.GRAY, this::close);
            add(cancel);
        }

        @Override
        public void alignWidgets() {
            box.setPosAndSize(12, 30, width - 24, 16);
            ok.setPos(width / 2 - 82, height - 28);
            cancel.setPos(width / 2 + 4, height - 28);
        }

        private boolean valid() {
            return NAME.matcher(box.getText().trim()).matches();
        }

        private void submit() {
            if (!valid()) return;
            String value = box.getText().trim();
            close();
            onAccept.accept(value);
        }

        void focusInput() {
            box.setFocused(true);
        }

        @Override
        public void drawBackground(GuiGraphics g, Theme theme, int x, int y, int w, int h) {
            super.drawBackground(g, theme, x, y, w, h);
            ok.setEnabled(valid());
            String text = box.getText().trim();
            boolean bad = !text.isEmpty() && !valid();
            Ui.text(g, theme, bad ? "3-24 characters: letters, digits, spaces, ' _ -" : "Press Enter to confirm",
                    x + 12, y + 52, bad ? Ui.RED : Ui.MUTED, false);
        }
    }
}
