package tk.therealsuji.vtopchennai.helpers;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Neural Network-based CAPTCHA solver
 * Uses pre-trained neural network weights to recognize CAPTCHA characters
 */
public class NeuralCaptchaSolver {
    private static final String TAG = "NeuralCaptchaSolver";

    // Neural network architecture constants
    private static final int INPUT_SIZE = 400; // 20x20 input image
    private static final int HIDDEN_SIZE = 100;
    private static final int OUTPUT_SIZE = 36; // 26 letters + 10 digits

    // Character mapping (0-9, A-Z)
    private static final String CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    // Neural network weights and biases (will be initialized from training data)
    private float[][] inputToHiddenWeights;
    private float[] hiddenBiases;
    private float[][] hiddenToOutputWeights;
    private float[] outputBiases;

    // Confidence threshold for character recognition
    private static final float CONFIDENCE_THRESHOLD = 0.3f;

    public NeuralCaptchaSolver() {
        initializeNeuralNetwork();
    }

    /**
     * Initialize neural network with pre-trained weights
     */
    private void initializeNeuralNetwork() {
        try {
            // Initialize arrays
            inputToHiddenWeights = new float[INPUT_SIZE][HIDDEN_SIZE];
            hiddenBiases = new float[HIDDEN_SIZE];
            hiddenToOutputWeights = new float[HIDDEN_SIZE][OUTPUT_SIZE];
            outputBiases = new float[OUTPUT_SIZE];

            // Load pre-trained weights
            CaptchaTrainingData trainingData = new CaptchaTrainingData();
            loadWeightsFromTrainingData(trainingData);

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize neural network", e);
            // Fallback to random weights if training data fails
            initializeRandomWeights();
        }
    }

    /**
     * Load weights from training data
     */
    private void loadWeightsFromTrainingData(CaptchaTrainingData data) {
        float[][] weights = data.getWeights();
        float[] biases = data.getBiases();

        // Extract input-to-hidden weights
        int weightIndex = 0;
        for (int i = 0; i < INPUT_SIZE; i++) {
            for (int j = 0; j < HIDDEN_SIZE; j++) {
                inputToHiddenWeights[i][j] = weights[weightIndex / weights[0].length][weightIndex % weights[0].length];
                weightIndex++;
            }
        }

        // Extract hidden biases
        System.arraycopy(biases, 0, hiddenBiases, 0, Math.min(biases.length, HIDDEN_SIZE));

        // Extract hidden-to-output weights
        for (int i = 0; i < HIDDEN_SIZE; i++) {
            for (int j = 0; j < OUTPUT_SIZE; j++) {
                if (weightIndex < weights.length * weights[0].length) {
                    hiddenToOutputWeights[i][j] = weights[weightIndex / weights[0].length][weightIndex % weights[0].length];
                    weightIndex++;
                }
            }
        }

        // Extract output biases
        int biasIndex = HIDDEN_SIZE;
        for (int i = 0; i < OUTPUT_SIZE && biasIndex < biases.length; i++) {
            outputBiases[i] = biases[biasIndex++];
        }
    }

    /**
     * Initialize random weights as fallback
     */
    private void initializeRandomWeights() {
        // Xavier initialization
        float inputWeight = (float) Math.sqrt(2.0 / INPUT_SIZE);
        float hiddenWeight = (float) Math.sqrt(2.0 / HIDDEN_SIZE);

        for (int i = 0; i < INPUT_SIZE; i++) {
            for (int j = 0; j < HIDDEN_SIZE; j++) {
                inputToHiddenWeights[i][j] = (float) (Math.random() - 0.5) * 2 * inputWeight;
            }
        }

        for (int i = 0; i < HIDDEN_SIZE; i++) {
            hiddenBiases[i] = 0.0f;
            for (int j = 0; j < OUTPUT_SIZE; j++) {
                hiddenToOutputWeights[i][j] = (float) (Math.random() - 0.5) * 2 * hiddenWeight;
            }
        }

        Arrays.fill(outputBiases, 0.0f);
    }

    /**
     * Solve CAPTCHA using neural network approach
     */
    public String solveCaptcha(Bitmap captchaImage) {
        try {
            // Preprocess the image
            Bitmap processedImage = preprocessImage(captchaImage);

            // Segment image into character blocks
            List<Bitmap> characterBlocks = segmentImage(processedImage);

            // Recognize each character
            StringBuilder result = new StringBuilder();
            for (Bitmap block : characterBlocks) {
                String character = recognizeCharacter(block);
                if (character != null && !character.isEmpty()) {
                    result.append(character);
                }
            }

            String captchaText = result.toString();
            Log.d(TAG, "Neural network recognized CAPTCHA: " + captchaText);

            // Validate result
            return isValidCaptchaText(captchaText) ? captchaText : null;

        } catch (Exception e) {
            Log.e(TAG, "Error solving CAPTCHA with neural network", e);
            return null;
        }
    }

    /**
     * Preprocess image for neural network input
     */
    private Bitmap preprocessImage(Bitmap original) {
        // Scale to standard size
        Bitmap scaled = Bitmap.createScaledBitmap(original, 100, 40, true);

        // Convert to grayscale and apply threshold
        return binarizeImage(scaled);
    }

