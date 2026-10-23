package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

/** Small GL helpers for the custom officer portraits. Each call sets the GL state it needs and leaves textures enabled. */
public class rsg_Draw {

    public static void quad(float x, float y, float w, float h, Color c, float alpha, boolean additive) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, additive ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA);
        setColor(c, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    public static void border(float x, float y, float w, float h, Color c, float alpha) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        setColor(c, alpha);
        GL11.glLineWidth(1f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x + 0.5f, y + 0.5f);
        GL11.glVertex2f(x + 0.5f, y + h - 0.5f);
        GL11.glVertex2f(x + w - 0.5f, y + h - 0.5f);
        GL11.glVertex2f(x + w - 0.5f, y + 0.5f);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    public static void portrait(String spriteName, float x, float y, float w, float h, float alpha) {
        portrait(spriteName, x, y, w, h, alpha, null);
    }

    /** Draws a portrait, multiplied by tint if given (vanilla tints combat portraits with the ship's side color). */
    public static void portrait(String spriteName, float x, float y, float w, float h, float alpha, Color tint) {
        if (spriteName == null) return;
        SpriteAPI sprite = Global.getSettings().getSprite(spriteName);
        if (sprite == null) return;
        sprite.setNormalBlend();
        sprite.setColor(tint != null ? tint : Color.white);
        sprite.setSize(w, h);
        sprite.setAlphaMult(alpha);
        sprite.render(x, y);
    }

    /**
     * Vanilla draws an empty officer seat as the grayscale default portrait tinted with the player faction's base UI
     * color (com.fs.starfarer.campaign.ui.oo0O on 0.98a-RC8, a translation of obfuscated code).
     */
    public static String getEmptySeatSprite() {
        return Global.getSettings().getSpriteName("misc", "default_portrait");
    }

    public static Color getEmptySeatTint() {
        return Global.getSector().getPlayerFaction().getBaseUIColor();
    }

    /** The portrait drawn with additive blending, as vanilla does for its hover glow. */
    public static void portraitAdditive(String spriteName, float x, float y, float w, float h, float alpha) {
        portraitAdditive(spriteName, x, y, w, h, alpha, null);
    }

    public static void portraitAdditive(String spriteName, float x, float y, float w, float h, float alpha, Color tint) {
        if (spriteName == null) return;
        SpriteAPI sprite = Global.getSettings().getSprite(spriteName);
        if (sprite == null) return;
        sprite.setAdditiveBlend();
        sprite.setColor(tint != null ? tint : Color.white);
        sprite.setSize(w, h);
        sprite.setAlphaMult(alpha);
        sprite.render(x, y);
        sprite.setNormalBlend();
    }

    public static Color lerp(Color from, Color to, float f) {
        f = Math.max(0f, Math.min(1f, f));
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * f),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * f),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * f),
                Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * f));
    }

    private static void setColor(Color c, float alpha) {
        GL11.glColor4ub((byte) c.getRed(), (byte) c.getGreen(), (byte) c.getBlue(),
                (byte) Math.round(c.getAlpha() * Math.max(0f, Math.min(1f, alpha))));
    }
}
