package com.salmanmalvasi.studentcc.helpers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * High-performance, pre-trained neural network CAPTCHA solver tailored specifically for VTOP captchas.
 * Slices 6 characters using precise geometric bounds and classifies each character
 * via linear projection + softmax across 32 non-ambiguous alphanumeric classes.
 */
public class NeuralCaptchaSolver {
    private static final String TAG = "NeuralCaptchaSolver";

    // VTOP character set (32 characters; VTOP omits 0, 1, I, O to prevent ambiguity)
    public static final String CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int INPUT_SIZE = 528; // 22 rows x 24 cols
    private static final int OUTPUT_SIZE = 32;

    private static final float[][] weights = new float[INPUT_SIZE][OUTPUT_SIZE];
    private static final float[] biases = new float[OUTPUT_SIZE];
    private static volatile boolean isLoaded = false;

    public NeuralCaptchaSolver() {
        ensureLoaded(null);
    }

    public NeuralCaptchaSolver(Context context) {
        ensureLoaded(context);
    }

    public static synchronized void ensureLoaded(Context context) {
        if (isLoaded) return;
        InputStream is = null;
        try {
            if (context != null) {
                try {
                    is = context.getAssets().open("vtop_captcha_model.bin");
                } catch (Exception ignored) {}
            }
            if (is == null) {
                is = NeuralCaptchaSolver.class.getClassLoader().getResourceAsStream("vtop_captcha_model.bin");
            }
            if (is == null) {
                is = ClassLoader.getSystemResourceAsStream("vtop_captcha_model.bin");
            }
            if (is != null) {
                byte[] bytes = new byte[(INPUT_SIZE * OUTPUT_SIZE + OUTPUT_SIZE) * 4];
                int offset = 0;
                while (offset < bytes.length) {
                    int read = is.read(bytes, offset, bytes.length - offset);
                    if (read == -1) break;
                    offset += read;
                }
                ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < INPUT_SIZE; i++) {
                    for (int j = 0; j < OUTPUT_SIZE; j++) {
                        weights[i][j] = buffer.getFloat();
                    }
                }
                for (int j = 0; j < OUTPUT_SIZE; j++) {
                    biases[j] = buffer.getFloat();
                }
                isLoaded = true;
                Log.d(TAG, "Neural captcha model loaded successfully (16928 parameters)");
            } else {
                Log.w(TAG, "Could not find vtop_captcha_model.bin in assets or resources");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading neural captcha model", e);
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Solves the VTOP captcha image directly with neural weights.
     * Returns a 6-character string, or null on failure.
     */
    public String solveCaptcha(Bitmap captchaImage) {
        if (captchaImage == null) return null;
        if (!isLoaded) {
            ensureLoaded(null);
        }
        if (!isLoaded) return null;

        try {
            Bitmap bmp = captchaImage;
            if (bmp.getWidth() != 200 || bmp.getHeight() != 40) {
                bmp = Bitmap.createScaledBitmap(bmp, 200, 40, true);
            }

            int width = 200;
            int height = 40;
            int[] pixels = new int[width * height];
            bmp.getPixels(pixels, 0, width, 0, 0, width, height);

            // Compute saturation matrix [40][200]
            int[][] saturate = new int[height][width];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int p = pixels[y * width + x];
                    int r = Color.red(p);
                    int g = Color.green(p);
                    int b = Color.blue(p);

                    int max = Math.max(r, Math.max(g, b));
                    int min = Math.min(r, Math.min(g, b));
                    saturate[y][x] = (max == 0) ? 0 : Math.round(((max - min) * 255.0f) / max);
                }
            }

            StringBuilder sb = new StringBuilder(6);
            for (int charIdx = 0; charIdx < 6; charIdx++) {
                int x1 = (charIdx + 1) * 25 + 2;
                int y1 = 7 + 5 * (charIdx % 2) + 1;
                int x2 = (charIdx + 2) * 25 + 1;
                int y2 = 35 - 5 * ((charIdx + 1) % 2);

                int blockH = y2 - y1; // 22
                int blockW = x2 - x1; // 24

                // Calculate mean saturation in the block
                double sum = 0;
                for (int r = y1; r < y2; r++) {
                    for (int c = x1; c < x2; c++) {
                        sum += saturate[r][c];
                    }
                }
                double avg = sum / (blockH * blockW);

                // Binarize
                float[] inputVector = new float[INPUT_SIZE];
                int vIdx = 0;
                for (int r = y1; r < y2; r++) {
                    for (int c = x1; c < x2; c++) {
                        inputVector[vIdx++] = (saturate[r][c] > avg) ? 1.0f : 0.0f;
                    }
                }

                // Matrix multiplication: inputVector (1x528) * weights (528x32) + biases (32)
                float bestLogit = -Float.MAX_VALUE;
                int bestIdx = 0;
                for (int k = 0; k < OUTPUT_SIZE; k++) {
                    float logit = biases[k];
                    for (int j = 0; j < INPUT_SIZE; j++) {
                        logit += inputVector[j] * weights[j][k];
                    }
                    if (logit > bestLogit) {
                        bestLogit = logit;
                        bestIdx = k;
                    }
                }
                sb.append(CHARSET.charAt(bestIdx));
            }

            String result = sb.toString();
            Log.d(TAG, "Neural captcha solver decoded: " + result);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error executing neural captcha solver", e);
            return null;
        }
    }
}
