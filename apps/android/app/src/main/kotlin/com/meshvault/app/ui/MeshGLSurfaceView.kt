package com.meshvault.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceHolder
import com.meshvault.app.data.MeshData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Параметры трансформации модели, применяются как предпросмотр и запекаются кнопкой «Применить». */
data class ModelTransform(
    var scaleX: Float = 1f,
    var scaleY: Float = 1f,
    var scaleZ: Float = 1f,
    var rotX: Float = 0f,
    var rotY: Float = 0f,
    var rotZ: Float = 0f,
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var offsetZ: Float = 0f,
) {
    fun reset() {
        scaleX = 1f; scaleY = 1f; scaleZ = 1f
        rotX = 0f; rotY = 0f; rotZ = 0f
        offsetX = 0f; offsetY = 0f; offsetZ = 0f
    }

    fun copyFrom(other: ModelTransform) {
        scaleX = other.scaleX; scaleY = other.scaleY; scaleZ = other.scaleZ
        rotX = other.rotX; rotY = other.rotY; rotZ = other.rotZ
        offsetX = other.offsetX; offsetY = other.offsetY; offsetZ = other.offsetZ
    }
}

enum class CameraPreset(val yaw: Float, val pitch: Float, val label: String) {
    ISO(-45f, 30f, "Изометрия"),
    TOP(0f, 89f, "Сверху"),
    BOTTOM(0f, -89f, "Снизу"),
    FRONT(-90f, 0f, "Спереди"),
    BACK(90f, 0f, "Сзади"),
    LEFT(180f, 0f, "Слева"),
    RIGHT(0f, 0f, "Справа"),
}

private const val VERTEX_SHADER = """#version 300 es
in vec3 aPosition;
in vec3 aNormal;
uniform mat4 uMVP;
out vec3 vNormal;
void main() {
    vNormal = mat3(uMVP) * aNormal;
    gl_Position = uMVP * vec4(aPosition, 1.0);
}
"""

private const val FRAGMENT_SHADER = """#version 300 es
precision mediump float;
in vec3 vNormal;
uniform vec4 uColor;
out vec4 fragColor;
void main() {
    vec3 n = normalize(vNormal);
    float light = max(dot(n, normalize(vec3(0.3f, 0.6f, 0.8f))), 0.0f) * 0.65f + 0.35f;
    fragColor = vec4(uColor.rgb * light, uColor.a);
}
"""

class MeshGLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {
    private val meshRenderer = MeshRenderer { requestRender() }
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    var onLongPress: ((Float, Float) -> Unit)? = null
    var onRingSelect: ((Int) -> Unit)? = null
    var onRingClose: (() -> Unit)? = null
    var onDoubleTap: (() -> Unit)? = null
    var ringActive = false
    private var ringCenterX = 0f
    private var ringCenterY = 0f
    private var lastTapAt = 0L

