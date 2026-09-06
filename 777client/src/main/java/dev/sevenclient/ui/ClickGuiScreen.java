package dev.sevenclient.ui;

import dev.sevenclient.SevenClient;
import dev.sevenclient.module.Category;
import dev.sevenclient.module.Module;
import dev.sevenclient.module.setting.BoolSetting;
import dev.sevenclient.module.setting.KeySetting;
import dev.sevenclient.module.setting.ModeSetting;
import dev.sevenclient.module.setting.NumberSetting;
import dev.sevenclient.module.setting.Setting;
import dev.sevenclient.module.setting.StringSetting;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 777 Client interface.
 *
 * Sidebar card on the left, content pane on the right, everything rounded and
 * eased. All persistent state is static so reopening lands exactly where you
 * left off.
 *
 * On animation: every transition is driven by `1 - exp(-speed * dt)` against a
 * real frame delta, which is the same framerate-independent approach the aim
 * math uses. A per-frame constant like `cur += (target-cur) * 0.2` would run
 * visibly faster at 300fps than at 60.
 */
public class ClickGuiScreen extends Screen {

    private static final int W = 470;
    private static final int H = 302;
    private static final int SIDEBAR = 134;
    private static final int PAD = 10;

    private static final int CARD_H = 30;
    private static final int SET_H = 20;
    private static final int SLIDER_H = 26;
    private static final int NAV_H = 24;
    private static final float R = 8f;

    private enum View {
        COMBAT("Combat"), MOVEMENT("Movement"), PLAYER("Player"),
        RENDER("Render"), KEYBINDS("Keybinds"), CONFIG("Config");

        final String label;
        View(String label) { this.label = label; }
    }

    private static float winX = Float.NaN;
    private static float winY = Float.NaN;
    private static View view = View.COMBAT;
    private static final Set<String> expanded = new HashSet<>();
    private static final Map<View, Float> scrollTarget = new EnumMap<>(View.class);
    private static final Map<String, Float> anims = new HashMap<>();
    private static String search = "";

    private boolean dragging;
    private double dragDx, dragDy;
    private NumberSetting slider;
    private float trackX, trackW;
    private KeySetting listening;
    private StringSetting editing;
    private ModeSetting dropdown;
    private float ddX, ddY, ddW;
    private boolean searchFocused;

    private long lastNanos = 0L;
    private double mx, my;
    private String tip = "";

    public ClickGuiScreen() {
        super(Text.literal("777 Client"));
    }

    // ------------------------------------------------------------- animation

    private static float readAnim(String key, float fallback) {
        Float v = anims.get(key);
        return v == null ? fallback : v;
    }

    private static void stepAnim(String key, float target, float speed, float dt) {
        float cur = readAnim(key, target);
        cur += (target - cur) * (1f - (float) Math.exp(-speed * dt));
        if (Math.abs(target - cur) < 0.0015f) cur = target;
        anims.put(key, cur);
    }

    private float frameDelta() {
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 1f / 60f : (float) ((now - lastNanos) / 1_000_000_000.0d);
        lastNanos = now;
        return Math.max(1f / 1000f, Math.min(0.1f, dt));
    }

    private void stepAll(float dt) {
        for (Module m : SevenClient.get().modules.all()) {
            stepAnim("exp:" + m.name(), expanded.contains(m.name()) ? 1f : 0f, 15f, dt);
            stepAnim("on:" + m.name(), m.isEnabled() ? 1f : 0f, 18f, dt);
        }
        for (View v : View.values()) {
            stepAnim("nav:" + v.name(), v == view ? 1f : 0f, 16f, dt);
        }

        float max = Math.max(0f, contentHeight() - listH());
        float t = Math.max(0f, Math.min(max, scrollTarget.getOrDefault(view, 0f)));
        scrollTarget.put(view, t);
        stepAnim("scroll:" + view.name(), t, 17f, dt);
    }

    // -------------------------------------------------------------- geometry

    private float sbX() { return winX + PAD; }
    private float sbY() { return winY + PAD; }
    private float sbW() { return SIDEBAR - PAD - 6; }
    private float sbH() { return H - PAD * 2; }

    private float cX() { return winX + SIDEBAR + 2; }
    private float cW() { return W - SIDEBAR - PAD - 4; }
    private float listTop() { return winY + PAD + 46; }
    private float listBottom() { return winY + H - PAD; }
    private float listH() { return listBottom() - listTop(); }

    private static Category cat(View v) {
        return switch (v) {
            case COMBAT -> Category.COMBAT;
            case MOVEMENT -> Category.MOVEMENT;
            case PLAYER -> Category.PLAYER;
            case RENDER -> Category.RENDER;
            default -> null;
        };
    }

