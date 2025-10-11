package tk.therealsuji.vtopchennai.activities;

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

import tk.therealsuji.vtopchennai.R;

public class FlappyGameActivity extends AppCompatActivity {

    private GameView gameView;
    private TextView scoreText, highScoreText, gameOverText, motivationText, trollMessage, cgpaText;
    private Button playButton, homeButton;
    private LinearLayout gameOverLayout, startInstructions;
    private ProgressBar cgpaProgressBar;

    private SharedPreferences preferences;
    private static final String PREF_HIGH_SCORE = "flappy_high_score";
    private static final int IMPOSSIBLE_TARGET = 750; // 750 points for 9.0 CGPA 😈

    // Progressive scoring system - starts from 6.0 CGPA (because we're generous! 😈)
    private static final int[] CGPA_THRESHOLDS = {
        0,    // Start at 6.0 CGPA - Everyone deserves a head start!
        30,   // 6.5 CGPA - 30 points (still easy)
        70,   // 7.0 CGPA - 40 more points
        120,  // 7.5 CGPA - 50 more points
        180,  // 8.0 CGPA - 60 more points
        250,  // 8.5 CGPA - 70 more points (getting tougher)
        330,  // 8.6 CGPA - 80 more points
        420,  // 8.7 CGPA - 90 more points
        520,  // 8.8 CGPA - 100 more points
        630,  // 8.9 CGPA - 110 more points (brutal zone)
        750   // 9.0 CGPA - 120 more points (IMPOSSIBLE!)
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
                        // They actually did it?!
                        gameOverText.setText("IMPOSSIBLE! 🤯\nYou broke the matrix!");
                        trollMessage.setText("Wait... how did you...? 🤖\nThis wasn't supposed to happen!\n\nCongratulations! You've achieved:\n✅ 9.0+ CGPA\n✅ Friends who believe in you\n✅ 8 hours of sleep\n✅ 50 LPA job offer\n\n...in a mobile game. Reality still pending! 😂");
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
        motivationText.setText("Starting strong at 6.0 CGPA! Let's climb higher! �");
        gameView.startGame();
    }

    // Calculate CGPA based on progressive thresholds - STARTING FROM 6.0!
    private float calculateCGPA(int score) {
        for (int i = 0; i < CGPA_THRESHOLDS.length; i++) {
            if (score < CGPA_THRESHOLDS[i]) {
                if (i == 0) {
                    // Before first threshold - still start at 6.0 base
                    return 6.0f + (score / 30.0f) * 0.5f; // 6.0 to 6.5
                } else {
                    // Between thresholds - interpolate
                    int prevThreshold = CGPA_THRESHOLDS[i-1];
                    float progress = (float)(score - prevThreshold) / (CGPA_THRESHOLDS[i] - prevThreshold);
                    return 6.0f + (i * 0.5f) + (progress * 0.5f); // 0.5 CGPA increment per threshold
                }
            }
        }
        // Beyond all thresholds - they made it!
        return 9.0f + (score - CGPA_THRESHOLDS[CGPA_THRESHOLDS.length-1]) / 100.0f;
    }

    private void updateMotivationText(int score) {
        float cgpa = calculateCGPA(score);

        if (score >= 630) {
            motivationText.setText("8.9 CGPA! SO CLOSE TO PERFECTION! 😱✨");
        } else if (score >= 520) {
            motivationText.setText("8.8 CGPA! Your parents are crying with joy! 🥹");
        } else if (score >= 420) {
            motivationText.setText("8.7 CGPA! Professor's favorite student! 🏆");
        } else if (score >= 330) {
            motivationText.setText("8.6 CGPA! Excellence is your middle name! 💫");
        } else if (score >= 250) {
            motivationText.setText("8.5 CGPA! You're absolutely crushing it! 🔥");
        } else if (score >= 180) {
            motivationText.setText("8.0 CGPA! Outstanding performance! ⭐");
        } else if (score >= 120) {
            motivationText.setText("7.5 CGPA! Above average superstar! �");
        } else if (score >= 70) {
            motivationText.setText("7.0 CGPA! Great job! Keep climbing! 💪");
        } else if (score >= 30) {
            motivationText.setText("6.5 CGPA! Nice start! You're doing well! 📈");
        } else {
            motivationText.setText("6.0 CGPA! Everyone starts somewhere decent! �");
        }
    }

