package me.cortex.voxy.client.core.model;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;

public final class TextureUtilsTest {
    public static void main(String[] args) throws Exception {
        int[] pixels = {0xff0000ff, 0xff00ff00, 0xffff0000, 0x80402010, 0xffffffff, 0};
        for (boolean dark : new boolean[]{false, true}) for (int pixel : pixels) {
            if (TextureUtils.mipColours(dark, pixel, pixel, pixel, pixel) != pixel) {
                throw new AssertionError("RGBA texel channels changed: " + Integer.toHexString(pixel));
            }
        }
        var digest = MessageDigest.getInstance("SHA-256");
        var random = new Random(4326987911L);
        for (int i = 0; i < 100000; i++) {
            int a = random.nextInt(), b = random.nextInt(), c = random.nextInt(), d = random.nextInt();
            for (boolean dark : new boolean[]{false, true}) {
                int mip = TextureUtils.mipColours(dark, a, b, c, d);
                for (int channel = 0; channel < 4; channel++) digest.update((byte) (mip >>> (8 * channel)));
            }
        }
        // Recorded from the original source/artifact under JDK25, using this exact fixture.
        var hash = HexFormat.of().formatHex(digest.digest());
        if (!hash.equals("8ac96228763384c080e7e424dbe02e6c99dda36a661690f50dc460ae9d7821b9")) {
            throw new AssertionError("Mipmap compatibility changed: " + hash);
        }
        System.out.println("TextureUtils: RGBA channel fixtures and 100000 original-equivalent mip pairs passed");
    }
}
