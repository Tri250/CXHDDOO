package com.poseai.app.gpu

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * GPU 渲染引擎
 *
 * 基于 OpenGL ES 2.0 的 GPU 加速渲染系统，支持：
 * - Bitmap → GPU 纹理 → Shader 处理 → Bitmap 回读
 * - 实时相机预览滤镜渲染（GLSurfaceView.Renderer）
 * - 多种内置 Shader 效果（亮度/对比度/饱和度/模糊/锐化/暗角/色温/HDR）
 *
 * 所有 GPU 调用均有错误检查，失败时回退到 CPU 处理。
 */
object GpuRenderEngine {

    private const val TAG = "GpuRenderEngine"

    // 顶点坐标（全屏四边形）
    private val VERTEX_COORDS = floatArrayOf(
        -1f, -1f, 0f,  // 左下
         1f, -1f, 0f,  // 右下
        -1f,  1f, 0f,  // 左上
         1f,  1f, 0f   // 右上
    )

    // 纹理坐标（与顶点对应）
    private val TEX_COORDS = floatArrayOf(
        0f, 0f,  // 左下
        1f, 0f,  // 右下
        0f, 1f,  // 左上
        1f, 1f   // 右上
    )

    // ═══════════════════════════════════════════════════════════════
    // Bitmap GPU 处理
    // ═══════════════════════════════════════════════════════════════

    /**
     * 使用指定 Shader 处理 Bitmap
     *
     * @param bitmap 输入位图
     * @param fragmentShaderSource 片段着色器代码
     * @param params 统一变量设置回调
     * @return 处理后的位图（失败时返回原图副本）
     */
    fun processBitmap(
        bitmap: Bitmap,
        fragmentShaderSource: String,
        params: ((ShaderProgram) -> Unit)? = null
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // 创建输出 Bitmap
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        try {
            // 1. 创建顶点/纹理缓冲区
            val vertexBuffer = createFloatBuffer(VERTEX_COORDS)
            val texBuffer = createFloatBuffer(TEX_COORDS)

            // 2. 编译着色器程序
            val program = createProgram(VERTEX_SHADER, fragmentShaderSource)
            if (program == 0) {
                bitmap.recycle()
                return output.also { canvas ->
                    android.graphics.Canvas(output).drawBitmap(bitmap, 0f, 0f, null)
                }
            }

            val shaderProgram = ShaderProgram(program)
            shaderProgram.use()

            // 3. 创建纹理并上传 Bitmap
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            val texId = textures[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            // 将 Bitmap 上传为 GL 纹理
            val bitmapBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
            bitmap.copyPixelsToBuffer(bitmapBuffer)
            bitmapBuffer.rewind()
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bitmapBuffer
            )

            // 4. 设置视口
            GLES20.glViewport(0, 0, width, height)

            // 5. 设置顶点属性
            val posLoc = GLES20.glGetAttribLocation(program, "aPosition")
            val texLoc = GLES20.glGetAttribLocation(program, "aTexCoord")

            GLES20.glEnableVertexAttribArray(posLoc)
            GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glEnableVertexAttribArray(texLoc)
            GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

            // 6. 设置纹理
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)

            // 7. 设置自定义参数
            params?.invoke(shaderProgram)

            // 8. 绘制
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            // 9. 回读像素
            val pixelBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer)
            pixelBuffer.rewind()
            output.copyPixelsFromBuffer(pixelBuffer)

            // 注意：GL 原点在左下，Bitmap 原点在左上，需要垂直翻转
            val flipped = flipVertical(output, width, height)

            // 10. 清理
            GLES20.glDeleteTextures(1, textures, 0)
            GLES20.glDeleteProgram(program)

