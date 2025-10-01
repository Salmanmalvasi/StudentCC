package tk.therealsuji.vtopchennai.helpers;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Configuration settings for enhanced CAPTCHA processing
 */
public class CaptchaConfig {
    private static final String PREF_NAME = "captcha_config";
    
    // Configuration keys
    private static final String KEY_USE_NEURAL_NETWORK = "use_neural_network";
    private static final String KEY_USE_PATTERN_MATCHING = "use_pattern_matching";
    private static final String KEY_USE_ENHANCED_OCR = "use_enhanced_ocr";
    private static final String KEY_USE_TEMPLATE_MATCHING = "use_template_matching";
    private static final String KEY_NEURAL_CONFIDENCE_THRESHOLD = "neural_confidence_threshold";
    private static final String KEY_PATTERN_CONFIDENCE_THRESHOLD = "pattern_confidence_threshold";
    private static final String KEY_AUTO_SUBMIT = "auto_submit";
    private static final String KEY_PREPROCESSING_ENABLED = "preprocessing_enabled";
    
    // Default values
    private static final boolean DEFAULT_USE_NEURAL_NETWORK = true;
    private static final boolean DEFAULT_USE_PATTERN_MATCHING = true;
    private static final boolean DEFAULT_USE_ENHANCED_OCR = true;
    private static final boolean DEFAULT_USE_TEMPLATE_MATCHING = false;
    private static final float DEFAULT_NEURAL_CONFIDENCE_THRESHOLD = 0.6f;
    private static final float DEFAULT_PATTERN_CONFIDENCE_THRESHOLD = 0.7f;
    private static final boolean DEFAULT_AUTO_SUBMIT = true;
    private static final boolean DEFAULT_PREPROCESSING_ENABLED = true;
    
    private final SharedPreferences preferences;
    
