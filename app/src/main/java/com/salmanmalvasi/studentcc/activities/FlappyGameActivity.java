package com.salmanmalvasi.studentcc.activities;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.salmanmalvasi.studentcc.R;

public class FlappyGameActivity extends AppCompatActivity {

    private GameView gameView;
    private TextView scoreText, highScoreText, gameOverText, motivationText, trollMessage, cgpaText;
    private Button playButton, homeButton;
    private LinearLayout gameOverLayout, startInstructions;
    private ProgressBar cgpaProgressBar;

    private SharedPreferences preferences;
    private static final String PREF_HIGH_SCORE = "flappy_high_score";
    private static final int IMPOSSIBLE_TARGET = 700; // Reduced from 750 for easier achievement 🎉

    // Progressive scoring system - starts from 6.0 CGPA (everyone deserves a good start! �)
    private static final int[] CGPA_THRESHOLDS = {
        0,    // Start at 6.0 CGPA - Great foundation!
        25,   // 6.5 CGPA - 25 points (easier!)
        55,   // 7.0 CGPA - 30 more points
        90,   // 7.5 CGPA - 35 more points
        130,  // 8.0 CGPA - 40 more points
        180,  // 8.5 CGPA - 50 more points
        240,  // 8.6 CGPA - 60 more points
        310,  // 8.7 CGPA - 70 more points
        390,  // 8.8 CGPA - 80 more points
        480,  // 8.9 CGPA - 90 more points
        700   // 9.0 CGPA - 120 more points (Achievable!)
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flappy_game);

        preferences = getSharedPreferences("FlappyGame", MODE_PRIVATE);