    private String getAchievementMessage(int score) {
        float cgpa = calculateCGPA(score);
        return String.format("Final CGPA: %.1f", cgpa);
    }

    private String getProgressiveTrollMessage(int score) {
        float cgpa = calculateCGPA(score);

        if (score >= 630) {
            return "8.9 CGPA! 😤 Oh wow, you're so close to perfection!\nToo bad the universe has other plans.\n\nJust 120 more points! What could possibly go wrong? 🎭\n\nHint: Everything. Everything can go wrong. 💀";
        } else if (score >= 520) {
            return "8.8 CGPA! 🙄 LinkedIn influencer vibes!\n'Blessed to announce...' energy detected!\n\nCareful, your ego is showing! 😏\n\nBut hey, at least you're consistent... consistently annoying! 🤡";
        } else if (score >= 420) {
            return "8.7 CGPA! 🤓 Teacher's pet achievement unlocked!\nBet you remind the teacher about homework too!\n\nNerd level: Maximum! �\n\nDo you even have friends or just study buddies? 👻";
        } else if (score >= 330) {
            return "8.6 CGPA! 😎 Excellence? More like 'excellently boring'!\nWhen was the last time you touched grass?\n\nSocial life: 404 Not Found! 🤖\n\nYour calculator sees you more than your family! 💻";
        } else if (score >= 250) {
            return "8.5 CGPA! 😏 Outstanding student or outstanding loser?\nYour resume is thicc, your social skills... not so much!\n\nCongrats on your academic prison! 🏛️\n\nFun fact: Books don't hug back! 📚";
        } else if (score >= 180) {
            return "8.0 CGPA! 🎭 Elite club member!\nWelcome to the 'I peaked in college' support group!\n\nReality check incoming... 💥\n\nSpending 6.0 CGPA like a pro now! 🎪";
        } else if (score >= 120) {
            return "7.5 CGPA! 💨 Above average superstar!\nYour future is looking 'decent' at best!\n\nTop tier? More like 'trying tier'! 🎯\n\nBut hey, you're above the starting line! 📈";
        } else if (score >= 70) {
            return "7.0 CGPA! 🎪 Solid performance... for a try-hard!\nYou've climbed from 6.0 to mediocrity plus!\n\nCongratulations on being... adequate! 👏\n\nYour parents' expectations: Cautiously optimistic! 🤞";
        } else if (score >= 30) {
            return "6.5 CGPA! 🤷‍♂️ Baby steps from the starting line!\nYou've escaped the 6.0 baseline into... slightly above baseline!\n\nAim higher? Or aim for a personality! 🎯\n\nProgress is progress... I guess? 🐌";
        } else {
            return "6.0 CGPA! 🌱 Everyone starts at 6.0... some stay there!\nAt least you're not below average... yet!\n\nThe real journey? Proving you deserve this headstart! 🚀\n\nPro tip: Actually try playing the game! �";
        }
    }

