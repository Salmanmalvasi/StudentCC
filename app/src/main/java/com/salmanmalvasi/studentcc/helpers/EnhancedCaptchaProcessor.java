package com.salmanmalvasi.studentcc.helpers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

/**
 * Enhanced CAPTCHA processor that combines multiple recognition approaches
 * Uses neural network, pattern matching, and OCR fallback for maximum accuracy
 */
public class EnhancedCaptchaProcessor {
    private static final String TAG = "EnhancedCaptchaProcessor";

    private final NeuralCaptchaSolver neuralSolver;
    private final CaptchaTrainingData trainingData;
    private final CaptchaConfig config;

    public EnhancedCaptchaProcessor(Context context) {
        this.neuralSolver = new NeuralCaptchaSolver();
        this.trainingData = new CaptchaTrainingData();
        this.config = new CaptchaConfig(context);
    }

    // Keep the old constructor for backward compatibility
    public EnhancedCaptchaProcessor() {
        this.neuralSolver = new NeuralCaptchaSolver();
        this.trainingData = new CaptchaTrainingData();
        this.config = null; // Will use default settings
    }

    /**
     * Process CAPTCHA using multiple recognition methods
     * Returns the best result from available methods
     */
    public CaptchaResult processCaptcha(Bitmap captchaImage) {
        Log.d(TAG, "Processing CAPTCHA with enhanced methods");

        CaptchaResult result = new CaptchaResult();

        // Get configuration settings (use defaults if config is null)
        boolean useNeural = config != null ? config.isNeuralNetworkEnabled() : true;
        boolean usePattern = config != null ? config.isPatternMatchingEnabled() : true;
        boolean useEnhancedOcr = config != null ? config.isEnhancedOcrEnabled() : true;
        boolean useTemplate = config != null ? config.isTemplateMatchingEnabled() : false;

        float neuralThreshold = config != null ? config.getNeuralConfidenceThreshold() : 0.6f;
        float patternThreshold = config != null ? config.getPatternConfidenceThreshold() : 0.7f;

        try {
            // Method 1: Neural Network Recognition
            if (useNeural) {
                String neuralResult = processWithNeuralNetwork(captchaImage);
                if (neuralResult != null && !neuralResult.isEmpty()) {
                    result.addResult("neural", neuralResult, neuralThreshold + 0.1f);
                    Log.d(TAG, "Neural network result: " + neuralResult);
                }
            }

            // Method 2: Pattern Matching
            if (usePattern) {
                String patternResult = processWithPatternMatching(captchaImage);
                if (patternResult != null && !patternResult.isEmpty()) {
                    result.addResult("pattern", patternResult, patternThreshold);
                    Log.d(TAG, "Pattern matching result: " + patternResult);
                }
            }

            // Method 3: Enhanced OCR (fallback)
            if (useEnhancedOcr) {
                String ocrResult = processWithEnhancedOCR(captchaImage);
                if (ocrResult != null && !ocrResult.isEmpty()) {
                    result.addResult("ocr", ocrResult, 0.5f);
                    Log.d(TAG, "Enhanced OCR result: " + ocrResult);
                }
            }

            // Method 4: Template Matching (additional fallback)
            if (useTemplate) {
                String templateResult = processWithTemplateMatching(captchaImage);
                if (templateResult != null && !templateResult.isEmpty()) {
                    result.addResult("template", templateResult, 0.4f);
                    Log.d(TAG, "Template matching result: " + templateResult);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing CAPTCHA", e);
        }

        return result;
    }

    /**
     * Process with neural network
     */
    private String processWithNeuralNetwork(Bitmap image) {
        try {
            return neuralSolver.solveCaptcha(image);
        } catch (Exception e) {
            Log.e(TAG, "Neural network processing failed", e);
            return null;
        }
    }

    /**
     * Process with pattern matching
     */
    private String processWithPatternMatching(Bitmap image) {
        try {
            // Preprocess image
            Bitmap processed = preprocessForPatternMatching(image);

            // Segment into characters
            Bitmap[] characters = segmentIntoCharacters(processed);

            StringBuilder result = new StringBuilder();
            for (Bitmap charImage : characters) {
                // Convert character to input array
                float[] input = convertCharacterToArray(charImage);

                // Use training data pattern matching
                String character = trainingData.matchCharacterPattern(input);
                if (character != null) {
                    result.append(character);
                }
            }

            return result.length() > 0 ? result.toString() : null;

        } catch (Exception e) {
            Log.e(TAG, "Pattern matching failed", e);
            return null;
        }
    }

    /**
     * Process with enhanced OCR techniques
     */
    private String processWithEnhancedOCR(Bitmap image) {
        try {
            // Apply multiple preprocessing techniques
            Bitmap[] preprocessedImages = {
                enhanceContrast(image, 2.0f),
                applySharpenFilter(image),
                removeNoise(image),
                adjustBrightness(image, 30),
                applyGaussianBlur(image, 1.0f)
            };

            // Try OCR on each preprocessed version
            for (Bitmap processed : preprocessedImages) {
                String result = performBasicOCR(processed);
                if (isValidCaptchaResult(result)) {
                    return result;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Enhanced OCR failed", e);
        }

        return null;
    }

    /**
     * Process with template matching
     */
    private String processWithTemplateMatching(Bitmap image) {
        try {
            // Create character templates
            Bitmap[] templates = createCharacterTemplates();

            // Preprocess image
            Bitmap processed = binarizeImage(image);

            // Segment into characters
            Bitmap[] characters = segmentIntoCharacters(processed);

            StringBuilder result = new StringBuilder();
            for (Bitmap charImage : characters) {
                String bestMatch = findBestTemplateMatch(charImage, templates);
                if (bestMatch != null) {
                    result.append(bestMatch);
                }
            }

            return result.length() > 0 ? result.toString() : null;

        } catch (Exception e) {
            Log.e(TAG, "Template matching failed", e);
            return null;
        }
    }

    /**
     * Preprocess image for pattern matching
     */
    private Bitmap preprocessForPatternMatching(Bitmap image) {
        // Scale to standard size
        Bitmap scaled = Bitmap.createScaledBitmap(image, 100, 40, true);

        // Apply median filter to reduce noise
        Bitmap denoised = applyMedianFilter(scaled);

        // Binarize
        return binarizeImage(denoised);
    }

    /**
     * Segment image into individual characters
     */
    private Bitmap[] segmentIntoCharacters(Bitmap image) {
        // This is a simplified segmentation - can be enhanced
        int charWidth = image.getWidth() / 5; // Assume 5 characters
        Bitmap[] characters = new Bitmap[5];

        for (int i = 0; i < 5; i++) {
            int startX = i * charWidth;
            int width = Math.min(charWidth, image.getWidth() - startX);

            if (width > 0) {
                characters[i] = Bitmap.createBitmap(image, startX, 0, width, image.getHeight());
            }
        }

        return characters;
    }

    /**
     * Convert character bitmap to array for pattern matching
     */
    private float[] convertCharacterToArray(Bitmap charImage) {
        if (charImage == null) return new float[0];

        // Scale to 5x5 for pattern matching
        Bitmap scaled = Bitmap.createScaledBitmap(charImage, 5, 5, true);
        float[] array = new float[25];

        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                int pixel = scaled.getPixel(x, y);
                array[y * 5 + x] = (pixel == Color.BLACK) ? 0.0f : 1.0f;
            }
        }

        return array;
    }

    /**
     * Enhanced contrast adjustment
     */
    private Bitmap enhanceContrast(Bitmap image, float factor) {
        Bitmap result = image.copy(image.getConfig(), true);
        int width = result.getWidth();
        int height = result.getHeight();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = result.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                r = Math.max(0, Math.min(255, (int) ((r - 128) * factor + 128)));
                g = Math.max(0, Math.min(255, (int) ((g - 128) * factor + 128)));
                b = Math.max(0, Math.min(255, (int) ((b - 128) * factor + 128)));

                result.setPixel(x, y, Color.rgb(r, g, b));
            }
        }

        return result;
    }

