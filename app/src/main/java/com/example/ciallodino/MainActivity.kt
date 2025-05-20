package com.example.ciallodino


import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.ciallodino.R


class MainActivity : ComponentActivity() {
    private var mediaPlayerService: MediaPlayerService? = null
    private var isServiceBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MediaPlayerService.LocalBinder
            mediaPlayerService = binder.service
            isServiceBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 绑定服务
        bindService(
            Intent(this, MediaPlayerService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )

        setContent {
            MaterialTheme {
                DinoGameApp(
                    onJump = { playJumpSound() }
                )
            }
        }
    }

    private fun playJumpSound() {
        mediaPlayerService?.playJumpSound()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 解绑服务
        if (isServiceBound) {
            unbindService(connection)
            isServiceBound = false
        }
    }
}

/**
 * 游戏常量配置类，集中管理游戏中的各种参数和数值
 */
object GameConstants {
    // 恐龙角色相关参数
    const val DINO_SIZE = 150f              // 恐龙图像绘制尺寸（像素）
    const val DINO_JUMP_HEIGHT = 400f       // 恐龙跳跃的最大高度（像素）
    const val DINO_JUMP_DURATION = 60       // 恐龙完成一次跳跃的总帧数

    // 障碍物相关参数
    const val OBSTACLE_SIZE = 150f          // 障碍物图像绘制尺寸（像素）
    const val OBSTACLE_SPEED = 10f          // 障碍物向左移动的速度（像素/帧）
    const val OBSTACLE_SPAWN_CHANCE = 2     // 每帧生成新障碍物的概率（百分比）

    // 游戏世界布局参数
    const val GROUND_HEIGHT_RATIO = 0.7f    // 地面位置占屏幕高度的比例（0.7表示70%高度处）
    const val DINO_X_POSITION_RATIO = 0.2f  // 恐龙在水平方向的固定位置比例（0.2表示20%宽度处）

    // 游戏循环控制参数
    const val GAME_LOOP_DELAY_MS = 30L      // 游戏主循环每次迭代的延迟时间（毫秒），控制游戏速度
}

@Composable
fun DinoGameApp(    onJump: () -> Unit = {}
) {
    // 屏幕尺寸计算
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val groundY = screenHeightPx * GameConstants.GROUND_HEIGHT_RATIO

    // 游戏资源
    val context = LocalContext.current
    val dinoBitmap = ImageBitmap.imageResource(context.resources,R.drawable.dino)
    val obstacleBitmap = ImageBitmap.imageResource(context.resources, R.drawable.obstacle)

    // 游戏状态
    var gameState by remember { mutableStateOf(GameState.READY) }
    var score by remember { mutableIntStateOf(0) }
    var highScore by remember { mutableIntStateOf(0) }

    // 恐龙状态
    var dinoY by remember { mutableFloatStateOf(groundY - GameConstants.DINO_SIZE) }
    var isJumping by remember { mutableStateOf(false) }
    var jumpCount by remember { mutableIntStateOf(0) }

    // 障碍物状态
    val obstacles = remember { mutableStateListOf<Obstacle>() }

    // 游戏主循环
    LaunchedEffect(gameState) {
        if (gameState == GameState.PLAYING) {
            var lastUpdateTime = System.currentTimeMillis()

            while (gameState == GameState.PLAYING) {
                val currentTime = System.currentTimeMillis()
                val deltaTime = currentTime - lastUpdateTime
                lastUpdateTime = currentTime

                // 更新分数
                score++
                highScore = maxOf(score, highScore)

                // 处理恐龙跳跃
                if (isJumping) {
                    if (jumpCount == 0) {
                        // 跳跃开始时触发音效
                        onJump()
                    }
                    if (jumpCount < GameConstants.DINO_JUMP_DURATION / 2) {
                        dinoY -= GameConstants.DINO_JUMP_HEIGHT / (GameConstants.DINO_JUMP_DURATION / 2)
                    } else if (jumpCount < GameConstants.DINO_JUMP_DURATION) {
                        dinoY += GameConstants.DINO_JUMP_HEIGHT / (GameConstants.DINO_JUMP_DURATION / 2)
                    } else {
                        isJumping = false
                        jumpCount = 0
                        dinoY = groundY - GameConstants.DINO_SIZE // 确保恐龙回到地面
                    }
                    jumpCount++
                }

                // 移动障碍物
                obstacles.removeAll { obstacle ->
                    obstacle.x + GameConstants.OBSTACLE_SIZE < 0
                }
                obstacles.forEach { obstacle ->
                    obstacle.x -= GameConstants.OBSTACLE_SPEED
                }

                // 生成新障碍物
                val minObstacleGap = GameConstants.OBSTACLE_SIZE * 3  // 最小间距为障碍物宽度的3倍
                val lastObstacle = obstacles.lastOrNull()
                if (Random.nextInt(100) < GameConstants.OBSTACLE_SPAWN_CHANCE &&
                    (lastObstacle == null || lastObstacle.x < screenWidthPx - minObstacleGap)
                ) {
                    obstacles.add(
                        Obstacle(
                            x = screenWidthPx,
                            y = groundY - GameConstants.OBSTACLE_SIZE,
                            width = GameConstants.OBSTACLE_SIZE,
                            height = GameConstants.OBSTACLE_SIZE
                        )
                    )
                }

                // 碰撞检测
                if (checkCollision(
                        dinoX = screenWidthPx * GameConstants.DINO_X_POSITION_RATIO,
                        dinoY = dinoY,
                        dinoWidth = GameConstants.DINO_SIZE,
                        dinoHeight = GameConstants.DINO_SIZE,
                        obstacles = obstacles
                    )) {
                    gameState = GameState.GAME_OVER
                }

                // 控制游戏速度
                delay(maxOf(0, GameConstants.GAME_LOOP_DELAY_MS - deltaTime))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable {
                // 点击屏幕开始游戏或让恐龙跳跃
                when (gameState) {
                    GameState.READY, GameState.GAME_OVER -> {
                        // 开始或重新开始游戏
                        gameState = GameState.PLAYING
                        score = 0
                        dinoY = groundY - GameConstants.DINO_SIZE
                        obstacles.clear()
                        Log.d("GameControl", "Game started by screen tap")
                    }
                    GameState.PLAYING -> {
                        // 游戏进行中，让恐龙跳跃
                        if (!isJumping) {
                            isJumping = true
                            jumpCount = 0
                            Log.d("GameControl", "Dino jump by screen tap")
                        }
                    }
                }
            }
    ) {
        // 游戏画布
        GameCanvas(
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            groundY = groundY,
            dinoBitmap = dinoBitmap,
            dinoY = dinoY,
            obstacles = obstacles,
            obstacleBitmap = obstacleBitmap
        )

        // 分数显示
        ScoreDisplay(score = score, highScore = highScore)

        // 游戏状态界面
        if (gameState != GameState.PLAYING) {
            GameStatusOverlay(gameState = gameState)
        }
    }
}

@Composable
private fun GameCanvas(
    screenWidthPx: Float,
    screenHeightPx: Float,
    groundY: Float,
    dinoBitmap: ImageBitmap,
    dinoY: Float,
    obstacles: List<Obstacle>,
    obstacleBitmap: ImageBitmap
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // 绘制地面
        drawLine(
            color = Color.Gray,
            start = Offset(0f, groundY),
            end = Offset(size.width, groundY),
            strokeWidth = 3f
        )

        // 绘制恐龙
        drawScaledImage(
            image = dinoBitmap,
            topLeft = Offset(screenWidthPx * GameConstants.DINO_X_POSITION_RATIO, dinoY),
            width = GameConstants.DINO_SIZE,
            height = GameConstants.DINO_SIZE
        )

        // 绘制障碍物
        obstacles.forEach { obstacle ->
            drawScaledImage(
                image = obstacleBitmap,
                topLeft = Offset(obstacle.x, obstacle.y),
                width = GameConstants.OBSTACLE_SIZE,
                height = GameConstants.OBSTACLE_SIZE
            )
        }
    }
}

fun DrawScope.drawScaledImage(
    image: ImageBitmap,
    topLeft: Offset,
    width: Float,
    height: Float
) {
    drawImage(
        image = image,
        dstOffset = IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
        dstSize = IntSize(width.toInt(), height.toInt())
    )
}

@Composable
private fun ScoreDisplay(score: Int, highScore: Int) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopEnd)
        ) {
            Text(
                text = "分数: $score",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "最高分: $highScore",
                fontSize = 16.sp,
                color = Color.Black
            )
        }
    }
}