    init {
        setEGLContextClientVersion(3)
        setRenderer(meshRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        super.surfaceCreated(holder)
        requestRender()
    }

    private fun clearLongPress() {
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
    }

    fun clear() = queueEvent { meshRenderer.clear(); requestRender() }
    fun setMesh(mesh: MeshData) = queueEvent { meshRenderer.setMesh(mesh); requestRender() }
    fun setAutoRotate(v: Boolean) = queueEvent { meshRenderer.autoRotate = v; requestRender() }
    fun setRotateSpeed(v: Float) = queueEvent { meshRenderer.rotateSpeed = v }
    fun setWireframe(v: Boolean) = queueEvent { meshRenderer.wireframe = v; requestRender() }
    fun setGrid(v: Boolean) = queueEvent { meshRenderer.showGrid = v; requestRender() }
    fun setAxes(v: Boolean) = queueEvent { meshRenderer.showAxes = v; requestRender() }
    fun setOrtho(v: Boolean) = queueEvent { meshRenderer.ortho = v; requestRender() }
    fun setColor(r: Float, g: Float, b: Float) = queueEvent {
        meshRenderer.color[0] = r; meshRenderer.color[1] = g; meshRenderer.color[2] = b
        requestRender()
    }
    fun zoom(factor: Float) = queueEvent { meshRenderer.zoom(factor); requestRender() }
    fun setZoom(value: Float) = queueEvent { meshRenderer.setZoom(value); requestRender() }
    fun resetView() = queueEvent { meshRenderer.resetView(); requestRender() }
    fun setPreset(preset: CameraPreset) = queueEvent { meshRenderer.setPreset(preset); requestRender() }
    fun setTransform(t: ModelTransform) = queueEvent {
        meshRenderer.transform.copyFrom(t)
        requestRender()
    }
    fun applyTransform() = queueEvent { meshRenderer.applyTransform(); requestRender() }
    fun snapshot(callback: (Bitmap?) -> Unit) {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        runCatching {
            PixelCopy.request(this, bitmap, { result ->
                callback(if (result == PixelCopy.SUCCESS) bitmap else null)
            }, Handler(Looper.getMainLooper()))
        }.onFailure { callback(null) }
    }
    fun isLinked(): Boolean = meshRenderer.linked

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x; lastY = event.y
                val x = event.x; val y = event.y
                clearLongPress()
                longPressRunnable = Runnable {
                    ringActive = true; ringCenterX = x; ringCenterY = y
                    onLongPress?.invoke(x, y)
                }.also { longPressHandler.postDelayed(it, 350) }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                clearLongPress()
                if (ringActive) { ringActive = false; onRingClose?.invoke() }
                if (event.pointerCount >= 2) {
                    lastDistance = touchDistance(event)
                    lastMidX = (event.getX(0) + event.getX(1)) / 2f
                    lastMidY = (event.getY(0) + event.getY(1)) / 2f
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (ringActive) { lastX = event.x; lastY = event.y; return true }
                if (abs(event.x - lastX) > 10f || abs(event.y - lastY) > 10f) clearLongPress()
                if (event.pointerCount >= 2) {
                    val dist = touchDistance(event)
                    val midX = (event.getX(0) + event.getX(1)) / 2f
                    val midY = (event.getY(0) + event.getY(1)) / 2f
                    queueEvent {
                        meshRenderer.zoom(lastDistance / dist)
                        meshRenderer.panX -= (midX - lastMidX) * meshRenderer.mmPerPixel
                        meshRenderer.panY += (midY - lastMidY) * meshRenderer.mmPerPixel
                        requestRender()
                    }
                    lastDistance = dist; lastMidX = midX; lastMidY = midY
                } else {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    queueEvent {
                        meshRenderer.yaw += dx * 0.5f
                        meshRenderer.pitch = (meshRenderer.pitch + dy * 0.35f).coerceIn(-89f, 89f)
                        meshRenderer.spinVelocity = dx * 0.3f
                        requestRender()
                    }
                    lastX = event.x; lastY = event.y
                }
            }
            MotionEvent.ACTION_UP -> {
                clearLongPress()
                if (ringActive) {
                    ringActive = false
                    val dx = event.x - ringCenterX
                    val dy = event.y - ringCenterY
                    if (kotlin.math.hypot(dx, dy) < 40f) {
                        onRingClose?.invoke()
                    } else {
                        var degrees = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        degrees = (degrees + 90f + 360f) % 360f
                        onRingSelect?.invoke((degrees / 36f).toInt() % 10)
                    }
                    requestRender()
                    return true
                }
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastTapAt < 300 && abs(event.x - lastX) < 12f && abs(event.y - lastY) < 12f) {
                    onDoubleTap?.invoke()
                    lastTapAt = 0L
                } else {
                    lastTapAt = now
                }
                queueEvent { requestRender() }
            }
            MotionEvent.ACTION_CANCEL -> clearLongPress()
        }
        return true
    }

    private fun touchDistance(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    }

    private var lastX = 0f
    private var lastY = 0f
    private var lastDistance = 1f
    private var lastMidX = 0f
    private var lastMidY = 0f
}