    private List<Module> visibleModules() {
        List<Module> out = new ArrayList<>();
        Category c = cat(view);
        if (c == null) return out;
        String q = search.toLowerCase(Locale.ROOT).trim();
        for (Module m : SevenClient.get().modules.byCategory(c)) {
            if (!q.isEmpty()
                    && !m.name().toLowerCase(Locale.ROOT).contains(q)
                    && !m.description().toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(m);
        }
        return out;
    }

    private float settingsHeight(Module m) {
        float h = 4f;
        for (Setting<?> s : m.settings()) {
            if (!s.visible()) continue;
            h += (s instanceof NumberSetting ? SLIDER_H : SET_H) + 2;
        }
        return h + 4f;
    }

    private float contentHeight() {
        float h = 0f;
        switch (view) {
            case KEYBINDS -> h = SevenClient.get().modules.all().size() * (SET_H + 4f);
            case CONFIG -> h = (26f + 26f + 32f) + 4 * 16f;
            default -> {
                for (Module m : visibleModules()) {
                    h += CARD_H + 4f;
                    float p = readAnim("exp:" + m.name(), expanded.contains(m.name()) ? 1f : 0f);
                    if (p > 0.002f) h += settingsHeight(m) * p + 4f;
                }
            }
        }
        return h + 6f;
    }

    // ------------------------------------------------------------------ rows

    private enum Kind { CARD, BOOL, NUMBER, MODE, KEY, STRING, BIND_ROW, BUTTON, INFO }

    private static final class Row {
        Kind kind;
        Module module;
        Setting<?> setting;
        float x, y, w, h;
        float blockY, blockH;
        String label = "";
        Runnable action;

        Row(Kind k, float x, float y, float w, float h) {
            this.kind = k; this.x = x; this.y = y; this.w = w; this.h = h;
        }

        /** Setting rows live inside an animated reveal block; this clips them. */
        boolean withinBlock() {
            return blockH <= 0f || (y + h <= blockY + blockH + 1f);
        }
    }

    private List<Row> layout() {
        List<Row> rows = new ArrayList<>();
        float x = cX();
        float w = cW() - 8f;
        float y = listTop() - readAnim("scroll:" + view.name(), 0f);

        if (view == View.KEYBINDS) {
            for (Module m : SevenClient.get().modules.all()) {
                Row r = new Row(Kind.BIND_ROW, x, y, w, SET_H);
                r.module = m;
                rows.add(r);
                y += SET_H + 4f;
            }
            return rows;
        }

        if (view == View.CONFIG) {
            rows.add(button(x, y, w, "Save config now", () -> {
                SevenClient.get().config.save();
                SevenClient.chat("\u00a77Config saved.");
            }));
            y += 26f;
            rows.add(button(x, y, w, "Reload config from disk", () -> {
                SevenClient.get().config.load();
                SevenClient.chat("\u00a77Config reloaded.");
            }));
            y += 26f;
            rows.add(button(x, y, w, "Disable all modules", () -> {
                for (Module m : SevenClient.get().modules.all()) {
                    if (m.isEnabled() && !m.name().equals("Debug")) m.setEnabled(false);
                }
            }));
            y += 32f;

            int on = 0;
            for (Module m : SevenClient.get().modules.all()) if (m.isEnabled()) on++;
            rows.add(info(x, y, w, "modules   " + SevenClient.get().modules.all().size()
                    + " total, " + on + " enabled")); y += 16f;
            rows.add(info(x, y, w, "font      " + (UiFont.usingCustomFont()
                    ? "custom TTF" : "vanilla (drop Inter-Medium.ttf in assets)"))); y += 16f;
            rows.add(info(x, y, w, "config    " + SevenClient.get().config.path().getFileName())); y += 16f;
            rows.add(info(x, y, w, "path      " + shorten(SevenClient.get().config.path().toString(), (int) w - 70)));
            return rows;
        }

        for (Module m : visibleModules()) {
            Row card = new Row(Kind.CARD, x, y, w, CARD_H);
            card.module = m;
            rows.add(card);
            y += CARD_H + 4f;

            float p = readAnim("exp:" + m.name(), expanded.contains(m.name()) ? 1f : 0f);
            if (p > 0.002f) {
                float shown = settingsHeight(m) * p;
                float blockY = y;
                float sy = blockY + 4f;
                for (Setting<?> s : m.settings()) {
                    if (!s.visible()) continue;
                    float h = s instanceof NumberSetting ? SLIDER_H : SET_H;
                    Kind k = s instanceof BoolSetting ? Kind.BOOL
                            : s instanceof NumberSetting ? Kind.NUMBER
                            : s instanceof KeySetting ? Kind.KEY
                            : s instanceof ModeSetting ? Kind.MODE
                            : s instanceof StringSetting ? Kind.STRING : null;
                    if (k == null) continue;

                    Row r = new Row(k, x + 10f, sy, w - 12f, h);
                    r.module = m;
                    r.setting = s;
                    r.blockY = blockY;
                    r.blockH = shown;
                    rows.add(r);
                    sy += h + 2f;
                }
                y += shown + 4f;
            }
        }
        return rows;
    }

    private Row button(float x, float y, float w, String label, Runnable action) {
        Row r = new Row(Kind.BUTTON, x, y, w, 22f);
        r.label = label;
        r.action = action;
        return r;
    }

    private Row info(float x, float y, float w, String label) {
        Row r = new Row(Kind.INFO, x, y, w, 14f);
        r.label = label;
        return r;
    }

    private static String shorten(String s, int maxWidth) {
        if (UiFont.width(s) <= maxWidth) return s;
        String out = s;
        while (out.length() > 4 && UiFont.width("..." + out) > maxWidth) {
            out = out.substring(1);
        }
        return "..." + out;
    }

    // ---------------------------------------------------------------- render

    @Override
    protected void init() {
        if (Float.isNaN(winX)) {
            winX = (this.width - W) / 2f;
            winY = (this.height - H) / 2f;
        }
        winX = Math.max(0, Math.min(Math.max(0, this.width - W), winX));
        winY = Math.max(0, Math.min(Math.max(0, this.height - H), winY));
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, Theme.SHADE);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float dt = frameDelta();
        this.mx = mouseX;
        this.my = mouseY;
        this.tip = "";

        super.render(ctx, mouseX, mouseY, delta);
        stepAll(dt);

        Render2D.shadow(ctx, winX, winY, W, H, R, 7, Theme.SHADOW);
        Render2D.roundedGradient(ctx, winX, winY, W, H, R, Theme.WINDOW, Theme.WINDOW_GRAD);
        Render2D.roundedBorder(ctx, winX, winY, W, H, R, Theme.OUTLINE);
        Render2D.rect(ctx, winX + R, winY, W - R * 2f, 1f, Theme.HIGHLIGHT);
        Render2D.innerTopLight(ctx, winX, winY, W, R, Theme.INNER_LIGHT);

        renderSidebar(ctx, dt);
        renderHeader(ctx);

        ctx.enableScissor((int) cX(), (int) listTop(), (int) (cX() + cW()), (int) listBottom());
        for (Row row : layout()) {
            if (row.y + row.h < listTop() - 8 || row.y > listBottom() + 8) continue;
            renderRow(ctx, row, dt);
        }
        ctx.disableScissor();

        renderScrollbar(ctx);

        if (!tip.isEmpty()) {
            UiFont.draw(ctx, UiFont.trim(tip, (int) cW() - 10), cX(), winY + H - PAD + 1, Theme.TEXT_FAINT);
        }

        if (dropdown != null) renderDropdown(ctx);
    }