    private String getDeathTrollMessage(int score) {
        float cgpa = calculateCGPA(score);

        String[] deathQuotes = {
            "\"I'll just wing this exam\" 🪶\n- And you crashed like a bird into a window",

            "\"Sleep is overrated anyway\" 😴\n- Narrator: Your brain cells disagreed",

            "\"I work better under pressure\" ⚡\n- *Pressure crushes you completely*",

            "\"Just one more episode...\" 📺\n- Famous last words before academic death",

            "\"I'll start studying tomorrow\" 📅\n- Tomorrow: Still procrastinating",

            "\"My notes are somewhere...\" 📝\n- Spoiler: In another dimension",

            "\"The professor loves me\" 💕\n- Professor: 'Who are you again?'",

            "\"I can study everything in one night\" 🌙\n- Narrator: They could not",

            "\"Attendance is just a number\" 📊\n- So is zero, apparently",

            "\"I'm a visual learner\" 👀\n- *Stares at F grade intensely*",

            "\"I have a photographic memory\" 📷\n- Too bad you forgot to load the film",

            "\"College is just a piece of paper\" 📄\n- So is a tissue, but this one's worth ₹5 lakhs",

            "\"Grades don't define me\" 💅\n- Narrator: The placement committee disagrees",

            "\"I learn better by doing\" 🤲\n- *Proceeds to do absolutely nothing*",

            "\"I'm street smart, not book smart\" 🛣️\n- Streets don't pay engineering salaries though"
        };

        // Add specific BRUTAL messages based on CGPA achieved
        if (cgpa >= 8.5f) {
            return "SO CLOSE TO GREATNESS! 😭\n\nYou were one step away from legend status!\nInstead, you're a legend of failure!\n\n" + deathQuotes[score % deathQuotes.length];
        } else if (cgpa >= 8.0f) {
            return "SOLID PERFORMANCE... NOT! 🎭\n\nYou proved you can almost succeed!\nAlmost doesn't get you a job though!\n\n" + deathQuotes[score % deathQuotes.length];
        } else if (cgpa >= 7.0f) {
            return "DECENT PROGRESS! 🤡\n\nYou climbed from 6.0 to mediocrity!\nTime to accept your moderate fate!\n\n" + deathQuotes[score % deathQuotes.length];
        } else if (cgpa >= 6.5f) {
            return "BABY STEPS... TO NOWHERE! 💩\n\nYou barely moved from the starting line!\nKeep the minimal effort going!\n\n" + deathQuotes[score % deathQuotes.length];
        } else {
            return "NEVER LEFT THE STARTING LINE! 🪦\n\nYou started at 6.0 and somehow went backwards!\nComeback stories are just fairy tales!\n\n" + deathQuotes[score % deathQuotes.length];
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
            // Make it progressively harder! But start much easier 😈
            // Gap size starts MUCH bigger and gets smaller very gradually
            int baseGapSize = 500; // Increased from 400 - much more forgiving!
            int difficultyReduction = Math.min(score / 2, 120); // Even slower difficulty increase
            int gapSize = Math.max(280, baseGapSize - difficultyReduction); // Minimum 280px gap (was 200px)

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
        private static final float BASE_GRAVITY = 800f; // Reduced from 1000f for easier control
        private static final float BASE_JUMP_VELOCITY = -450f; // Increased from -400f for stronger jumps

        public Player(Context context, float x, float y) {
            this.x = x;
            this.y = y;
            this.width = 80;
            this.height = 80;
            this.velocityY = 0;

            // Load and scale the community icon
            Bitmap original = BitmapFactory.decodeResource(context.getResources(), R.drawable.community_icon);
            bitmap = Bitmap.createScaledBitmap(original, width, height, true);
        }

        public void reset(float x, float y) {
            this.x = x;
            this.y = y;
            this.velocityY = 0;
        }

        public void update(float deltaTime, int currentScore) {
            // Gravity increases with score - but much more slowly! 😈
            float gravityMultiplier = 1.0f + (currentScore * 0.002f); // 0.2% more gravity per point (was 0.5%)
            float currentGravity = BASE_GRAVITY * Math.min(gravityMultiplier, 1.5f); // Max 1.5x gravity (was 2x)

            velocityY += currentGravity * deltaTime;
            y += velocityY * deltaTime;
        }

        public void jump(int currentScore) {
            // Jump power decreases much more slowly with score 😭
            float jumpMultiplier = Math.max(0.9f, 1.0f - (currentScore * 0.0005f)); // Much less weakness
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
        private static final float BASE_SPEED = 150f; // Reduced from 200f for easier gameplay

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
            // Change color based on difficulty for extra trolling
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