class MeshRenderer(private val onFrame: () -> Unit = {}) : GLSurfaceView.Renderer {
    var yaw = -45f
    var pitch = 30f
    var panX = 0f
    var panY = 0f
    var autoRotate = false
    var rotateSpeed = 1f
    var wireframe = false
    var showGrid = true
    var showAxes = true
    var ortho = false
    var spinVelocity = 0f
    var zoomFactor = 1f
        private set
    val mmPerPixel: Float get() = distance / 900f
    val color = floatArrayOf(0.78f, 0.83f, 0.90f, 1f)
    val transform = ModelTransform()

    private var program = 0
    private var positionBuffer: FloatBuffer? = null
    private var normalBuffer: FloatBuffer? = null
    private var edgePositions: FloatBuffer? = null
    private var edgeNormals: FloatBuffer? = null
    private var gridPositions: FloatBuffer? = null
    private var gridNormals: FloatBuffer? = null
    private var axisX: FloatBuffer? = null
    private var axisY: FloatBuffer? = null
    private var axisZ: FloatBuffer? = null
    private var axisNormals: FloatBuffer? = null
    private var vertexCount = 0
    private var edgeVertexCount = 0
    private var gridVertexCount = 0
    private var extent = 100f
    private var distance = 320f
    private var baseDistance = 320f
    private var aspect = 1f
    private val offset = FloatArray(3)
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val mvp = FloatArray(16)
    private val eye = FloatArray(3)
    var linked: Boolean = false
        private set

    fun setPreset(preset: CameraPreset) {
        yaw = preset.yaw; pitch = preset.pitch
        panX = 0f; panY = 0f
        spinVelocity = 0f
    }

    fun resetView() {
        yaw = -45f; pitch = 30f
        panX = 0f; panY = 0f
        zoomFactor = 1f
        distance = baseDistance
        spinVelocity = 0f
        transform.reset()
    }

    fun zoom(factor: Float) {
        zoomFactor = (factor * zoomFactor).coerceIn(0.05f, 8f)
    }

    fun setZoom(value: Float) {
        zoomFactor = value.coerceIn(0.05f, 8f)
    }

    fun clear() {
        positionBuffer = null; normalBuffer = null
        edgePositions = null; edgeNormals = null
        vertexCount = 0; edgeVertexCount = 0
    }

    fun setMesh(mesh: MeshData) {
        clear()
        vertexCount = mesh.vertexCount
        positionBuffer = toBuffer(mesh.positions)
        normalBuffer = toBuffer(mesh.normals)

        // Ограничиваем число линий каркаса, чтобы не съесть память телефона
        val maxEdgeVertices = 120_000
        val ePos = ArrayList<Float>(maxEdgeVertices * 3)
        val eNorm = ArrayList<Float>(maxEdgeVertices * 3)
        var i = 0
        while (i + 8 < mesh.positions.size && ePos.size < maxEdgeVertices * 3) {
            for (edge in intArrayOf(0, 1, 1, 2, 2, 0)) {
                val a = i + edge * 3
                ePos.add(mesh.positions[a]); ePos.add(mesh.positions[a + 1]); ePos.add(mesh.positions[a + 2])
                eNorm.add(mesh.normals[a]); eNorm.add(mesh.normals[a + 1]); eNorm.add(mesh.normals[a + 2])
            }
            i += 9
        }
        edgeVertexCount = ePos.size / 3
        edgePositions = toBuffer(ePos.toFloatArray())
        edgeNormals = toBuffer(eNorm.toFloatArray())

        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (j in mesh.positions.indices step 3) {
            minX = minOf(minX, mesh.positions[j]); maxX = maxOf(maxX, mesh.positions[j])
            minY = minOf(minY, mesh.positions[j + 1]); maxY = maxOf(maxY, mesh.positions[j + 1])
            minZ = minOf(minZ, mesh.positions[j + 2]); maxZ = maxOf(maxZ, mesh.positions[j + 2])
        }
        offset[0] = -(minX + maxX) / 2f
        offset[1] = -(minY + maxY) / 2f
        offset[2] = -minZ
        extent = maxOf(maxX - minX, maxY - minY, maxZ - minZ, 10f)
        baseDistance = max(120f, extent * 2.4f)
        distance = baseDistance
        buildGrid(max(110f, extent * 0.6f))
        buildAxes()
    }