    public CaptchaConfig(Context context) {
        this.preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    // Getter methods
    public boolean isNeuralNetworkEnabled() {
        return preferences.getBoolean(KEY_USE_NEURAL_NETWORK, DEFAULT_USE_NEURAL_NETWORK);
    }
    
    public boolean isPatternMatchingEnabled() {
        return preferences.getBoolean(KEY_USE_PATTERN_MATCHING, DEFAULT_USE_PATTERN_MATCHING);
    }
    
    public boolean isEnhancedOcrEnabled() {
        return preferences.getBoolean(KEY_USE_ENHANCED_OCR, DEFAULT_USE_ENHANCED_OCR);
    }
    
    public boolean isTemplateMatchingEnabled() {
        return preferences.getBoolean(KEY_USE_TEMPLATE_MATCHING, DEFAULT_USE_TEMPLATE_MATCHING);
    }
    
    public float getNeuralConfidenceThreshold() {
        return preferences.getFloat(KEY_NEURAL_CONFIDENCE_THRESHOLD, DEFAULT_NEURAL_CONFIDENCE_THRESHOLD);
    }
    
    public float getPatternConfidenceThreshold() {
        return preferences.getFloat(KEY_PATTERN_CONFIDENCE_THRESHOLD, DEFAULT_PATTERN_CONFIDENCE_THRESHOLD);
    }
    
    public boolean isAutoSubmitEnabled() {
        return preferences.getBoolean(KEY_AUTO_SUBMIT, DEFAULT_AUTO_SUBMIT);
    }
    
    public boolean isPreprocessingEnabled() {
        return preferences.getBoolean(KEY_PREPROCESSING_ENABLED, DEFAULT_PREPROCESSING_ENABLED);
    }
    
    // Setter methods
    public void setNeuralNetworkEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_USE_NEURAL_NETWORK, enabled).apply();
    }
    
    public void setPatternMatchingEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_USE_PATTERN_MATCHING, enabled).apply();
    }
    
    public void setEnhancedOcrEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_USE_ENHANCED_OCR, enabled).apply();
    }
    
    public void setTemplateMatchingEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_USE_TEMPLATE_MATCHING, enabled).apply();
    }
    
    public void setNeuralConfidenceThreshold(float threshold) {
        preferences.edit().putFloat(KEY_NEURAL_CONFIDENCE_THRESHOLD, 
            Math.max(0.0f, Math.min(1.0f, threshold))).apply();
    }
    
    public void setPatternConfidenceThreshold(float threshold) {
        preferences.edit().putFloat(KEY_PATTERN_CONFIDENCE_THRESHOLD, 
            Math.max(0.0f, Math.min(1.0f, threshold))).apply();
    }
    
    public void setAutoSubmitEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_AUTO_SUBMIT, enabled).apply();
    }
    
    public void setPreprocessingEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_PREPROCESSING_ENABLED, enabled).apply();
    }
    
    /**
     * Reset all settings to default values
     */
    public void resetToDefaults() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(KEY_USE_NEURAL_NETWORK, DEFAULT_USE_NEURAL_NETWORK);
        editor.putBoolean(KEY_USE_PATTERN_MATCHING, DEFAULT_USE_PATTERN_MATCHING);
        editor.putBoolean(KEY_USE_ENHANCED_OCR, DEFAULT_USE_ENHANCED_OCR);
        editor.putBoolean(KEY_USE_TEMPLATE_MATCHING, DEFAULT_USE_TEMPLATE_MATCHING);
        editor.putFloat(KEY_NEURAL_CONFIDENCE_THRESHOLD, DEFAULT_NEURAL_CONFIDENCE_THRESHOLD);
        editor.putFloat(KEY_PATTERN_CONFIDENCE_THRESHOLD, DEFAULT_PATTERN_CONFIDENCE_THRESHOLD);
        editor.putBoolean(KEY_AUTO_SUBMIT, DEFAULT_AUTO_SUBMIT);
        editor.putBoolean(KEY_PREPROCESSING_ENABLED, DEFAULT_PREPROCESSING_ENABLED);
        editor.apply();
    }
    
    /**
     * Get a summary of current configuration
     */
    public String getConfigSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("CAPTCHA Configuration:\n");
        summary.append("Neural Network: ").append(isNeuralNetworkEnabled() ? "Enabled" : "Disabled").append("\n");
        summary.append("Pattern Matching: ").append(isPatternMatchingEnabled() ? "Enabled" : "Disabled").append("\n");
        summary.append("Enhanced OCR: ").append(isEnhancedOcrEnabled() ? "Enabled" : "Disabled").append("\n");
        summary.append("Template Matching: ").append(isTemplateMatchingEnabled() ? "Enabled" : "Disabled").append("\n");
        summary.append("Neural Threshold: ").append(String.format("%.2f", getNeuralConfidenceThreshold())).append("\n");
        summary.append("Pattern Threshold: ").append(String.format("%.2f", getPatternConfidenceThreshold())).append("\n");
        summary.append("Auto Submit: ").append(isAutoSubmitEnabled() ? "Enabled" : "Disabled").append("\n");
        summary.append("Preprocessing: ").append(isPreprocessingEnabled() ? "Enabled" : "Disabled");
        return summary.toString();
    }
    
    /**
     * Optimize settings for accuracy (slower but more accurate)
     */
    public void setAccuracyMode() {
        setNeuralNetworkEnabled(true);
        setPatternMatchingEnabled(true);
        setEnhancedOcrEnabled(true);
        setTemplateMatchingEnabled(true);
        setNeuralConfidenceThreshold(0.8f);
        setPatternConfidenceThreshold(0.8f);
        setPreprocessingEnabled(true);
    }
    
    /**
     * Optimize settings for speed (faster but potentially less accurate)
     */
    public void setSpeedMode() {
        setNeuralNetworkEnabled(true);
        setPatternMatchingEnabled(false);
        setEnhancedOcrEnabled(false);
        setTemplateMatchingEnabled(false);
        setNeuralConfidenceThreshold(0.5f);
        setPatternConfidenceThreshold(0.6f);
        setPreprocessingEnabled(false);
    }
    
    /**
     * Balanced settings (good balance of speed and accuracy)
     */
    public void setBalancedMode() {
        setNeuralNetworkEnabled(true);
        setPatternMatchingEnabled(true);
        setEnhancedOcrEnabled(true);
        setTemplateMatchingEnabled(false);
        setNeuralConfidenceThreshold(0.6f);
        setPatternConfidenceThreshold(0.7f);
        setPreprocessingEnabled(true);
    }
}