    /**
     * Binarize image (convert to black and white)
     */
    private Bitmap binarizeImage(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();
        Bitmap binarized = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        // Calculate threshold using Otsu's method
        int[] histogram = new int[256];

        // Build histogram
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = original.getPixel(x, y);
                int gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
                histogram[gray]++;
            }
        }

        // Find optimal threshold
        int threshold = findOtsuThreshold(histogram, width * height);

        // Apply threshold
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = original.getPixel(x, y);
                int gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
                int binaryValue = gray > threshold ? Color.WHITE : Color.BLACK;
                binarized.setPixel(x, y, binaryValue);
            }
        }

        return binarized;
    }

    /**
     * Find optimal threshold using Otsu's method
     */
    private int findOtsuThreshold(int[] histogram, int totalPixels) {
        float sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += i * histogram[i];
        }

        float sumB = 0;
        int wB = 0;
        int wF = 0;
        float varMax = 0;
        int threshold = 0;

        for (int t = 0; t < 256; t++) {
            wB += histogram[t];
            if (wB == 0) continue;

            wF = totalPixels - wB;
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
     * Segment image into individual character blocks
     */
    private List<Bitmap> segmentImage(Bitmap image) {
        List<Bitmap> blocks = new ArrayList<>();
        int width = image.getWidth();
        int height = image.getHeight();

        // Find character boundaries by analyzing vertical projections
        boolean[] hasContent = new boolean[width];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = image.getPixel(x, y);
                if (pixel == Color.BLACK) {
                    hasContent[x] = true;
                    break;
                }
            }
        }

        // Find character segments
        List<int[]> segments = new ArrayList<>();
        int start = -1;

        for (int x = 0; x < width; x++) {
            if (hasContent[x] && start == -1) {
                start = x;
            } else if (!hasContent[x] && start != -1) {
                if (x - start > 5) { // Minimum character width
                    segments.add(new int[]{start, x});
                }
                start = -1;
            }
        }

        // Add final segment if needed
        if (start != -1 && width - start > 5) {
            segments.add(new int[]{start, width});
        }

        // Extract character blocks
        for (int[] segment : segments) {
            int blockWidth = segment[1] - segment[0];
            if (blockWidth > 0) {
                Bitmap block = Bitmap.createBitmap(image, segment[0], 0, blockWidth, height);
                // Scale to standard 20x20 size for neural network
                Bitmap scaledBlock = Bitmap.createScaledBitmap(block, 20, 20, true);
                blocks.add(scaledBlock);
            }
        }

        return blocks;
    }

    /**
     * Recognize single character using neural network
     */
    private String recognizeCharacter(Bitmap characterImage) {
        try {
            // Convert bitmap to input array
            float[] input = bitmapToInputArray(characterImage);

            // Forward pass through neural network
            float[] output = forwardPass(input);

            // Find character with highest confidence
            int maxIndex = 0;
            float maxConfidence = output[0];

            for (int i = 1; i < output.length; i++) {
                if (output[i] > maxConfidence) {
                    maxConfidence = output[i];
                    maxIndex = i;
                }
            }

            // Return character if confidence is above threshold
            if (maxConfidence > CONFIDENCE_THRESHOLD) {
                return String.valueOf(CHARSET.charAt(maxIndex));
            }

        } catch (Exception e) {
            Log.e(TAG, "Error recognizing character", e);
        }

        return null;
    }

    /**
     * Convert bitmap to input array for neural network
     */
    private float[] bitmapToInputArray(Bitmap bitmap) {
        float[] input = new float[INPUT_SIZE];
        int index = 0;

        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) {
                int pixel = bitmap.getPixel(x, y);
                // Convert to 0-1 range (0 for black, 1 for white)
                input[index++] = pixel == Color.BLACK ? 0.0f : 1.0f;
            }
        }

        return input;
    }

    /**
     * Perform forward pass through neural network
     */
    private float[] forwardPass(float[] input) {
        // Hidden layer
        float[] hidden = new float[HIDDEN_SIZE];
        for (int i = 0; i < HIDDEN_SIZE; i++) {
            float sum = hiddenBiases[i];
            for (int j = 0; j < INPUT_SIZE; j++) {
                sum += input[j] * inputToHiddenWeights[j][i];
            }
            hidden[i] = relu(sum);
        }

        // Output layer
        float[] output = new float[OUTPUT_SIZE];
        for (int i = 0; i < OUTPUT_SIZE; i++) {
            float sum = outputBiases[i];
            for (int j = 0; j < HIDDEN_SIZE; j++) {
                sum += hidden[j] * hiddenToOutputWeights[j][i];
            }
            output[i] = sum;
        }

        // Apply softmax
        return softmax(output);
    }

    /**
     * ReLU activation function
     */
    private float relu(float x) {
        return Math.max(0, x);
    }

    /**
     * Softmax activation function
     */
    private float[] softmax(float[] input) {
        float[] output = new float[input.length];
        float max = input[0];

        // Find max for numerical stability
        for (float value : input) {
            if (value > max) max = value;
        }

        // Calculate exponentials and sum
        float sum = 0;
        for (int i = 0; i < input.length; i++) {
            output[i] = (float) Math.exp(input[i] - max);
            sum += output[i];
        }

        // Normalize
        for (int i = 0; i < output.length; i++) {
            output[i] /= sum;
        }

        return output;
    }

    /**
     * Validate if the recognized text looks like a valid captcha
     */
    private boolean isValidCaptchaText(String text) {
        return text != null && text.length() >= 3 && text.length() <= 8 &&
               text.matches("[A-Za-z0-9]+");
    }
}