    /**
     * Apply sharpen filter
     */
    private Bitmap applySharpenFilter(Bitmap image) {
        // Sharpen kernel
        float[] kernel = {
            0, -1, 0,
            -1, 5, -1,
            0, -1, 0
        };

        return applyConvolution(image, kernel, 3);
    }

    /**
     * Remove noise using median filter
     */
    private Bitmap removeNoise(Bitmap image) {
        return applyMedianFilter(image);
    }

    /**
     * Adjust brightness
     */
    private Bitmap adjustBrightness(Bitmap image, int brightness) {
        Bitmap result = image.copy(image.getConfig(), true);
        int width = result.getWidth();
        int height = result.getHeight();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = result.getPixel(x, y);
                int r = Math.max(0, Math.min(255, Color.red(pixel) + brightness));
                int g = Math.max(0, Math.min(255, Color.green(pixel) + brightness));
                int b = Math.max(0, Math.min(255, Color.blue(pixel) + brightness));

                result.setPixel(x, y, Color.rgb(r, g, b));
            }
        }

        return result;
    }

    /**
     * Apply Gaussian blur
     */
    private Bitmap applyGaussianBlur(Bitmap image, float sigma) {
        // Simple box blur approximation
        return applyBoxBlur(image, 2);
    }

    /**
     * Apply convolution filter
     */
    private Bitmap applyConvolution(Bitmap image, float[] kernel, int kernelSize) {
        Bitmap result = image.copy(image.getConfig(), true);
        int width = image.getWidth();
        int height = image.getHeight();
        int offset = kernelSize / 2;

        for (int x = offset; x < width - offset; x++) {
            for (int y = offset; y < height - offset; y++) {
                float r = 0, g = 0, b = 0;

                for (int i = 0; i < kernelSize; i++) {
                    for (int j = 0; j < kernelSize; j++) {
                        int px = x + i - offset;
                        int py = y + j - offset;
                        int pixel = image.getPixel(px, py);
                        float weight = kernel[i * kernelSize + j];

                        r += Color.red(pixel) * weight;
                        g += Color.green(pixel) * weight;
                        b += Color.blue(pixel) * weight;
                    }
                }

                int newR = Math.max(0, Math.min(255, (int) r));
                int newG = Math.max(0, Math.min(255, (int) g));
                int newB = Math.max(0, Math.min(255, (int) b));

                result.setPixel(x, y, Color.rgb(newR, newG, newB));
            }
        }

        return result;
    }

    /**
     * Apply median filter
     */
    private Bitmap applyMedianFilter(Bitmap image) {
        // Simplified median filter implementation
        return applyBoxBlur(image, 1);
    }

    /**
     * Apply box blur
     */
    private Bitmap applyBoxBlur(Bitmap image, int radius) {
        Bitmap result = image.copy(image.getConfig(), true);
        int width = image.getWidth();
        int height = image.getHeight();

        for (int x = radius; x < width - radius; x++) {
            for (int y = radius; y < height - radius; y++) {
                int r = 0, g = 0, b = 0, count = 0;

                for (int i = -radius; i <= radius; i++) {
                    for (int j = -radius; j <= radius; j++) {
                        int pixel = image.getPixel(x + i, y + j);
                        r += Color.red(pixel);
                        g += Color.green(pixel);
                        b += Color.blue(pixel);
                        count++;
                    }
                }

                result.setPixel(x, y, Color.rgb(r / count, g / count, b / count));
            }
        }

        return result;
    }

    /**
     * Binarize image using adaptive thresholding
     */
    private Bitmap binarizeImage(Bitmap image) {
        // Convert to grayscale first
        Bitmap grayscale = convertToGrayscale(image);

        // Apply adaptive threshold
        return applyAdaptiveThreshold(grayscale);
    }

    private Bitmap convertToGrayscale(Bitmap image) {
        Bitmap result = image.copy(image.getConfig(), true);
        int width = result.getWidth();
        int height = result.getHeight();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = result.getPixel(x, y);
                int gray = (int) (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel));
                result.setPixel(x, y, Color.rgb(gray, gray, gray));
            }
        }

        return result;
    }

    private Bitmap applyAdaptiveThreshold(Bitmap image) {
        // Simplified adaptive threshold
        Bitmap result = image.copy(image.getConfig(), true);
        int width = image.getWidth();
        int height = image.getHeight();

        // Calculate global threshold using Otsu's method
        int threshold = calculateOtsuThreshold(image);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = image.getPixel(x, y);
                int gray = Color.red(pixel); // Already grayscale

                int binaryValue = gray > threshold ? Color.WHITE : Color.BLACK;
                result.setPixel(x, y, binaryValue);
            }
        }

        return result;
    }

    private int calculateOtsuThreshold(Bitmap image) {
        int[] histogram = new int[256];
        int totalPixels = image.getWidth() * image.getHeight();

        // Build histogram
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int pixel = image.getPixel(x, y);
                int gray = Color.red(pixel);
                histogram[gray]++;
            }
        }

        // Calculate Otsu threshold
        float sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += i * histogram[i];
        }

        float sumB = 0;
        int wB = 0;
        float varMax = 0;
        int threshold = 0;

        for (int t = 0; t < 256; t++) {
            wB += histogram[t];
            if (wB == 0) continue;

            int wF = totalPixels - wB;
            if (wF == 0) break;

            sumB += t * histogram[t];
            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;

            float varBetween = wB * wF * (mB - mF) * (mB - mF);

            if (varBetween > varMax) {
                varMax = varBetween;
                threshold = t;
            }
        }

        return threshold;
    }

    /**
     * Perform basic OCR (placeholder - integrate with ML Kit)
     */
    private String performBasicOCR(Bitmap image) {
        // This would integrate with Google ML Kit TextRecognition
        // For now, return null to indicate OCR should be handled externally
        return null;
    }

    /**
     * Create character templates for template matching
     */
    private Bitmap[] createCharacterTemplates() {
        // This would create standard character templates
        // For now, return empty array
        return new Bitmap[0];
    }

    /**
     * Find best template match
     */
    private String findBestTemplateMatch(Bitmap character, Bitmap[] templates) {
        // Template matching implementation
        return null;
    }

    /**
     * Validate if result looks like valid CAPTCHA
     */
    private boolean isValidCaptchaResult(String result) {
        return result != null && result.length() >= 3 && result.length() <= 8 &&
               result.matches("[A-Za-z0-9]+");
    }

    /**
     * Result container for CAPTCHA processing
     */
    public static class CaptchaResult {
        private String bestResult = null;
        private float bestConfidence = 0.0f;
        private String bestMethod = null;

        public void addResult(String method, String result, float confidence) {
            if (confidence > bestConfidence) {
                bestResult = result;
                bestConfidence = confidence;
                bestMethod = method;
            }
        }

        public String getBestResult() {
            return bestResult;
        }

        public float getBestConfidence() {
            return bestConfidence;
        }

        public String getBestMethod() {
            return bestMethod;
        }

        public boolean hasValidResult() {
            return bestResult != null && bestResult.length() >= 3;
        }
    }
}
