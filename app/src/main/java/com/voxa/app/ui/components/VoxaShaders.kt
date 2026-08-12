package com.voxa.app.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.voxa.app.ui.viewmodel.AssistantState

private const val BACKGROUND_SHADER_SRC = """
    uniform float uTime;
    uniform float2 uResolution;
    uniform float3 uColor;
    uniform float uVolume;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / uResolution;
        
        // Aspect ratio correction for full coverage
        float aspect = uResolution.y / uResolution.x;
        vec2 p = (uv - 0.5);
        p.y *= aspect * 0.5; // Squish vertical coordinate to make glow reach top/bottom faster
        
        float dist = length(p);
        
        // Ultra-wide falloff for full screen ambient wash
        float glow = exp(-dist * 1.2);
        
        vec3 obsidian = vec3(0.043, 0.047, 0.063);
        
        // Dynamic intensity based on state color and volume
        float intensity = glow * (0.25 + uVolume * 0.15);
        vec3 color = mix(obsidian, uColor, intensity);
        
        // Subtle moving noise/waves in the background wash
        float waves = sin(uv.x * 10.0 + uTime) * cos(uv.y * 10.0 - uTime) * 0.01;
        color += uColor * waves * glow;
        
        return half4(color, 1.0);
    }
"""

private const val ORB_SHADER_SRC = """
    uniform float uTime;
    uniform float2 uResolution;
    uniform float3 uColor;
    uniform float uVolume;
    uniform float uState;

    half4 main(float2 fragCoord) {
        vec2 uv = (fragCoord - 0.5 * uResolution) / min(uResolution.x, uResolution.y);
        float d = length(uv);
        
        float baseRadius = 0.2;
        if (uState == 0.0) baseRadius += uVolume * 0.25;
        if (uState == 1.0) baseRadius += sin(uTime * 4.0) * 0.05;
        
        float speed = (uState == 1.0) ? 6.0 : 2.5;
        float strength = (uState == 1.0) ? 0.1 : 0.05;
        
        float noise = sin(uv.x * 10.0 + uTime * speed) * strength +
                      cos(uv.y * 8.0 - uTime * speed * 0.8) * strength;
        
        float dist = d - (baseRadius + noise);
        
        float glow = exp(-dist * 2.0); 
        float core = 1.0 - smoothstep(0.0, 0.02, dist);
        
        vec3 obsidian = vec3(0.043, 0.047, 0.063);
        vec3 color = mix(obsidian, uColor, (glow * 2.0) + (core * 1.2));
        
        float highlight = pow(max(0.0, 1.0 - length(uv - vec2(0.12, 0.12))), 8.0);
        color += uColor * highlight * 0.6;
        
        float finalAlpha = clamp(core + (glow * 0.8), 0.0, 1.0);
        
        return half4(color, finalAlpha);
    }
"""

private const val WAVEFORM_SHADER_SRC = """
    uniform float uTime;
    uniform float2 uResolution;
    uniform float3 uColor;
    uniform float uVolume;
    uniform float uState;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / uResolution;
        
        float v = (uState == 0.0) ? uVolume : 0.05;
        float intensity = 0.0;
        float centerLine = 0.5;
        
        for(int i = -3; i <= 3; i++) {
            float f_i = float(i);
            float freq = 5.0 + abs(f_i) * 2.0;
            float speed = uTime * (4.0 + f_i);
            
            float envelope = sin(uv.x * 3.14159);
            float amplitude = (0.05 + v * 0.3) * envelope / (1.0 + abs(f_i) * 0.5);
            
            float h = centerLine + sin(uv.x * freq + speed) * amplitude;
            
            float dist = abs(uv.y - h);
            intensity += 0.001 / (dist + 0.003);
        }
        
        vec3 color = mix(uColor * 0.5, uColor, uv.x);
        float alpha = clamp(intensity, 0.0, 1.0);
        alpha *= smoothstep(0.0, 0.1, uv.x) * smoothstep(1.0, 0.9, uv.x);
        
        return half4(color * intensity, alpha);
    }
"""