    private void renderSidebar(DrawContext ctx, float dt) {
        Render2D.roundedGradient(ctx, sbX(), sbY(), sbW(), sbH(), 7f,
                Theme.SIDEBAR, Theme.SIDEBAR_GRAD);
        Render2D.roundedBorder(ctx, sbX(), sbY(), sbW(), sbH(), 7f, Theme.OUTLINE_SOFT);
        Render2D.innerTopLight(ctx, sbX(), sbY(), sbW(), 7f, Theme.INNER_LIGHT);

        // brand
        float bx = sbX() + 11f;
        float by = sbY() + 12f;
        Render2D.roundedGradient(ctx, bx, by, 15f, 15f, 4f, Theme.NAV_ACTIVE, Theme.PANEL_GRAD);
        Render2D.roundedBorder(ctx, bx, by, 15f, 15f, 4f, Theme.OUTLINE_SOFT);
        Icons.mark(ctx, bx, by, 15f, Theme.ACCENT_HI);

        UiFont.drawBold(ctx, "777", bx + 21f, by + 1f, Theme.TEXT);
        UiFont.draw(ctx, "Client", bx + 21f + UiFont.widthBold("777 "), by + 1f, Theme.TEXT_FAINT);
        UiFont.draw(ctx, "B E T A", bx + 21f, by + 11f, Theme.TEXT_FAINT);

        float y = sbY() + 46f;
        UiFont.draw(ctx, "MODULES", sbX() + 12f, y, Theme.TEXT_FAINT);
        y += 13f;

        for (View v : new View[] { View.COMBAT, View.MOVEMENT, View.PLAYER, View.RENDER }) {
            renderNav(ctx, v, y, true);
            y += NAV_H + 2f;
        }

        y += 8f;
        Render2D.rect(ctx, sbX() + 12f, y, sbW() - 24f, 1f, Theme.alpha(Theme.OUTLINE, 0.8f));
        y += 10f;
        UiFont.draw(ctx, "GENERAL", sbX() + 12f, y, Theme.TEXT_FAINT);
        y += 13f;

        for (View v : new View[] { View.KEYBINDS, View.CONFIG }) {
            renderNav(ctx, v, y, false);
            y += NAV_H + 2f;
        }
    }