    private fun buildGrid(half: Float) {
        val positions = ArrayList<Float>()
        val normals = ArrayList<Float>()
        var v = -half
        while (v <= half) {
            positions.add(-half); positions.add(v); positions.add(0f)
            positions.add(half); positions.add(v); positions.add(0f)
            positions.add(v); positions.add(-half); positions.add(0f)
            positions.add(v); positions.add(half); positions.add(0f)
            repeat(4) { normals.add(0f); normals.add(0f); normals.add(1f) }
            v += 10f
        }
        gridVertexCount = positions.size / 3
        gridPositions = toBuffer(positions.toFloatArray())
        gridNormals = toBuffer(normals.toFloatArray())
    }

    private fun buildAxes() {
        val len = max(25f, extent * 0.35f)
        axisX = toBuffer(floatArrayOf(0f, 0f, 0f, len, 0f, 0f))
        axisY = toBuffer(floatArrayOf(0f, 0f, 0f, 0f, len, 0f))
        axisZ = toBuffer(floatArrayOf(0f, 0f, 0f, 0f, 0f, len))
        axisNormals = toBuffer(floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f))
    }

    private fun transformMatrix(): FloatArray {
        val m = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, transform.offsetX, transform.offsetY, transform.offsetZ)
        Matrix.rotateM(m, 0, transform.rotZ, 0f, 0f, 1f)
        Matrix.rotateM(m, 0, transform.rotY, 0f, 1f, 0f)
        Matrix.rotateM(m, 0, transform.rotX, 1f, 0f, 0f)
        Matrix.scaleM(m, 0, transform.scaleX, transform.scaleY, transform.scaleZ)
        return m
    }

    /** Запекает предпросмотр трансформации в вершины и сбрасывает параметры. */
    fun applyTransform() {
        val positions = positionBuffer ?: return
        val normals = normalBuffer ?: return
        val m = transformMatrix()
        val pos = FloatArray(vertexCount * 3)
        val norm = FloatArray(vertexCount * 3)
        positions.position(0); positions.get(pos)
        normals.position(0); normals.get(norm)
        val src = FloatArray(4)
        val dst = FloatArray(4)
        for (i in pos.indices step 3) {
            src[0] = pos[i]; src[1] = pos[i + 1]; src[2] = pos[i + 2]; src[3] = 1f
            Matrix.multiplyMV(dst, 0, m, 0, src, 0)
            pos[i] = dst[0]; pos[i + 1] = dst[1]; pos[i + 2] = dst[2]
            src[0] = norm[i]; src[1] = norm[i + 1]; src[2] = norm[i + 2]; src[3] = 0f
            Matrix.multiplyMV(dst, 0, m, 0, src, 0)
            val length = kotlin.math.sqrt(dst[0] * dst[0] + dst[1] * dst[1] + dst[2] * dst[2])
            if (length > 0.0001f) {
                norm[i] = dst[0] / length; norm[i + 1] = dst[1] / length; norm[i + 2] = dst[2] / length
            }
        }
        positionBuffer = toBuffer(pos)
        normalBuffer = toBuffer(norm)
        transform.reset()
        setMesh(MeshData(pos, norm))
    }

    private fun toBuffer(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(data); position(0) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.07f, 0.08f, 0.10f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        program = createProgram()
    }

    private fun createProgram(): Int {
        val vertex = compile(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment = compile(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        return GLES30.glCreateProgram().also {
            GLES30.glAttachShader(it, vertex)
            GLES30.glAttachShader(it, fragment)
            GLES30.glLinkProgram(it)
            val status = IntArray(1)
            GLES30.glGetProgramiv(it, GLES30.GL_LINK_STATUS, status, 0)
            linked = status[0] == GLES30.GL_TRUE
        }
    }

    private fun compile(type: Int, source: String): Int =
        GLES30.glCreateShader(type).also { shader ->
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
        }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        aspect = width.toFloat() / max(height, 1)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        if (autoRotate) yaw += 0.5f * rotateSpeed
        if (!autoRotate && abs(spinVelocity) > 0.05f) {
            yaw += spinVelocity
            spinVelocity *= 0.92f
        }

        distance = baseDistance * zoomFactor
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        eye[0] = distance * cos(pitchRad).toFloat() * cos(yawRad).toFloat()
        eye[1] = distance * cos(pitchRad).toFloat() * sin(yawRad).toFloat()
        eye[2] = distance * sin(pitchRad).toFloat()
        Matrix.setLookAtM(view, 0, eye[0], eye[1], eye[2], panX, panY, 0f, 0f, 0f, 1f)

        if (ortho) {
            val halfH = distance * 0.55f
            val halfW = halfH * aspect
            Matrix.orthoM(projection, 0, -halfW, halfW, -halfH, halfH, 1f, 5000f)
        } else {
            Matrix.perspectiveM(projection, 0, 45f, aspect, 1f, 5000f)
        }

        GLES30.glUseProgram(program)
        val mvpHandle = GLES30.glGetUniformLocation(program, "uMVP")
        val colorHandle = GLES30.glGetUniformLocation(program, "uColor")
        val posHandle = GLES30.glGetAttribLocation(program, "aPosition")
        val normHandle = GLES30.glGetAttribLocation(program, "aNormal")

        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)
        if (showGrid) {
            gridPositions?.let { gp ->
                draw(gp, gridNormals ?: return, gridVertexCount, posHandle, normHandle,
                    mvpHandle, colorHandle, 0.26f, 0.31f, 0.38f, 1f, GLES30.GL_LINES)
            }
        }
        if (showAxes) {
            val normals = axisNormals ?: return
            axisX?.let { draw(it, normals, 2, posHandle, normHandle, mvpHandle, colorHandle, 0.95f, 0.30f, 0.30f, 1f, GLES30.GL_LINES) }
            axisY?.let { draw(it, normals, 2, posHandle, normHandle, mvpHandle, colorHandle, 0.30f, 0.90f, 0.45f, 1f, GLES30.GL_LINES) }
            axisZ?.let { draw(it, normals, 2, posHandle, normHandle, mvpHandle, colorHandle, 0.35f, 0.55f, 1f, 1f, GLES30.GL_LINES) }
        }

        val positions = positionBuffer ?: return
        val normals = normalBuffer ?: return
        if (vertexCount == 0) return

        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, offset[0], offset[1], offset[2])
        val tm = transformMatrix()
        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)
        Matrix.multiplyMM(mvp, 0, mvp, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, mvp, 0, tm, 0)

        if (wireframe) {
            val ep = edgePositions ?: return
            val en = edgeNormals ?: return
            draw(ep, en, edgeVertexCount, posHandle, normHandle, mvpHandle, colorHandle,
                color[0], color[1], color[2], 1f, GLES30.GL_LINES)
        } else {
            draw(positions, normals, vertexCount, posHandle, normHandle, mvpHandle, colorHandle,
                color[0], color[1], color[2], 1f, GLES30.GL_TRIANGLES)
        }

        if (autoRotate || abs(spinVelocity) > 0.05f) onFrame()
    }

    private fun draw(
        positions: FloatBuffer,
        normals: FloatBuffer,
        count: Int,
        posHandle: Int,
        normHandle: Int,
        mvpHandle: Int,
        colorHandle: Int,
        r: Float, g: Float, b: Float, a: Float,
        mode: Int,
    ) {
        if (count == 0) return
        GLES30.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES30.glUniform4f(colorHandle, r, g, b, a)
        GLES30.glEnableVertexAttribArray(posHandle)
        positions.position(0)
        GLES30.glVertexAttribPointer(posHandle, 3, GLES30.GL_FLOAT, false, 0, positions)
        GLES30.glEnableVertexAttribArray(normHandle)
        normals.position(0)
        GLES30.glVertexAttribPointer(normHandle, 3, GLES30.GL_FLOAT, false, 0, normals)
        GLES30.glDrawArrays(mode, 0, count)
    }
}