@Composable
fun VoxaBackgroundShader(
    modifier: Modifier = Modifier,
    state: AssistantState = AssistantState.IDLE,
    volume: Float = 0f
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        VoxaBackgroundShaderTiramisu(modifier, state, volume)
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "background_fallback")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse"
        )
        
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0B0C10))
                .drawWithCache {
                    val brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(Color(0xFF00FFCC).copy(alpha = 0.15f * pulseScale), Color.Transparent),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.minDimension * 0.8f * pulseScale
                    )
                    onDrawBehind {
                        drawRect(brush)
                    }
                }
        )
    }
}

@Composable
fun VoxaVoiceOrbShader(
    modifier: Modifier = Modifier,
    state: AssistantState = AssistantState.IDLE,
    volume: Float = 0f
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        VoxaVoiceOrbShaderTiramisu(modifier, state, volume)
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "orb_fallback")
        val stateColor = when (state) {
            AssistantState.LISTENING -> Color(0xFF00FFCC)
            AssistantState.THINKING -> Color(0xFFC6BFFF)
            AssistantState.PROCESSING -> Color(0xFF24FFCD)
            else -> Color(0xFF00FFCC)
        }
        
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )

        Box(
            modifier = modifier
                .graphicsLayer {
                    val volumeScale = 1f + (volume * 0.4f)
                    scaleX = scale * volumeScale
                    scaleY = scale * volumeScale
                }
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(stateColor.copy(alpha = 0.6f * alpha), stateColor.copy(alpha = 0f)),
                    ),
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
    }
}

