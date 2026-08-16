package com.voxa.app.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.voxa.app.ui.theme.VoxaTheme
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
        
        // Dynamic intensity based on state color and volume - BOOSTED
        float intensity = glow * (0.45 + uVolume * 0.25);
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
        
        // Multi-frequency noise for "Energy Nebula" feel (Not a solid bulb)
        float noise = sin(uv.x * 5.0 + uTime * 1.5) * 0.04 + 
                      cos(uv.y * 7.0 - uTime * 1.2) * 0.04 +
                      sin((uv.x + uv.y) * 12.0 + uTime * 2.0) * 0.02;
        
        float baseRadius = (uState == 1.0) ? 0.2 : 0.15 + uVolume * 0.4;
        float dist = d - (baseRadius + noise);
        
        // Pure spectral falloff (No hard core edges)
        float glow = exp(-dist * 8.0); 
        float aura = exp(-dist * 3.0);
        
        // Color mixing: Use the primary color for the aura and a slightly
        // brighter version for the energy center, but avoid pure white.
        vec3 energyColor = mix(uColor, vec3(1.0, 1.0, 1.0), 0.4);
        vec3 color = uColor * aura + energyColor * glow;
        
        // Final alpha blend
        float finalAlpha = clamp(glow * 0.9 + aura * 0.1, 0.0, 1.0);
        
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
    val stateColor = when (state) {
        AssistantState.LISTENING -> Color(0xFF00FFCC)
        AssistantState.THINKING -> Color(0xFFC6BFFF)
        AssistantState.PROCESSING -> Color(0xFF24FFCD)
        else -> Color(0xFF00FFCC)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        VoxaBackgroundShaderTiramisu(modifier, state, volume)
    } else {
        // BEAUTIFUL FALLBACK FOR ANDROID 12 AND BELOW
        val infiniteTransition = rememberInfiniteTransition(label = "bg_fallback")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.7f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(5000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse"
        )
        
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0B0C10)) // Deep Obsidian base
                .drawBehind {
                    // Multi-layered gradient to mimic AGSL depth - BOOSTED ALPHA
                    val centerGlow = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            stateColor.copy(alpha = 0.45f * (0.8f + volume * 0.2f)), 
                            stateColor.copy(alpha = 0.15f), 
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.minDimension * pulseScale
                    )
                    drawRect(centerGlow)
                    
                    // Top-left subtle light leak
                    drawCircle(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(stateColor.copy(alpha = 0.08f), Color.Transparent),
                            radius = size.maxDimension * 0.5f
                        ),
                        center = Offset(0f, 0f),
                        radius = size.maxDimension * 0.5f
                    )
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
    val stateColor = when (state) {
        AssistantState.LISTENING -> Color(0xFF00FFCC)
        AssistantState.THINKING -> Color(0xFFC6BFFF)
        AssistantState.PROCESSING -> Color(0xFF24FFCD)
        else -> Color(0xFF00FFCC)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        VoxaVoiceOrbShaderTiramisu(modifier, state, volume)
    } else {
        // HIGH-QUALITY FALLBACK TO MATCH AGSL DEPTH
        val infiniteTransition = rememberInfiniteTransition(label = "orb_fallback")
        
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(2500, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )

        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    val volumeScale = 1f + (volume * 0.35f)
                    scaleX = scale * volumeScale
                    scaleY = scale * volumeScale
                }
                .drawBehind {
                    // NEBULA FALLBACK: Soft energy field (Not a solid bulb)
                    // 1. Energy Center (Brighter but not pure white)
                    val centerColor = Color(
                        red = (stateColor.red + 1f) / 2f,
                        green = (stateColor.green + 1f) / 2f,
                        blue = (stateColor.blue + 1f) / 2f,
                        alpha = 0.8f * alpha
                    )
                    
                    drawCircle(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            0.0f to centerColor,
                            0.4f to stateColor.copy(alpha = 0.4f * alpha),
                            1.0f to Color.Transparent,
                            center = center,
                            radius = size.minDimension / 2.5f
                        ),
                        radius = size.minDimension / 2.5f
                    )
                    
                    // 2. Diffuse Ambient Aura
                    drawCircle(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            0.0f to stateColor.copy(alpha = 0.12f * alpha),
                            1.0f to Color.Transparent,
                            center = center,
                            radius = size.minDimension / 1.2f
                        ),
                        radius = size.minDimension / 1.2f
                    )
                }
        )
    }
}

@Composable
fun VoxaWaveformShader(
    modifier: Modifier = Modifier,
    state: AssistantState = AssistantState.IDLE,
    volume: Float = 0f
) {
    val stateColor = when (state) {
        AssistantState.LISTENING -> Color(0xFF00FFCC)
        AssistantState.THINKING -> Color(0xFFC6BFFF)
        AssistantState.PROCESSING -> Color(0xFF24FFCD)
        else -> Color(0xFF00FFCC)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        VoxaWaveformShaderTiramisu(modifier, state, volume)
    } else {
        // UNIVERSAL HIGH-QUALITY WAVEFORM FALLBACK
        val infiniteTransition = rememberInfiniteTransition(label = "waveform_fallback")
        val time by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6.28f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "time"
        )

        Canvas(modifier = modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            
            // Draw 5 overlapping waves with different properties for "Electric" feel
            for (i in 0..4) {
                val path = Path()
                val freq = 1.2f + (i * 0.4f)
                val speed = time * (1f + (i * 0.2f))
                
                // Volume responsive amplitude
                val baseAmp = (15.dp.toPx() + volume * 40.dp.toPx())
                val amp = baseAmp / (1 + i * 0.5f)
                
                path.moveTo(0f, centerY)
                
                // Draw wave with envelope (fades at start/end)
                for (x in 0..width.toInt() step 4) {
                    val xf = x.toFloat()
                    val normX = xf / width
                    val envelope = kotlin.math.sin(normX * 3.14159f) // Gaussian-like fade
                    
                    val y = centerY + kotlin.math.sin(normX * freq * 6.28f + speed) * amp * envelope
                    path.lineTo(xf, y)
                }
                
                // Draw the main path
                drawPath(
                    path = path,
                    color = stateColor.copy(alpha = 0.5f / (i + 1)),
                    style = Stroke(width = (2.dp.toPx() - i * 0.3f).coerceAtLeast(1f))
                )
                
                // Add a glow effect for the first few waves
                if (i < 2) {
                    drawPath(
                        path = path,
                        color = stateColor.copy(alpha = 0.1f),
                        style = Stroke(width = 8.dp.toPx())
                    )
                }
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
        // SAFE FALLBACK IF SHADER COMPILATION FAILS ON TIRAMISU DEVICES
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(stateColor.copy(alpha = 0.2f), Color(0xFF0B0C10)),
                    )
                )
        )
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

@Preview(showBackground = true, backgroundColor = 0xFF0B0C10)
@Composable
fun VoxaShadersPreview() {
    VoxaTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            VoxaBackgroundShader(state = AssistantState.LISTENING, volume = 0.5f)
            
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                VoxaVoiceOrbShader(
                    modifier = Modifier.size(200.dp),
                    state = AssistantState.LISTENING,
                    volume = 0.5f
                )
                
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(50.dp))
                
                VoxaWaveformShader(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    state = AssistantState.LISTENING,
                    volume = 0.8f
                )
            }
        }
    }
}