        initViews();
        setupGame();
    }

    private void initViews() {
        gameView = findViewById(R.id.game_view);
        scoreText = findViewById(R.id.score_text);
        highScoreText = findViewById(R.id.high_score_text);
        gameOverText = findViewById(R.id.game_over_text);
        motivationText = findViewById(R.id.motivation_text);
        trollMessage = findViewById(R.id.troll_message);
        cgpaText = findViewById(R.id.cgpa_text);
        cgpaProgressBar = findViewById(R.id.cgpa_progress_bar);
        gameOverLayout = findViewById(R.id.game_over_layout);
        startInstructions = findViewById(R.id.start_instructions);
        playButton = findViewById(R.id.play_button);
        homeButton = findViewById(R.id.home_button);

        playButton.setOnClickListener(v -> startGame());
        homeButton.setOnClickListener(v -> finish());

        // Add click listener to start instructions to begin game
        startInstructions.setOnClickListener(v -> startGame());
        gameView.setOnClickListener(v -> {
            if (startInstructions.getVisibility() == View.VISIBLE) {
                startGame();
            }
        });
    }

    private void setupGame() {
        int highScore = preferences.getInt(PREF_HIGH_SCORE, 0);
        highScoreText.setText("High Score: " + highScore);

        gameView.setGameCallbacks(new GameView.GameCallbacks() {
            @Override
            public void onScoreChanged(int score) {
                runOnUiThread(() -> {
                    scoreText.setText("Score: " + score);

                    // Calculate current CGPA based on progressive thresholds
                    float cgpa = calculateCGPA(score);
                    cgpaText.setText(String.format("CGPA: %.1f", cgpa));

                    // Update progress bar (max 750 for 9.0)
                    cgpaProgressBar.setProgress(Math.min(score, 750));

                    // Update motivation/troll text based on progress
                    updateMotivationText(score);
                });
            }

            @Override
            public void onGameOver(int finalScore) {
                runOnUiThread(() -> {
                    int highScore = preferences.getInt(PREF_HIGH_SCORE, 0);

                    if (finalScore >= IMPOSSIBLE_TARGET) {
                        // They actually did it! 🎉
                        gameOverText.setText("AMAZING! 🎉\nYou reached the summit!");
                        trollMessage.setText("Incredible! You've achieved greatness! 🌟\n\nCongratulations! You've unlocked:\n✅ 9.0+ CGPA\n✅ Amazing friends\n✅ Great sleep schedule\n✅ Dream job secured\n\n...in this fun game! Time to make it real! �");
                    } else if (finalScore > highScore) {
                        preferences.edit().putInt(PREF_HIGH_SCORE, finalScore).apply();
                        highScoreText.setText("High Score: " + finalScore);

                        String achievementMsg = getAchievementMessage(finalScore);
                        gameOverText.setText("NEW HIGH SCORE! 🎉\n" + achievementMsg);
                        trollMessage.setText(getProgressiveTrollMessage(finalScore));
                    } else {
                        String achievementMsg = getAchievementMessage(finalScore);
                        gameOverText.setText("Reality Check! 📚\n" + achievementMsg);
                        trollMessage.setText(getDeathTrollMessage(finalScore));
                    }
                    gameOverLayout.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void startGame() {
        startInstructions.setVisibility(View.GONE);
        gameOverLayout.setVisibility(View.GONE);
        scoreText.setText("Score: 0");
        cgpaText.setText("CGPA: 6.0"); // Start from 6.0!
        cgpaProgressBar.setProgress(0);
        motivationText.setText("Starting at 6.0 CGPA! Let's aim higher! 🚀");
        gameView.startGame();
    }

    // Calculate CGPA based on progressive thresholds - STARTING FROM 6.0!
    private float calculateCGPA(int score) {
        for (int i = 0; i < CGPA_THRESHOLDS.length; i++) {
            if (score < CGPA_THRESHOLDS[i]) {
                if (i == 0) {
                    // Before first threshold - still start at 6.0 base
                    return 6.0f + (score / 25.0f) * 0.5f; // 6.0 to 6.5 (easier thresholds)
                } else {
                    // Between thresholds - interpolate
                    int prevThreshold = CGPA_THRESHOLDS[i-1];
                    float progress = (float)(score - prevThreshold) / (CGPA_THRESHOLDS[i] - prevThreshold);
                    return 6.0f + (i * 0.5f) + (progress * 0.5f); // 0.5 CGPA increment per threshold
                }
            }
        }
        // Beyond all thresholds - they made it!
        return 9.0f + (score - CGPA_THRESHOLDS[CGPA_THRESHOLDS.length-1]) / 50.0f; // Easier bonus
    }

    private void updateMotivationText(int score) {
        float cgpa = calculateCGPA(score);

        if (score >= 480) {
            motivationText.setText("8.9 CGPA! Almost at the peak! 🎯✨");
        } else if (score >= 390) {
            motivationText.setText("8.8 CGPA! Your hard work is showing! 📈");
        } else if (score >= 310) {
            motivationText.setText("8.7 CGPA! Excellent progress! �");
        } else if (score >= 240) {
            motivationText.setText("8.6 CGPA! You're doing great! ⭐");
        } else if (score >= 180) {
            motivationText.setText("8.5 CGPA! Fantastic journey so far! �");
        } else if (score >= 130) {
            motivationText.setText("8.0 CGPA! Solid achievement! 💪");
        } else if (score >= 90) {
            motivationText.setText("7.5 CGPA! Keep up the momentum! 📊");
        } else if (score >= 55) {
            motivationText.setText("7.0 CGPA! Nice progress! 🎯");
        } else if (score >= 25) {
            motivationText.setText("6.5 CGPA! Good start! Keep going! 📈");
        } else {
            motivationText.setText("6.0 CGPA! Building momentum! 🌟");
        }
    }

    private String getAchievementMessage(int score) {
        float cgpa = calculateCGPA(score);
        return String.format("Final CGPA: %.1f", cgpa);
    }

    private String getProgressiveTrollMessage(int score) {
        float cgpa = calculateCGPA(score);

        if (score >= 480) {
            return "8.9 CGPA! 🎉 You're almost at the summit!\nJust a little more to reach perfection!\n\nYou've got this! Keep pushing! 💪\n\nLegend status is within reach! 🏆";
        } else if (score >= 390) {
            return "8.8 CGPA! 🌟 Impressive dedication!\nYour consistency is paying off!\n\nKeep this energy going! �\n\nYou're in the elite zone now! ⭐";
        } else if (score >= 310) {
            return "8.7 CGPA! 📚 Knowledge champion!\nYou're clearly putting in the work!\n\nStudy smarter, not just harder! 🎯\n\nGreat balance of effort and results! �";
        } else if (score >= 240) {
            return "8.6 CGPA! 🎓 Academic excellence!\nYou've found your rhythm!\n\nConsistency is your superpower! ⚡\n\nKeep this momentum rolling! 🌊";
        } else if (score >= 180) {
            return "8.5 CGPA! 🏅 Outstanding achievement!\nYou're clearly dedicated to success!\n\nBalance is key - don't forget to have fun! 😊\n\nYou're doing amazingly well! 🎈";
        } else if (score >= 130) {
            return "8.0 CGPA! � Solid performance!\nYou've reached a great milestone!\n\nTime to celebrate your progress! 🎉\n\nYou're in a fantastic position! 📊";
        } else if (score >= 90) {
            return "7.5 CGPA! � Above average achiever!\nYou're climbing steadily upward!\n\nConsistent effort shows! 💪\n\nKeep this positive trend going! 🌟";
        } else if (score >= 55) {
            return "7.0 CGPA! 🎪 Good progress!\nYou're moving in the right direction!\n\nEvery step counts! �\n\nBuilding a strong foundation! 🏗️";
        } else if (score >= 25) {
            return "6.5 CGPA! 🌱 Nice improvement!\nYou've made your first step up!\n\nProgress is progress! 📈\n\nKeep building momentum! �";
        } else {
            return "6.0 CGPA! � Great starting point!\nEveryone begins their journey here!\n\nYou've got unlimited potential! ✨\n\nTime to show what you can do! 🎯";
        }
    }

    private String getDeathTrollMessage(int score) {
        float cgpa = calculateCGPA(score);

        String[] funnyQuotes = {
            "\"I'll just wing this exam\" 🪶\n- Plot twist: Birds are better at this",

            "\"Sleep is overrated anyway\" 😴\n- Your brain: 'Am I a joke to you?'",

            "\"I work better under pressure\" ⚡\n- Pressure cooker vibes activated",

            "\"Just one more episode...\" 📺\n- Netflix: 'Gotcha again!'",

            "\"I'll start studying tomorrow\" 📅\n- Tomorrow: 'Still loading...'",

            "\"My notes are somewhere...\" 📝\n- GPS for notes: Location not found",

            "\"The professor loves me\" 💕\n- Professor: 'Do I know you?'",

            "\"I can study everything in one night\" 🌙\n- One night: 'Challenge rejected'",

            "\"Attendance is just a number\" 📊\n- Zero: 'I'm a number too!'",

            "\"I'm a visual learner\" 👀\n- *Visualizes success, achieves confusion*",

            "\"I have a photographic memory\" 📷\n- Camera battery: 'Dead since semester 1'",

            "\"College is just a piece of paper\" 📄\n- Expensive paper: '₹5 lakhs worth!'",

            "\"Grades don't define me\" 💅\n- Placement officer: 'Actually, they do'",

            "\"I learn better by doing\" 🤲\n- Doing: 'Still waiting for you to start'",

            "\"I'm street smart, not book smart\" 🛣️\n- Streets: 'Don't pay engineering salaries though'"
        };

        // Add encouraging messages based on CGPA achieved
        if (cgpa >= 8.5f) {
            return "SO CLOSE TO GREATNESS! 🎯\n\nYou almost reached the summit!\nJust a little more practice needed!\n\n" + funnyQuotes[score % funnyQuotes.length];
        } else if (cgpa >= 8.0f) {
            return "SOLID EFFORT! 💪\n\nYou showed real skill there!\nKeep pushing - you've got this!\n\n" + funnyQuotes[score % funnyQuotes.length];
        } else if (cgpa >= 7.0f) {
            return "GOOD PROGRESS! 📈\n\nYou're getting the hang of it!\nPractice makes perfect!\n\n" + funnyQuotes[score % funnyQuotes.length];
        } else if (cgpa >= 6.5f) {
            return "NICE START! 🌟\n\nYou made some progress!\nEvery journey begins with a single step!\n\n" + funnyQuotes[score % funnyQuotes.length];
        } else {
            return "LEARNING PHASE! 🎮\n\nEveryone starts somewhere!\nThe best players practice the most!\n\n" + funnyQuotes[score % funnyQuotes.length];
        }
    }

    public static class GameView extends SurfaceView implements SurfaceHolder.Callback {

        private GameThread gameThread;
        private GameCallbacks callbacks;

        // Game objects
        private Player player;
        private List<MoneyTower> towers;
        private Random random;

        // Game state
        private boolean gameRunning;
        private int score;
        private long lastTime;

        // Dimensions
        private int screenWidth, screenHeight;
        private Paint paint;

        // Colors
        private int backgroundColor;
        private int towerColor;
        private int scoreColor;

        public GameView(Context context, android.util.AttributeSet attrs) {
            super(context, attrs);
            getHolder().addCallback(this);

            random = new Random();
            towers = new ArrayList<>();
            paint = new Paint();
            paint.setAntiAlias(true);

            // Initialize colors
            backgroundColor = ContextCompat.getColor(context, R.color.colorPrimary);
            towerColor = Color.parseColor("#FFD700"); // Gold color for money towers
            scoreColor = Color.WHITE;
        }

        public void setGameCallbacks(GameCallbacks callbacks) {
            this.callbacks = callbacks;
        }

        public void startGame() {
            if (gameThread != null && gameThread.isAlive()) {
                gameThread.setRunning(false);
                try {
                    gameThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            resetGame();
            gameThread = new GameThread(getHolder());
            gameThread.setRunning(true);
            gameThread.start();
        }

        private void resetGame() {
            gameRunning = true;
            score = 0;
            towers.clear();

            // Initialize player
            if (player == null) {
                player = new Player(getContext(), screenWidth / 4, screenHeight / 2);
            } else {
                player.reset(screenWidth / 4, screenHeight / 2);
            }

            // Add initial towers with much more space
            for (int i = 0; i < 3; i++) {
                addTower(screenWidth + i * 600); // Increased from 400 to 600 for easier start
            }

            lastTime = System.currentTimeMillis();
        }

        private void addTower(int x) {
            // Make it progressively harder! But start much easier �
            // Gap size starts MUCH bigger and gets smaller very gradually
            int baseGapSize = 550; // Increased even more for comfortable play!
            int difficultyReduction = Math.min(score / 3, 100); // Very gentle difficulty increase
            int gapSize = Math.max(320, baseGapSize - difficultyReduction); // Minimum 320px gap for easier play

            int towerWidth = 80; // Slightly thinner towers for more room

            // Random gap position is much less extreme
            int extremeFactor = Math.min(score / 100, 30); // Much less extreme positioning
            int minGapY = 120 + extremeFactor;
            int maxGapY = screenHeight - gapSize - 120 - extremeFactor;
            int gapY = random.nextInt(maxGapY - minGapY) + minGapY;

            towers.add(new MoneyTower(x, 0, towerWidth, gapY));
            towers.add(new MoneyTower(x, gapY + gapSize, towerWidth, screenHeight - gapY - gapSize));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN && gameRunning) {
                player.jump(score); // Pass score for dynamic jump power
                return true;
            }
            return super.onTouchEvent(event);
        }

        private void updateGame() {
            if (!gameRunning) return;

            long currentTime = System.currentTimeMillis();
            float deltaTime = (currentTime - lastTime) / 1000f;
            lastTime = currentTime;

            // Update player
            player.update(deltaTime, score); // Pass score for dynamic difficulty

            // Check bounds
            if (player.y < 0 || player.y > screenHeight - player.height) {
                gameOver();
                return;
            }

            // Update towers
            for (int i = towers.size() - 1; i >= 0; i--) {
                MoneyTower tower = towers.get(i);
                tower.update(deltaTime, score); // Pass score for dynamic difficulty

                // Check collision
                if (player.collidesWith(tower)) {
                    gameOver();
                    return;
                }

                // Remove towers that are off screen
                if (tower.x + tower.width < 0) {
                    towers.remove(i);
                    if (tower.y == 0) { // Only count top towers for score
                        score++;
                        if (callbacks != null) {
                            callbacks.onScoreChanged(score);
                        }
                    }
                }
            }

            // Add new towers (give players MUCH more breathing room between towers)
            if (!towers.isEmpty()) {
                MoneyTower lastTower = towers.get(towers.size() - 1);
                int spacing = Math.max(350, 550 - (score * 2)); // MUCH more space between towers!
                if (lastTower.x < screenWidth - spacing) {
                    addTower(screenWidth + 150); // Even more initial space
                }
            }
        }

        private void gameOver() {
            gameRunning = false;
            if (callbacks != null) {
                callbacks.onGameOver(score);
            }
        }

        private void drawGame(Canvas canvas) {
            // Clear screen
            canvas.drawColor(backgroundColor);

            // Draw background gradient
            paint.setShader(new android.graphics.LinearGradient(
                0, 0, 0, screenHeight,
                new int[]{Color.parseColor("#87CEEB"), Color.parseColor("#E0F6FF")},
                null, android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, screenWidth, screenHeight, paint);
            paint.setShader(null);

            // Draw towers
            for (MoneyTower tower : towers) {
                tower.draw(canvas, paint, score); // Pass score for visual effects
            }

            // Draw player
            player.draw(canvas, paint, score); // Pass score for visual effects

            // Draw score
            paint.setColor(scoreColor);
            paint.setTextSize(60);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Score: " + score, screenWidth / 2, 100, paint);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            screenWidth = getWidth();
            screenHeight = getHeight();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            screenWidth = width;
            screenHeight = height;
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if (gameThread != null) {
                gameThread.setRunning(false);
                try {
                    gameThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public interface GameCallbacks {
            void onScoreChanged(int score);
            void onGameOver(int finalScore);
        }

        private class GameThread extends Thread {
            private SurfaceHolder surfaceHolder;
            private boolean running;
            private static final int FPS = 60;
            private static final long FRAME_TIME = 1000 / FPS;

            public GameThread(SurfaceHolder holder) {
                surfaceHolder = holder;
            }

            public void setRunning(boolean running) {
                this.running = running;
            }

            @Override
            public void run() {
                while (running) {
                    long startTime = System.currentTimeMillis();

                    Canvas canvas = null;
                    try {
                        canvas = surfaceHolder.lockCanvas();
                        if (canvas != null) {
                            updateGame();
                            drawGame(canvas);
                        }
                    } finally {
                        if (canvas != null) {
                            surfaceHolder.unlockCanvasAndPost(canvas);
                        }
                    }

                    long frameTime = System.currentTimeMillis() - startTime;
                    if (frameTime < FRAME_TIME) {
                        try {
                            Thread.sleep(FRAME_TIME - frameTime);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private static class Player {
        float x, y, velocityY;
        int width, height;
        Bitmap bitmap;
    private static final float BASE_GRAVITY = 900f; // Slightly higher for snappier vertical movement
    private static final float BASE_JUMP_VELOCITY = -650f; // Stronger jump to match faster fall speed

        public Player(Context context, float x, float y) {
            this.x = x;
            this.y = y;
            this.width = 80;
            this.height = 80;
            this.velocityY = 0;

            // Load and scale the flappy bird icon
            try {
                Bitmap original = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_flappy_bird);
                if (original != null) {
                    bitmap = Bitmap.createScaledBitmap(original, width, height, true);
                }
            } catch (Exception e) {
                // If flappy bird fails to load, create a simple colored bitmap
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                Paint paint = new Paint();
                paint.setColor(Color.YELLOW);
                canvas.drawCircle(width/2, height/2, width/2, paint);
            }
        }

        public void reset(float x, float y) {
            this.x = x;
            this.y = y;
            this.velocityY = 0;
        }

        public void update(float deltaTime, int currentScore) {
            // Gravity increases with score - very gently for comfortable play! �
            float gravityMultiplier = 1.0f + (currentScore * 0.001f); // Only 0.1% more gravity per point for gentler progression
            float currentGravity = BASE_GRAVITY * Math.min(gravityMultiplier, 1.3f); // Max 1.3x gravity for manageable difficulty

            velocityY += currentGravity * deltaTime;
            y += velocityY * deltaTime;
        }

        public void jump(int currentScore) {
            // Jump power decreases much more slowly with score 😭
            float jumpMultiplier = Math.max(0.85f, 1.0f - (currentScore * 0.00045f)); // Maintain control as speed ramps
            velocityY = BASE_JUMP_VELOCITY * jumpMultiplier;
        }

        public boolean collidesWith(MoneyTower tower) {
            // Much more forgiving collision detection - reduce hitbox significantly!
            float padding = 15f; // Give even more mercy!
            return x + padding < tower.x + tower.width - padding &&
                   x + width - padding > tower.x + padding &&
                   y + padding < tower.y + tower.height - padding &&
                   y + height - padding > tower.y + padding;
        }

        public void draw(Canvas canvas, Paint paint, int currentScore) {
            if (bitmap != null) {
                // Add stress effects at higher scores
                if (currentScore >= 500) {
                    // Slight red tint for stress
                    paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(new float[]{
                        1.2f, 0, 0, 0, 20,    // Red channel
                        0, 0.8f, 0, 0, 0,     // Green channel
                        0, 0, 0.8f, 0, 0,     // Blue channel
                        0, 0, 0, 1, 0         // Alpha channel
                    }));
                } else {
                    paint.setColorFilter(null);
                }

                canvas.drawBitmap(bitmap, x, y, paint);
                paint.setColorFilter(null); // Reset filter
            } else {
                // Fallback circle if bitmap fails to load
                paint.setColor(currentScore >= 500 ? Color.RED : Color.YELLOW);
                canvas.drawCircle(x + width/2, y + height/2, width/2, paint);
            }
        }
    }

    private static class MoneyTower {
        float x, y;
        int width, height;
        private static final float BASE_SPEED = 220f; // Faster scroll speed for livelier gameplay

        public MoneyTower(float x, float y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public void update(float deltaTime, int currentScore) {
            // Speed increases with score - but much more slowly! 😈
            float speedMultiplier = 1.0f + (currentScore * 0.005f); // 0.5% faster per point (was 1%)
            float currentSpeed = BASE_SPEED * Math.min(speedMultiplier, 2.0f); // Max 2x speed (was 3x)
            x -= currentSpeed * deltaTime;
        }

        public void draw(Canvas canvas, Paint paint, int currentScore) {
            // Change color based on progress for visual feedback
            if (currentScore >= 600) {
                paint.setColor(Color.parseColor("#FF0000")); // RED = DANGER ZONE
            } else if (currentScore >= 400) {
                paint.setColor(Color.parseColor("#FF6600")); // Orange = Getting tough
            } else if (currentScore >= 200) {
                paint.setColor(Color.parseColor("#FFD700")); // Gold = Normal
            } else {
                paint.setColor(Color.parseColor("#90EE90")); // Light green = Easy mode
            }

            RectF rect = new RectF(x, y, x + width, y + height);
            canvas.drawRoundRect(rect, 10, 10, paint);

            // Add stress indicators for higher scores
            if (currentScore >= 500) {
                // Add "stress lines" - jagged edges
                paint.setColor(Color.parseColor("#FF3333"));
                for (int i = 0; i < height; i += 20) {
                    canvas.drawLine(x - 5, y + i, x, y + i + 10, paint);
                    canvas.drawLine(x + width, y + i, x + width + 5, y + i + 10, paint);
                }
            }

            // Add money stack effect with difficulty-based symbols
            paint.setColor(Color.parseColor("#FFA500")); // Orange for depth
            for (int i = 0; i < height; i += 30) {
                if (y + i + 20 < y + height) {
                    canvas.drawRoundRect(x + 5, y + i, x + width - 5, y + i + 20, 5, 5, paint);
                }
            }

            // Add symbols based on difficulty
            paint.setColor(Color.parseColor("#000000")); // Black for text
            paint.setTextSize(16);
            paint.setTextAlign(Paint.Align.CENTER);

            String symbol = "$";
            if (currentScore >= 700) symbol = "💀"; // Death at high scores
            else if (currentScore >= 500) symbol = "😰"; // Stress
            else if (currentScore >= 300) symbol = "😅"; // Nervous
            else if (currentScore >= 100) symbol = "🤔"; // Thinking

            for (int i = 30; i < height; i += 60) {
                if (y + i < y + height - 30) {
                    canvas.drawText(symbol, x + width/2, y + i, paint);
                }
            }

            // Add shine effect (more intense with higher scores)
            if (currentScore >= 300) {
                paint.setColor(Color.parseColor("#FFFF00")); // Bright yellow for high scores
                canvas.drawRect(x + width - 15, y, x + width - 10, y + height, paint);
                canvas.drawRect(x + width - 25, y, x + width - 20, y + height, paint); // Double shine
            } else {
                paint.setColor(Color.parseColor("#FFFF99")); // Light yellow
                canvas.drawRect(x + width - 15, y, x + width - 10, y + height, paint);
            }
        }
    }
}