@Composable
private fun GameStatusOverlay(gameState: GameState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (gameState) {
                    GameState.READY -> "点击屏幕开始游戏"
                    GameState.GAME_OVER -> "游戏结束!"
                    else -> ""
                },
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = when (gameState) {
                    GameState.READY -> "轻触任意位置开始"
                    GameState.GAME_OVER -> "轻触任意位置重新开始"
                    else -> ""
                },
                fontSize = 18.sp,
                color = Color.White
            )
        }
    }
}

data class Obstacle(
    var x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

enum class GameState {
    READY, PLAYING, GAME_OVER
}

/**
 * 检测恐龙与障碍物之间的碰撞（宽松模式）
 *
 * 通过减小碰撞体尺寸实现更宽松的碰撞检测，
 * 恐龙和障碍物需要更接近才能触发碰撞。
 */
private fun checkCollision(
    dinoX: Float,
    dinoY: Float,
    dinoWidth: Float,
    dinoHeight: Float,
    obstacles: List<Obstacle>
): Boolean {
    // 碰撞体缩小系数（值越大碰撞检测越宽松）
    val collisionRelaxFactor = 0.7f

    // 计算宽松模式下的碰撞体尺寸
    val relaxedDinoWidth = dinoWidth * collisionRelaxFactor
    val relaxedDinoHeight = dinoHeight * collisionRelaxFactor
    val relaxedDinoX = dinoX + (dinoWidth - relaxedDinoWidth) / 2
    val relaxedDinoY = dinoY + (dinoHeight - relaxedDinoHeight) / 2

    return obstacles.any { obstacle ->
        // 障碍物碰撞体同样缩小
        val relaxedObstacleWidth = obstacle.width * collisionRelaxFactor
        val relaxedObstacleHeight = obstacle.height * collisionRelaxFactor
        val relaxedObstacleX = obstacle.x + (obstacle.width - relaxedObstacleWidth) / 2
        val relaxedObstacleY = obstacle.y + (obstacle.height - relaxedObstacleHeight) / 2

        // 使用缩小后的碰撞体进行检测
        relaxedDinoX + relaxedDinoWidth > relaxedObstacleX &&
                relaxedDinoX < relaxedObstacleX + relaxedObstacleWidth &&
                relaxedDinoY + relaxedDinoHeight > relaxedObstacleY &&
                relaxedDinoY < relaxedObstacleY + relaxedObstacleHeight
    }
}