@Composable
fun VoxaWaveformShader(
    modifier: Modifier = Modifier,
    state: AssistantState = AssistantState.IDLE,
    volume: Float = 0f
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        VoxaWaveformShaderTiramisu(modifier, state, volume)
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "waveform_fallback")
        val time by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6.28f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "time"
        )
        
        val stateColor = when (state) {
            AssistantState.LISTENING -> Color(0xFF00FFCC)
            AssistantState.THINKING -> Color(0xFFC6BFFF)
            AssistantState.PROCESSING -> Color(0xFF24FFCD)
            else -> Color(0xFF00FFCC)
        }

        Canvas(modifier = modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            
            for (i in 0..2) {
                val path = Path()
                val freq = 1.5f + i * 0.5f
                val amp = (10.dp.toPx() + volume * 20.dp.toPx()) / (1 + i)
                
                path.moveTo(0f, centerY)
                for (x in 0..width.toInt() step 5) {
                    val x_f = x.toFloat()
                    val normalizedX = x_f / width
                    val envelope = kotlin.math.sin(normalizedX * 3.14159f)
                    val y = centerY + kotlin.math.sin(normalizedX * freq * 6.28f + time + i) * amp * envelope
                    path.lineTo(x_f, y)
                }
                
                drawPath(
                    path = path,
                    color = stateColor.copy(alpha = 0.6f / (i + 1)),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun VoxaBackgroundShaderTiramisu(
    modifier: Modifier,
    state: AssistantState,
    volume: Float
) {
    val time by produceState(0f) {
        while (true) {
            withInfiniteAnimationFrameMillis {
                value = it / 1000f
            }
        }
    }

    // Use a top-level or static-like reference for RuntimeShader to avoid compilation lag
    val shader = remember { 
        VoxaShaderCache.getBackgroundShader()
    }
    
    val stateColor = when (state) {
        AssistantState.LISTENING -> Color(0xFF00FFCC)
        AssistantState.THINKING -> Color(0xFFC6BFFF)
        AssistantState.PROCESSING -> Color(0xFF24FFCD)
        else -> Color(0xFF00FFCC)
    }

    if (shader != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .drawBehind {
                    shader.setFloatUniform("uTime", time)
                    shader.setFloatUniform("uResolution", size.width, size.height)
                    shader.setFloatUniform("uColor", stateColor.red, stateColor.green, stateColor.blue)
                    shader.setFloatUniform("uVolume", volume)
                    drawRect(ShaderBrush(shader))
                }
        )
    } else {
        Box(modifier = modifier.fillMaxSize().background(Color(0xFF0B0C10)))
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun VoxaVoiceOrbShaderTiramisu(
    modifier: Modifier,
    state: AssistantState,
    volume: Float
) {
    val time by produceState(0f) {
        while (true) {
            withInfiniteAnimationFrameMillis {
                value = it / 1000f
            }
        }
    }

    val shader = remember { 
        VoxaShaderCache.getOrbShader()
    }

    val stateColor = when (state) {
        AssistantState.LISTENING -> Color(0xFF00FFCC)
        AssistantState.THINKING -> Color(0xFFC6BFFF)
        AssistantState.PROCESSING -> Color(0xFF24FFCD)
        else -> Color(0xFF00FFCC)
    }
    
    val stateValue = when (state) {
        AssistantState.LISTENING -> 0f
        AssistantState.THINKING -> 1f
        AssistantState.PROCESSING -> 2f
        else -> 0f
    }

    if (shader != null) {
        Box(
            modifier = modifier
                .drawBehind {
                    shader.setFloatUniform("uTime", time)
                    shader.setFloatUniform("uResolution", size.width, size.height)
                    shader.setFloatUniform("uColor", stateColor.red, stateColor.green, stateColor.blue)
                    shader.setFloatUniform("uVolume", volume)
                    shader.setFloatUniform("uState", stateValue)
                    drawRect(ShaderBrush(shader))
                }
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun VoxaWaveformShaderTiramisu(
    modifier: Modifier,
    state: AssistantState,
    volume: Float
) {
    val time by produceState(0f) {
        while (true) {
            withInfiniteAnimationFrameMillis {
                value = it / 1000f
            }
        }
    }

    val shader = remember { 
        VoxaShaderCache.getWaveformShader()
    }
    
    val stateColor = when (state) {
        AssistantState.LISTENING -> Color(0xFF00FFCC)
        AssistantState.THINKING -> Color(0xFFC6BFFF)
        AssistantState.PROCESSING -> Color(0xFF24FFCD)
        else -> Color(0xFF00FFCC)
    }
    
    val stateValue = when (state) {
        AssistantState.LISTENING -> 0f
        AssistantState.THINKING -> 1f
        AssistantState.PROCESSING -> 2f
        else -> 0f
    }

    if (shader != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .drawBehind {
                    shader.setFloatUniform("uTime", time)
                    shader.setFloatUniform("uResolution", size.width, size.height)
                    shader.setFloatUniform("uColor", stateColor.red, stateColor.green, stateColor.blue)
                    shader.setFloatUniform("uVolume", volume)
                    shader.setFloatUniform("uState", stateValue)
                    drawRect(ShaderBrush(shader))
                }
        )
    }
}

/**
 * Singleton cache to prevent re-compilation of AGSL shaders during runtime navigation.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object VoxaShaderCache {
    private var backgroundShader: RuntimeShader? = null
    private var orbShader: RuntimeShader? = null
    private var waveformShader: RuntimeShader? = null

    fun getBackgroundShader(): RuntimeShader? {
        if (backgroundShader == null) {
            try { backgroundShader = RuntimeShader(BACKGROUND_SHADER_SRC) } catch (e: Exception) {}
        }
        return backgroundShader
    }

    fun getOrbShader(): RuntimeShader? {
        if (orbShader == null) {
            try { orbShader = RuntimeShader(ORB_SHADER_SRC) } catch (e: Exception) {}
        }
        return orbShader
    }

    fun getWaveformShader(): RuntimeShader? {
        if (waveformShader == null) {
            try { waveformShader = RuntimeShader(WAVEFORM_SHADER_SRC) } catch (e: Exception) {}
        }
        return waveformShader
    }
}