    private void renderNav(DrawContext ctx, View v, float y, boolean showCount) {
        float x = sbX() + 7f;
        float w = sbW() - 14f;
        boolean hov = Render2D.hovered(mx, my, x, y, w, NAV_H);
        float sel = readAnim("nav:" + v.name(), v == view ? 1f : 0f);

        if (sel > 0.01f || hov) {
            int t0 = Theme.mix(Theme.alpha(Theme.PANEL_HOVER, hov ? 0.55f : 0f), Theme.NAV_ACTIVE, sel);
            int t1 = Theme.mix(Theme.alpha(Theme.PANEL_HOVER_GRAD, hov ? 0.55f : 0f),
                    Theme.NAV_ACTIVE_GRAD, sel);
            Render2D.roundedGradient(ctx, x, y, w, NAV_H, 5f, t0, t1);
        }
        if (sel > 0.01f) {
            Render2D.roundedBorder(ctx, x, y, w, NAV_H, 5f, Theme.alpha(Theme.ACCENT_SOFT, sel));
            // white left indicator: in monochrome this is what marks the active
            // row, since a hue shift is no longer available to do it
            float barH = (NAV_H - 12f) * sel;
            Render2D.roundedRect(ctx, x + 1f, y + (NAV_H - barH) / 2f, 2f, barH, 1f,
                    Theme.alpha(Theme.ACCENT, sel));
        }

        int iconColor = Theme.mix(Theme.TEXT_FAINT, Theme.ACCENT_HI, sel);
        int textColor = Theme.mix(Theme.TEXT_DIM, Theme.TEXT, sel);
        float ix = x + 8f;
        float iy = y + (NAV_H - 12f) / 2f;

        switch (v) {
            case COMBAT -> Icons.combat(ctx, ix, iy, 12f, iconColor);
            case MOVEMENT -> Icons.movement(ctx, ix, iy, 12f, iconColor);
            case PLAYER -> Icons.player(ctx, ix, iy, 12f, iconColor);
            case RENDER -> Icons.render(ctx, ix, iy, 12f, iconColor);
            case KEYBINDS -> Icons.keyboard(ctx, ix, iy, 12f, iconColor);
            case CONFIG -> Icons.sliders(ctx, ix, iy, 12f, iconColor);
        }

        UiFont.draw(ctx, v.label, x + 26f, y + (NAV_H - UiFont.height()) / 2f, textColor);

        if (showCount) {
            Category c = cat(v);
            if (c != null) {
                String n = String.valueOf(SevenClient.get().modules.byCategory(c).size());
                UiFont.drawRight(ctx, n, x + w - 9f, y + (NAV_H - UiFont.height()) / 2f, Theme.TEXT_FAINT);
            }
        }
    }

    private void renderHeader(DrawContext ctx) {
        float x = cX();
        float y = winY + PAD + 6f;

        UiFont.drawBold(ctx, view.label, x, y, Theme.TEXT);
        float titleW = UiFont.widthBold(view.label + " ");
        String suffix = (cat(view) != null) ? "Modules" : "";
        if (!suffix.isEmpty()) UiFont.drawBold(ctx, suffix, x + titleW, y, Theme.TEXT_FAINT);

        String sub;
        if (cat(view) != null) {
            List<Module> all = SevenClient.get().modules.byCategory(cat(view));
            int on = 0;
            for (Module m : all) if (m.isEnabled()) on++;
            sub = all.size() + " modules \u00b7 " + on + " enabled";
            if (!search.isEmpty()) sub += " \u00b7 " + visibleModules().size() + " shown";
        } else if (view == View.KEYBINDS) {
            sub = "click a bind to rebind, right click to clear";
        } else {
            sub = "save, reload, and client status";
        }
        UiFont.draw(ctx, sub, x, y + 13f, Theme.TEXT_DIM);

        if (cat(view) != null) renderSearch(ctx);
    }

    private float searchX() { return cX() + cW() - 124f; }
    private float searchY() { return winY + PAD + 4f; }
    private static final float SEARCH_W = 124f;
    private static final float SEARCH_H = 20f;

