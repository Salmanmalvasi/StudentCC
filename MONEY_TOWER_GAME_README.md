# 🎮 Money Tower Game

A fun Flappy Bird-style mini-game integrated into the StudentCC profile section!

## 🎯 Game Features

### 🐥 Player Character
- **Community Icon Bird**: Uses the StudentCC community icon as the player character
- **Physics-Based Movement**: Gravity and jump mechanics for realistic flight
- **Smooth Animation**: 60 FPS gameplay for smooth experience

### 💰 Obstacles
- **Money Towers**: Navigate through towers made of stacked gold coins
- **Dynamic Difficulty**: Randomly generated tower gaps
- **Visual Effects**: Shimmering gold towers with dollar sign details

### 🏆 Scoring System
- **Real-time Score**: Score increases as you pass through tower gaps
- **High Score Tracking**: Persistent high score storage
- **Achievement Notifications**: Special messages for new high scores

### 🎨 Visual Design
- **Material Design**: Consistent with app's design language
- **Gradient Background**: Beautiful sky-to-ground gradient
- **Particle Effects**: Money tower shine effects
- **Responsive UI**: Adapts to different screen sizes

## 🎮 How to Play

1. **Launch**: Tap "Money Tower Game" in the Profile section
2. **Start**: Tap anywhere on the screen to begin
3. **Control**: Tap to make the community icon fly up
4. **Objective**: Navigate through gaps in the money towers
5. **Score**: Earn points for each tower you pass
6. **Challenge**: Beat your high score!

## 🛠️ Technical Implementation

### Architecture
- **Activity-Based**: `FlappyGameActivity` with custom SurfaceView
- **Game Loop**: 60 FPS game thread with proper lifecycle management
- **Collision Detection**: Precise rectangle-based collision system
- **State Management**: Game state tracking with proper pause/resume

### Performance Optimizations
- **Efficient Rendering**: Canvas-based drawing with optimized paint objects
- **Memory Management**: Proper bitmap recycling and object pooling
- **Thread Safety**: Synchronized game state updates
- **Battery Friendly**: Pauses when activity is not visible

### Graphics System
- **Custom Drawing**: Hand-drawn money towers with stacking effects
- **Bitmap Scaling**: Optimized community icon scaling
- **Color Theming**: Adapts to app's current theme colors
- **Screen Adaptation**: Responsive layout for different screen sizes

## 🎯 Game Mechanics

### Physics
- **Gravity**: 1000 units/second² downward acceleration
- **Jump Force**: -400 units/second upward velocity
- **Speed**: Towers move at 200 units/second

### Difficulty Scaling
- **Gap Size**: 300 pixel gaps between tower segments
- **Tower Spacing**: 400 pixels between tower sets
- **Random Heights**: Varying tower heights for challenge

### Collision System
- **Bounding Boxes**: Rectangle-based collision detection
- **Precise Timing**: Frame-accurate collision checking
- **Edge Cases**: Proper handling of screen boundaries

## 🔧 Configuration

### SharedPreferences
- **High Score**: Stored in "FlappyGame" preferences
- **Key**: "flappy_high_score"
- **Persistence**: Survives app restarts and updates

### Customization Options
- **Difficulty**: Easily adjustable gap sizes and speeds
- **Visuals**: Configurable colors and effects
- **Sound**: Ready for sound effect integration

## 🚀 Future Enhancements

### Planned Features
- **Sound Effects**: Jump, score, and collision sounds
- **Particle Effects**: Coin sparkles and explosion effects
- **Power-ups**: Special abilities and bonuses
- **Leaderboards**: Global high score competition
- **Achievements**: Unlock system with rewards
- **Themes**: Multiple visual themes to unlock

### Technical Improvements
- **Sprite Animations**: Animated character sprites
- **Better Physics**: More realistic physics simulation
- **Procedural Generation**: Dynamic background elements
- **Performance Monitoring**: FPS counter and optimization metrics

## 📱 Integration

### Profile Section
- Located in the "Personal" section of the profile
- Icon: Custom game controller icon
- Description: "Navigate through towers of money with our mascot!"
- Quick access from main app navigation

### User Experience
- **Seamless Integration**: Matches app's design language
- **Quick Access**: Easy to find and launch
- **Social Sharing**: High scores can be shared (future feature)
- **Progress Tracking**: Statistics and achievements (future feature)

## 🎨 Design Philosophy

### Visual Consistency
- Uses app's primary and secondary colors
- Follows Material Design principles
- Consistent typography and spacing
- Smooth animations and transitions

### User Engagement
- Quick, casual gameplay sessions
- Challenging but fair difficulty curve
- Rewarding progression system
- Memorable visual style

The Money Tower Game adds a fun, engaging element to the StudentCC app while maintaining the professional design and user experience standards!