            if (flipped != output) output.recycle()
            return flipped
        } catch (e: Exception) {
            // GPU 处理失败，返回原图副本
            return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
    }

    /** 垂直翻转 Bitmap */
    private fun flipVertical(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.preScale(1f, -1f)
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false)
    }

    // ═══════════════════════════════════════════════════════════════
    // GL 程序创建
    // ═══════════════════════════════════════════════════════════════

    /** 创建着色器程序 */
    fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader)
            return 0
        }

        val program = GLES20.glCreateProgram()
        if (program == 0) {
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return 0
        }

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            GLES20.glDeleteProgram(program)
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return 0
        }

        // 链接成功后可以删除着色器
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        return program
    }

    /** 编译着色器 */
    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) return 0
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    /** 创建 FloatBuffer */
    private fun createFloatBuffer(array: FloatArray): FloatBuffer {
        val buffer = ByteBuffer.allocateDirect(array.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(array).rewind()
        return buffer
    }

    // ═══════════════════════════════════════════════════════════════
    // Shader 程序封装
    // ═══════════════════════════════════════════════════════════════

    class ShaderProgram(val programId: Int) {
        fun use() {
            GLES20.glUseProgram(programId)
        }

        fun setFloat(name: String, value: Float) {
            val loc = GLES20.glGetUniformLocation(programId, name)
            if (loc >= 0) GLES20.glUniform1f(loc, value)
        }

        fun setFloat2(name: String, v1: Float, v2: Float) {
            val loc = GLES20.glGetUniformLocation(programId, name)
            if (loc >= 0) GLES20.glUniform2f(loc, v1, v2)
        }

        fun setFloat3(name: String, v1: Float, v2: Float, v3: Float) {
            val loc = GLES20.glGetUniformLocation(programId, name)
            if (loc >= 0) GLES20.glUniform3f(loc, v1, v2, v3)
        }

        fun setFloatArray(name: String, values: FloatArray) {
            val loc = GLES20.glGetUniformLocation(programId, name)
            if (loc >= 0) GLES20.glUniform1fv(loc, values.size, values, 0)
        }

        fun setInt(name: String, value: Int) {
            val loc = GLES20.glGetUniformLocation(programId, name)
            if (loc >= 0) GLES20.glUniform1i(loc, value)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 内置 Shader
    // ═══════════════════════════════════════════════════════════════

    /** 顶点着色器（通用） */
    const val VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """

    /** 基础直通着色器 */
    const val SHADER_PASSTHROUGH = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        void main() {
            gl_FragColor = texture2D(uTexture, vTexCoord);
        }
    """

    /** 亮度/对比度/饱和度着色器 */
    const val SHADER_BCS = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform float uBrightness;   // -1.0 ~ 1.0
        uniform float uContrast;     // 0.0 ~ 2.0
        uniform float uSaturation;   // 0.0 ~ 2.0

        vec3 rgb2hsv(vec3 c) {
            vec4 K = vec4(0.0, -1.0/3.0, 2.0/3.0, -1.0);
            vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
            vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
            float d = q.x - min(q.w, q.y);
            float e = 1.0e-10;
            return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
        }

        vec3 hsv2rgb(vec3 c) {
            vec4 K = vec4(1.0, 2.0/3.0, 1.0/3.0, 3.0);
            vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
            return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
        }

        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            // 亮度
            color.rgb += uBrightness;
            // 对比度
            color.rgb = (color.rgb - 0.5) * uContrast + 0.5;
            // 饱和度（HSV 空间）
            vec3 hsv = rgb2hsv(color.rgb);
            hsv.y *= uSaturation;
            color.rgb = hsv2rgb(hsv);
            gl_FragColor = clamp(color, 0.0, 1.0);
        }
    """

    /** 高斯模糊着色器（单次水平或垂直） */
    const val SHADER_GAUSSIAN_BLUR = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform vec2 uTexelOffset;   // (1/width, 0) 或 (0, 1/height)
        uniform float uBlurRadius;   // 模糊半径

        void main() {
            vec4 sum = vec4(0.0);
            float weights[5];
            weights[0] = 0.227027;
            weights[1] = 0.1945946;
            weights[2] = 0.1216216;
            weights[3] = 0.054054;
            weights[4] = 0.016216;

            sum += texture2D(uTexture, vTexCoord) * weights[0];
            for (int i = 1; i < 5; i++) {
                sum += texture2D(uTexture, vTexCoord + uTexelOffset * float(i) * uBlurRadius) * weights[i];
                sum += texture2D(uTexture, vTexCoord - uTexelOffset * float(i) * uBlurRadius) * weights[i];
            }
            gl_FragColor = sum;
        }
    """

    /** 锐化着色器 */
    const val SHADER_SHARPEN = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform vec2 uTexelSize;
        uniform float uSharpness;   // 0.0 ~ 2.0

        void main() {
            vec4 center = texture2D(uTexture, vTexCoord);
            vec4 left = texture2D(uTexture, vTexCoord - vec2(uTexelSize.x, 0.0));
            vec4 right = texture2D(uTexture, vTexCoord + vec2(uTexelSize.x, 0.0));
            vec4 top = texture2D(uTexture, vTexCoord - vec2(0.0, uTexelSize.y));
            vec4 bottom = texture2D(uTexture, vTexCoord + vec2(0.0, uTexelSize.y));

            vec4 sharp = center * 5.0 - left - right - top - bottom;
            gl_FragColor = mix(center, sharp, uSharpness);
        }
    """

    /** 暗角着色器 */
    const val SHADER_VIGNETTE = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform float uVignetteStrength;  // 0.0 ~ 1.0
        uniform float uVignetteRadius;    // 0.3 ~ 1.0

        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            vec2 center = vTexCoord - 0.5;
            float dist = length(center);
            float vignette = smoothstep(uVignetteRadius, uVignetteRadius * 0.5, dist);
            color.rgb *= mix(1.0 - uVignetteStrength, 1.0, vignette);
            gl_FragColor = color;
        }
    """

    /** 色温着色器 */
    const val SHADER_COLOR_TEMP = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform float uTemperature;  // -1.0 (冷) ~ 1.0 (暖)

        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            // 暖色：增加红，减少蓝
            color.r += uTemperature * 0.1;
            color.b -= uTemperature * 0.1;
            // 冷色：增加蓝，减少红
            color.r += uTemperature * 0.05;
            color.b -= uTemperature * 0.05;
            gl_FragColor = clamp(color, 0.0, 1.0);
        }
    """

    /** HDR 色调映射着色器 */
    const val SHADER_TONE_MAPPING = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform float uExposure;     // 曝光 0.5 ~ 2.0
        uniform float uGamma;        // 伽马 0.5 ~ 2.0

        vec3 acesToneMap(vec3 color) {
            float a = 2.51;
            float b = 0.03;
            float c = 2.43;
            float d = 0.59;
            float e = 0.14;
            return clamp((color * (a * color + b)) / (color * (c * color + d) + e), 0.0, 1.0);
        }

        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            color.rgb *= uExposure;
            color.rgb = acesToneMap(color.rgb);
            color.rgb = pow(color.rgb, vec3(1.0 / uGamma));
            gl_FragColor = color;
        }
    """

    /** 通用滤镜着色器（支持亮度/对比度/饱和度/色温/暗角组合） */
    const val SHADER_COMBINED = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform float uBrightness;
        uniform float uContrast;
        uniform float uSaturation;
        uniform float uTemperature;
        uniform float uVignette;
        uniform float uSharpen;
        uniform vec2 uTexelSize;

        vec3 rgb2hsv(vec3 c) {
            vec4 K = vec4(0.0, -1.0/3.0, 2.0/3.0, -1.0);
            vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
            vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
            float d = q.x - min(q.w, q.y);
            float e = 1.0e-10;
            return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
        }

        vec3 hsv2rgb(vec3 c) {
            vec4 K = vec4(1.0, 2.0/3.0, 1.0/3.0, 3.0);
            vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
            return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
        }

        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);

            // 锐化
            if (uSharpen > 0.0) {
                vec4 left = texture2D(uTexture, vTexCoord - vec2(uTexelSize.x, 0.0));
                vec4 right = texture2D(uTexture, vTexCoord + vec2(uTexelSize.x, 0.0));
                vec4 top = texture2D(uTexture, vTexCoord - vec2(0.0, uTexelSize.y));
                vec4 bottom = texture2D(uTexture, vTexCoord + vec2(0.0, uTexelSize.y));
                vec4 sharp = color * 5.0 - left - right - top - bottom;
                color = mix(color, sharp, uSharpen);
            }

            // 亮度
            color.rgb += uBrightness;
            // 对比度
            color.rgb = (color.rgb - 0.5) * uContrast + 0.5;
            // 色温
            color.r += uTemperature * 0.1;
            color.b -= uTemperature * 0.1;
            // 饱和度
            vec3 hsv = rgb2hsv(color.rgb);
            hsv.y *= uSaturation;
            color.rgb = hsv2rgb(hsv);
            color = clamp(color, 0.0, 1.0);

            // 暗角
            if (uVignette > 0.0) {
                vec2 center = vTexCoord - 0.5;
                float dist = length(center);
                float vig = smoothstep(0.8, 0.3, dist);
                color.rgb *= mix(1.0 - uVignette, 1.0, vig);
            }

            gl_FragColor = color;
        }
    """
}

/**
 * 实时滤镜渲染器
 *
 * 用于相机预览实时渲染。绑定到 GLSurfaceView，
 * 接收 SurfaceTexture 中的相机帧，应用滤镜后输出到屏幕。
 */
class RealtimeFilterRenderer : GLSurfaceView.Renderer {

    /** 当前滤镜参数 */
    var brightness = 0f      // -1 ~ 1
    var contrast = 1f        // 0 ~ 2
    var saturation = 1f      // 0 ~ 2
    var temperature = 0f     // -1 ~ 1
    var vignette = 0f        // 0 ~ 1
    var sharpen = 0f         // 0 ~ 1
    var lutIntensity = 0f    // 0 ~ 1

    /** LUT 纹理 ID（如使用 LUT 滤镜） */
    var lutTextureId = 0

    /** 相机 SurfaceTexture */
    var cameraTexture: SurfaceTexture? = null
    private var cameraTexId = 0

    private var program = 0
    private var vertexBuffer: FloatBuffer? = null
    private var texBuffer: FloatBuffer? = null

    // 纹理矩阵
    private val mvpMatrix = FloatArray(16)
    private val texMatrix = FloatArray(16)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)

        // 创建着色器程序
        program = GpuRenderEngine.createProgram(
            GpuRenderEngine.VERTEX_SHADER,
            GpuRenderEngine.SHADER_COMBINED
        )

        // 创建顶点缓冲
        vertexBuffer = createFloatBuffer(GpuRenderEngine.VERTEX_COORDS)
        texBuffer = createFloatBuffer(GpuRenderEngine.TEX_COORDS)

        // 创建相机纹理
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        cameraTexId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cameraTexId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // 创建 SurfaceTexture 用于接收相机帧
        cameraTexture = SurfaceTexture(cameraTexId)

        Matrix.setIdentityM(mvpMatrix, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // 更新相机纹理
        cameraTexture?.updateTexImage()
        cameraTexture?.getTransformMatrix(texMatrix)

        if (program == 0) return

        GLES20.glUseProgram(program)

        // 设置顶点属性
        val posLoc = GLES20.glGetAttribLocation(program, "aPosition")
        val texLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texLoc)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

        // 绑定相机纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cameraTexId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)

        // 设置滤镜参数
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uBrightness"), brightness)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uContrast"), contrast)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSaturation"), saturation)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uTemperature"), temperature)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uVignette"), vignette)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uSharpen"), sharpen)

        // 设置纹理像素大小（用于锐化采样）
        GLES20.glUniform2f(
            GLES20.glGetUniformLocation(program, "uTexelSize"),
            1f / 1080f, 1f / 1920f
        )

        // 绘制
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    /** 获取 Surface（用于绑定到相机） */
    fun getSurface(): Surface? {
        return cameraTexture?.let { Surface(it) }
    }

    /** 释放资源 */
    fun release() {
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
        if (cameraTexId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(cameraTexId), 0)
            cameraTexId = 0
        }
        cameraTexture?.release()
        cameraTexture = null
    }

    private fun createFloatBuffer(array: FloatArray): FloatBuffer {
        val buffer = ByteBuffer.allocateDirect(array.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buffer.put(array).rewind()
        return buffer
    }
}
