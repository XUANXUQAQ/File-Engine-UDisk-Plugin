package file.engine.udisk.utils;

import java.awt.*;

public class ColorUtil {

    public static String parseColorHex(Color color) {
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        StringBuilder rgb = new StringBuilder();
        if (r == 0) {
            rgb.append("00");
        } else {
            rgb.append(Integer.toHexString(r));
        }
        if (g == 0) {
            rgb.append("00");
        } else {
            rgb.append(Integer.toHexString(g));
        }
        if (b == 0) {
            rgb.append("00");
        } else {
            rgb.append(Integer.toHexString(b));
        }
        return rgb.toString();
    }

}
