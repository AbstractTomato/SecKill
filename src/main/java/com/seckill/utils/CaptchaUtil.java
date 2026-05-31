package com.seckill.utils;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * 验证码工具类 —— 生成 6 位随机码 + base64 图片
 */
public final class CaptchaUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char[] DIGITS = "0123456789".toCharArray();
    private static final char[] ALL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private CaptchaUtil() {
    }

    /**
     * 生成 6 位随机验证码，至少包含 2 个数字，区分大小写
     */
    public static String generateCode() {
        List<Character> chars = new ArrayList<>(6);
        // 强制至少 2 个数字
        chars.add(randomChar(DIGITS));
        chars.add(randomChar(DIGITS));

        // 剩余位从全体字符或纯字母中随机取
        while (chars.size() < 6) {
            chars.add(randomChar(RANDOM.nextBoolean() ? ALL_CHARS : LETTERS));
        }

        Collections.shuffle(chars, RANDOM);
        StringBuilder builder = new StringBuilder(6);
        for (Character ch : chars) {
            builder.append(ch);
        }
        return builder.toString();
    }

    /**
     * 生成验证码图片，返回 base64 data URI
     */
    public static String createImageBase64(String code) {
        int width = 160;
        int height = 56;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(245, 248, 252));
            graphics.fillRect(0, 0, width, height);

            drawNoiseLines(graphics, width, height);
            drawCode(graphics, code, width, height);
            drawNoiseDots(graphics, width, height);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create captcha image", e);
        } finally {
            graphics.dispose();
        }
    }

    /** 逐字绘制，每个字符随机旋转 ±15° */
    private static void drawCode(Graphics2D graphics, String code, int width, int height) {
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        FontMetrics metrics = graphics.getFontMetrics();
        int charWidth = width / code.length();
        int baseline = (height + metrics.getAscent() - metrics.getDescent()) / 2 - 2;

        for (int i = 0; i < code.length(); i++) {
            double angle = Math.toRadians(RANDOM.nextInt(31) - 15);
            int x = i * charWidth + 10 + RANDOM.nextInt(6);
            int y = baseline + RANDOM.nextInt(7) - 3;

            graphics.rotate(angle, x + charWidth / 2.0, y);
            graphics.setColor(randomDarkColor());
            graphics.drawString(String.valueOf(code.charAt(i)), x, y);
            graphics.rotate(-angle, x + charWidth / 2.0, y);
        }
    }

    private static void drawNoiseLines(Graphics2D graphics, int width, int height) {
        graphics.setStroke(new BasicStroke(1.4F));
        for (int i = 0; i < 8; i++) {
            graphics.setColor(randomLightColor());
            int x1 = RANDOM.nextInt(width);
            int y1 = RANDOM.nextInt(height);
            int x2 = RANDOM.nextInt(width);
            int y2 = RANDOM.nextInt(height);
            graphics.drawLine(x1, y1, x2, y2);
        }
    }

    private static void drawNoiseDots(Graphics2D graphics, int width, int height) {
        for (int i = 0; i < 90; i++) {
            graphics.setColor(randomLightColor());
            graphics.fillRect(RANDOM.nextInt(width), RANDOM.nextInt(height), 1, 1);
        }
    }

    private static char randomChar(char[] chars) {
        return chars[RANDOM.nextInt(chars.length)];
    }

    private static Color randomDarkColor() {
        return new Color(RANDOM.nextInt(90), RANDOM.nextInt(90), RANDOM.nextInt(90));
    }

    private static Color randomLightColor() {
        return new Color(120 + RANDOM.nextInt(100), 120 + RANDOM.nextInt(100), 120 + RANDOM.nextInt(100));
    }
}