    private void renderSearch(DrawContext ctx) {
        float x = searchX(), y = searchY();
        boolean hov = Render2D.hovered(mx, my, x, y, SEARCH_W, SEARCH_H);

        Render2D.roundedGradient(ctx, x, y, SEARCH_W, SEARCH_H, 6f,
                searchFocused ? Theme.PANEL_HOVER : Theme.PANEL,
                searchFocused ? Theme.PANEL_HOVER_GRAD : Theme.PANEL_GRAD);
        Render2D.roundedBorder(ctx, x, y, SEARCH_W, SEARCH_H, 6f,
                searchFocused ? Theme.ACCENT_SOFT : (hov ? Theme.OUTLINE : Theme.OUTLINE_SOFT));
        Render2D.innerTopLight(ctx, x, y, SEARCH_W, 6f, Theme.alpha(Theme.INNER_LIGHT, 0.6f));

        Render2D.magnifier(ctx, x + 11f, y + SEARCH_H / 2f,
                searchFocused ? Theme.ACCENT : Theme.TEXT_FAINT);

        String shown = search.isEmpty() && !searchFocused ? "Search modules" : search;
        int color = search.isEmpty() && !searchFocused ? Theme.TEXT_FAINT : Theme.TEXT;
        UiFont.draw(ctx, UiFont.trim(shown, (int) SEARCH_W - 30), x + 20f,
                y + (SEARCH_H - UiFont.height()) / 2f, color);

        if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            float cx = x + 20f + UiFont.width(search) + 1f;
            Render2D.rect(ctx, Math.min(cx, x + SEARCH_W - 6f), y + 5f, 1f, SEARCH_H - 10f, Theme.ACCENT);
        }
    }

    private void renderRow(DrawContext ctx, Row row, float dt) {
        boolean hov = Render2D.hovered(mx, my, row.x, row.y, row.w, row.h)
                && my >= listTop() && my <= listBottom() && row.withinBlock();

        switch (row.kind) {
            case CARD -> {
                Module m = row.module;
                stepAnim("hov:" + m.name(), hov ? 1f : 0f, 14f, dt);
                float hp = readAnim("hov:" + m.name(), 0f);
                float on = readAnim("on:" + m.name(), m.isEnabled() ? 1f : 0f);

                Render2D.roundedGradient(ctx, row.x, row.y, row.w, row.h, 6f,
                        Theme.mix(Theme.PANEL, Theme.PANEL_HOVER, hp),
                        Theme.mix(Theme.PANEL_GRAD, Theme.PANEL_HOVER_GRAD, hp));
                Render2D.roundedBorder(ctx, row.x, row.y, row.w, row.h, 6f,
                        Theme.mix(Theme.OUTLINE_SOFT, Theme.ACCENT_SOFT, Math.max(on * 0.9f, hp * 0.5f)));
                Render2D.innerTopLight(ctx, row.x, row.y, row.w, 6f,
                        Theme.alpha(Theme.INNER_LIGHT, 0.45f + 0.55f * Math.max(on, hp)));

                UiFont.drawBold(ctx, m.name(), row.x + 12f, row.y + 6f,
                        Theme.mix(Theme.TEXT_DIM, Theme.TEXT, Math.max(on, hp)));
                UiFont.draw(ctx, UiFont.trim(m.description(), (int) row.w - 100), row.x + 12f,
                        row.y + 17f, Theme.TEXT_FAINT);

                float ex = readAnim("exp:" + m.name(), 0f);
                Render2D.chevron(ctx, row.x + row.w - 47f, row.y + row.h / 2f - 1f, 3.5f, ex,
                        Theme.mix(Theme.TEXT_FAINT, Theme.TEXT_DIM, hp));

                drawSwitch(ctx, row.x + row.w - 32f, row.y + (row.h - 12f) / 2f, on);
            }
            case BOOL -> {
                BoolSetting s = (BoolSetting) row.setting;
                if (hov) Render2D.roundedRect(ctx, row.x, row.y, row.w, row.h, 4f,
                        Theme.alpha(Theme.PANEL, 0.55f));
                UiFont.draw(ctx, s.name(), row.x + 10f, row.y + (row.h - UiFont.height()) / 2f,
                        s.is() ? Theme.TEXT : Theme.TEXT_DIM);
                stepAnim("b:" + row.module.name() + s.name(), s.is() ? 1f : 0f, 18f, dt);
                drawSwitch(ctx, row.x + row.w - 30f, row.y + (row.h - 12f) / 2f,
                        readAnim("b:" + row.module.name() + s.name(), 0f));
            }
            case NUMBER -> {
                NumberSetting s = (NumberSetting) row.setting;
                if (hov) Render2D.roundedRect(ctx, row.x, row.y, row.w, row.h, 4f,
                        Theme.alpha(Theme.PANEL, 0.55f));

                UiFont.draw(ctx, s.name(), row.x + 10f, row.y + 3f, Theme.TEXT_DIM);
                UiFont.drawRightBold(ctx, fmt(s.val()), row.x + row.w - 10f, row.y + 3f, Theme.TEXT);

                float tx = row.x + 10f;
                float tw = row.w - 20f;
                float ty = row.y + row.h - 9f;
                Render2D.roundedGradient(ctx, tx, ty, tw, 3f, 1.5f, Theme.TRACK, Theme.TRACK_GRAD);
                float fill = (float) (tw * s.normalized());
                if (fill > 0.5f) {
                    Render2D.roundedGradient(ctx, tx, ty, fill, 3f, 1.5f,
                            Theme.ACCENT, Theme.ACCENT_GRAD);
                }

                boolean active = slider == s;
                float kr = active ? 4.8f : 4.0f;
                if (active) Render2D.disc(ctx, tx + fill, ty + 1.5f, 7f, Theme.ACCENT_GLOW);
                Render2D.discGradient(ctx, tx + fill, ty + 1.5f, kr, Theme.ACCENT, Theme.ACCENT_GRAD);
                Render2D.disc(ctx, tx + fill, ty + 1.5f, kr - 1.6f, Theme.KNOB);
            }
            case MODE -> {
                ModeSetting s = (ModeSetting) row.setting;
                if (hov) Render2D.roundedRect(ctx, row.x, row.y, row.w, row.h, 4f,
                        Theme.alpha(Theme.PANEL, 0.55f));
                UiFont.draw(ctx, s.name(), row.x + 10f, row.y + (row.h - UiFont.height()) / 2f,
                        Theme.TEXT_DIM);

                float cw = UiFont.width(s.get()) + 24f;
                float cx = row.x + row.w - 10f - cw;
                boolean chov = Render2D.hovered(mx, my, cx, row.y + 2f, cw, row.h - 4f) && row.withinBlock();
                Render2D.roundedRect(ctx, cx, row.y + 2f, cw, row.h - 4f, 4f,
                        chov ? Theme.PANEL_HOVER : Theme.PANEL);
                Render2D.roundedBorder(ctx, cx, row.y + 2f, cw, row.h - 4f, 4f,
                        Theme.alpha(Theme.OUTLINE, 0.7f));
                UiFont.draw(ctx, s.get(), cx + 8f, row.y + (row.h - UiFont.height()) / 2f, Theme.TEXT);
                Render2D.chevron(ctx, cx + cw - 8f, row.y + row.h / 2f - 1f, 2.8f,
                        dropdown == s ? 1f : 0f, Theme.TEXT_DIM);
            }
            case KEY -> {
                KeySetting s = (KeySetting) row.setting;
                if (hov) Render2D.roundedRect(ctx, row.x, row.y, row.w, row.h, 4f,
                        Theme.alpha(Theme.PANEL, 0.55f));
                UiFont.draw(ctx, s.name(), row.x + 10f, row.y + (row.h - UiFont.height()) / 2f,
                        Theme.TEXT_DIM);
                drawBindChip(ctx, s, row.x + row.w - 10f, row.y, row.h);
                if (hov) tip = "left click to rebind  \u00b7  right click to clear";
            }
            case STRING -> {
                StringSetting s = (StringSetting) row.setting;
                if (hov) Render2D.roundedRect(ctx, row.x, row.y, row.w, row.h, 4f,
                        Theme.alpha(Theme.PANEL, 0.55f));
                UiFont.draw(ctx, s.name(), row.x + 10f, row.y + (row.h - UiFont.height()) / 2f,
                        Theme.TEXT_DIM);

                float cw = Math.min(row.w * 0.5f, Math.max(70f, UiFont.width(s.get()) + 20f));
                float cx = row.x + row.w - 10f - cw;
                Render2D.roundedRect(ctx, cx, row.y + 2f, cw, row.h - 4f, 4f, Theme.PANEL);
                Render2D.roundedBorder(ctx, cx, row.y + 2f, cw, row.h - 4f, 4f,
                        editing == s ? Theme.ACCENT_SOFT : Theme.alpha(Theme.OUTLINE, 0.7f));
                String shown = s.get() + (editing == s && (System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "");
                UiFont.draw(ctx, UiFont.trim(shown, (int) cw - 14), cx + 7f,
                        row.y + (row.h - UiFont.height()) / 2f, Theme.TEXT);
                if (hov) tip = "comma separated  \u00b7  Enter to confirm";
            }
            case BIND_ROW -> {
                Module m = row.module;
                if (hov) Render2D.roundedRect(ctx, row.x, row.y, row.w, row.h, 4f,
                        Theme.alpha(Theme.PANEL, 0.55f));
                UiFont.draw(ctx, m.name(), row.x + 10f, row.y + (row.h - UiFont.height()) / 2f,
                        m.isEnabled() ? Theme.TEXT : Theme.TEXT_DIM);

                String mode = m.bindMode.get();
                float mw = UiFont.width(mode) + 16f;
                float bx = drawBindChip(ctx, m.bind, row.x + row.w - 10f, row.y, row.h);
                float mxp = bx - 6f - mw;
                Render2D.roundedRect(ctx, mxp, row.y + 3f, mw, row.h - 6f, 4f, Theme.PANEL);
                UiFont.draw(ctx, mode, mxp + 8f, row.y + (row.h - UiFont.height()) / 2f, Theme.TEXT_DIM);
                if (hov) tip = "click bind to rebind  \u00b7  click mode to switch Toggle/Hold";
            }
            case BUTTON -> {
                Render2D.roundedRect(ctx, row.x, row.y, row.w, row.h, 5f,
                        hov ? Theme.PANEL_HOVER : Theme.PANEL);
                Render2D.roundedBorder(ctx, row.x, row.y, row.w, row.h, 5f,
                        hov ? Theme.ACCENT_SOFT : Theme.alpha(Theme.OUTLINE, 0.7f));
                UiFont.draw(ctx, row.label, row.x + 12f, row.y + (row.h - UiFont.height()) / 2f,
                        hov ? Theme.TEXT : Theme.TEXT_DIM);
            }
            case INFO -> UiFont.draw(ctx, row.label, row.x + 2f,
                    row.y + (row.h - UiFont.height()) / 2f, Theme.TEXT_FAINT);
        }
    }

    /** Returns the chip's left edge so callers can stack another chip beside it. */
    private float drawBindChip(DrawContext ctx, KeySetting s, float rightX, float rowY, float rowH) {
        boolean isListening = listening == s;
        String label = isListening ? "..." : s.label();
        float cw = Math.max(34f, UiFont.width(label) + 18f);
        float cx = rightX - cw;
        boolean hov = Render2D.hovered(mx, my, cx, rowY + 2f, cw, rowH - 4f);

        Render2D.roundedRect(ctx, cx, rowY + 3f, cw, rowH - 6f, 4f,
                isListening ? Theme.ACCENT_SOFT : (hov ? Theme.PANEL_HOVER : Theme.PANEL));
        Render2D.roundedBorder(ctx, cx, rowY + 3f, cw, rowH - 6f, 4f,
                isListening ? Theme.ACCENT : Theme.alpha(Theme.OUTLINE, 0.7f));
        UiFont.drawCentered(ctx, label, cx + cw / 2f, rowY + (rowH - UiFont.height()) / 2f,
                isListening ? Theme.TEXT : (s.isBound() ? Theme.TEXT : Theme.TEXT_FAINT));
        return cx;
    }

    private void drawSwitch(DrawContext ctx, float x, float y, float p) {
        float w = 22f, h = 12f;
        Render2D.roundedGradient(ctx, x, y, w, h, h / 2f,
                Theme.mix(Theme.OFF, Theme.ON, p), Theme.mix(Theme.OFF_GRAD, Theme.ON_GRAD, p));
        Render2D.roundedBorder(ctx, x, y, w, h, h / 2f,
                Theme.mix(Theme.OUTLINE_SOFT, Theme.alpha(Theme.ACCENT, 0.35f), p));
        if (p > 0.02f) {
            Render2D.roundedRect(ctx, x - 1f, y - 1f, w + 2f, h + 2f, h / 2f + 1f,
                    Theme.alpha(Theme.ACCENT_GLOW, p));
        }
        float knobX = x + 1.5f + (w - h) * p;
        float kr = (h - 3f) / 2f;
        Render2D.discGradient(ctx, knobX + kr, y + h / 2f, kr,
                Theme.mix(Theme.TEXT_FAINT, Theme.KNOB, p),
                Theme.mix(Theme.OUTLINE, Theme.KNOB, p));
    }

    private void renderScrollbar(DrawContext ctx) {
        float total = contentHeight();
        float view_ = listH();
        if (total <= view_) return;

        float x = cX() + cW() - 3f;
        float ratio = view_ / total;
        float barH = Math.max(22f, view_ * ratio);
        float off = readAnim("scroll:" + view.name(), 0f) / (total - view_);
        float barY = listTop() + Math.max(0f, Math.min(1f, off)) * (view_ - barH);

        Render2D.roundedRect(ctx, x, listTop(), 2f, view_, 1f, Theme.alpha(Theme.OUTLINE, 0.7f));
        Render2D.roundedRect(ctx, x, barY, 2f, barH, 1f, Theme.ACCENT_SOFT);
    }

    private void renderDropdown(DrawContext ctx) {
        List<String> opts = dropdown.options();
        float h = opts.size() * 17f + 6f;
        Render2D.shadow(ctx, ddX, ddY, ddW, h, 5f, 6, Theme.SHADOW);
        Render2D.roundedRect(ctx, ddX, ddY, ddW, h, 5f, Theme.HEADER);
        Render2D.roundedBorder(ctx, ddX, ddY, ddW, h, 5f, Theme.ACCENT_SOFT);

        float y = ddY + 3f;
        for (String o : opts) {
            boolean hov = Render2D.hovered(mx, my, ddX + 2f, y, ddW - 4f, 17f);
            boolean sel = dropdown.get().equals(o);
            if (hov) Render2D.roundedRect(ctx, ddX + 2f, y, ddW - 4f, 17f, 3f, Theme.PANEL_HOVER);
            if (sel) Render2D.roundedRect(ctx, ddX + 4f, y + 6f, 3f, 3f, 1.5f, Theme.ACCENT);
            UiFont.draw(ctx, o, ddX + 12f, y + (17f - UiFont.height()) / 2f,
                    sel ? Theme.TEXT : Theme.TEXT_DIM);
            y += 17f;
        }
    }

    private static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-6) return String.valueOf((long) Math.rint(v));
        return String.format("%.2f", v);
    }

    // ----------------------------------------------------------------- input

    /*
     * 1.21.9+ replaced the primitive input parameters on Element with records:
     *   mouseClicked(Click, boolean) / mouseDragged(Click, double, double)
     *   mouseReleased(Click) / keyPressed(KeyInput) / charTyped(CharInput)
     * mouseScrolled(double, double, double, double) was left alone.
     * Unpacked into locals here so the logic below reads the same as before.
     */
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mxx = click.x();
        double myy = click.y();
        int button = click.button();

        if (dropdown != null) {
            List<String> opts = dropdown.options();
            float y = ddY + 3f;
            for (String o : opts) {
                if (Render2D.hovered(mxx, myy, ddX + 2f, y, ddW - 4f, 17f)) {
                    dropdown.set(o);
                    dropdown = null;
                    return true;
                }
                y += 17f;
            }
            dropdown = null;
            return true;
        }

        if (listening != null) {
            listening.bind(button, true);
            listening = null;
            return true;
        }

        boolean inSearch = cat(view) != null
                && Render2D.hovered(mxx, myy, searchX(), searchY(), SEARCH_W, SEARCH_H);
        searchFocused = inSearch;
        if (inSearch) {
            if (button == 1) { search = ""; scrollTarget.put(view, 0f); }
            return true;
        }
        if (editing != null) editing = null;

        // nav
        float y = sbY() + 59f;
        for (View v : new View[] { View.COMBAT, View.MOVEMENT, View.PLAYER, View.RENDER }) {
            if (Render2D.hovered(mxx, myy, sbX() + 7f, y, sbW() - 14f, NAV_H)) { view = v; return true; }
            y += NAV_H + 2f;
        }
        y += 18f + 13f;
        for (View v : new View[] { View.KEYBINDS, View.CONFIG }) {
            if (Render2D.hovered(mxx, myy, sbX() + 7f, y, sbW() - 14f, NAV_H)) { view = v; return true; }
            y += NAV_H + 2f;
        }

        if (myy >= listTop() && myy <= listBottom()) {
            for (Row row : layout()) {
                if (!row.withinBlock()) continue;
                if (!Render2D.hovered(mxx, myy, row.x, row.y, row.w, row.h)) continue;

                switch (row.kind) {
                    case CARD -> {
                        Module m = row.module;
                        boolean onSwitch = mxx >= row.x + row.w - 36f;
                        if (button == 1 || onSwitch) m.toggle();
                        else if (!expanded.remove(m.name())) expanded.add(m.name());
                    }
                    case BOOL -> ((BoolSetting) row.setting).toggle();
                    case NUMBER -> {
                        slider = (NumberSetting) row.setting;
                        trackX = row.x + 10f;
                        trackW = row.w - 20f;
                        slider.setNormalized((mxx - trackX) / trackW);
                    }
                    case MODE -> {
                        ModeSetting s = (ModeSetting) row.setting;
                        if (button == 1) { s.cycle(1); return true; }
                        dropdown = s;
                        ddW = Math.max(88f, longest(s) + 26f);
                        ddX = row.x + row.w - 10f - ddW;
                        ddY = row.y + row.h;
                        float dh = s.options().size() * 17f + 6f;
                        if (ddY + dh > listBottom()) ddY = row.y - dh;
                    }
                    case KEY -> {
                        KeySetting s = (KeySetting) row.setting;
                        if (button == 1) s.clear(); else listening = s;
                    }
                    case STRING -> editing = (StringSetting) row.setting;
                    case BIND_ROW -> {
                        Module m = row.module;
                        String mode = m.bindMode.get();
                        float mw = UiFont.width(mode) + 16f;
                        float chipLeft = row.x + row.w - 10f
                                - Math.max(34f, UiFont.width(m.bind.label()) + 18f);
                        if (mxx >= chipLeft) {
                            if (button == 1) m.bind.clear(); else listening = m.bind;
                        } else if (mxx >= chipLeft - 6f - mw) {
                            m.bindMode.cycle(1);
                        }
                    }
                    case BUTTON -> {
                        if (row.action != null) row.action.run();
                    }
                    case INFO -> { }
                }
                return true;
            }
        }

        // drag from the top strip, but never from the search field
        if (Render2D.hovered(mxx, myy, winX, winY, W, PAD + 40f) && button == 0) {
            dragging = true;
            dragDx = mxx - winX;
            dragDy = myy - winY;
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    private static float longest(ModeSetting s) {
        int max = 0;
        for (String o : s.options()) max = Math.max(max, UiFont.width(o));
        return max;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        double mxx = click.x();
        double myy = click.y();

        if (dragging) {
            winX = (float) Math.max(0, Math.min(Math.max(0, this.width - W), mxx - dragDx));
            winY = (float) Math.max(0, Math.min(Math.max(0, this.height - H), myy - dragDy));
            return true;
        }
        if (slider != null) {
            slider.setNormalized((mxx - trackX) / trackW);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = false;
        slider = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mxx, double myy, double horizontal, double vertical) {
        float max = Math.max(0f, contentHeight() - listH());
        float cur = scrollTarget.getOrDefault(view, 0f);
        scrollTarget.put(view, Math.max(0f, Math.min(max, cur - (float) vertical * 30f)));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();

        if (listening != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) listening.clear();
            else listening.bind(keyCode, false);
            listening = null;
            return true;
        }
        if (editing != null) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> editing.backspace();
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_ESCAPE -> editing = null;
                default -> { }
            }
            return true;
        }
        if (searchFocused) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
                    scrollTarget.put(view, 0f);
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    if (search.isEmpty()) searchFocused = false;
                    else { search = ""; scrollTarget.put(view, 0f); }
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> searchFocused = false;
                default -> { }
            }
            return true;
        }
        if (dropdown != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            dropdown = null;
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        int cp = input.codepoint();
        // guard the BMP range as well: append(char) can't represent astral codepoints
        if (cp < 32 || cp == 127 || cp > 0xFFFF) return super.charTyped(input);
        char chr = (char) cp;

        if (editing != null) {
            editing.append(chr);
            return true;
        }
        if (searchFocused) {
            if (search.length() < 32) search += chr;
            scrollTarget.put(view, 0f);
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void close() {
        SevenClient.get().config.save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
