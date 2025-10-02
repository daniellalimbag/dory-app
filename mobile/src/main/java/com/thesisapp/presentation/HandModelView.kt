package com.thesisapp.presentation

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.Matrix
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.*
import com.google.android.filament.gltfio.*
import java.nio.ByteBuffer

class HandModelView(context: Context, attrs: AttributeSet? = null) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    private lateinit var engine: Engine
    private lateinit var renderer: Renderer
    private lateinit var scene: Scene
    private lateinit var view: View
    private lateinit var swapChain: SwapChain
    private lateinit var camera: Camera
    private lateinit var assetLoader: AssetLoader
    private lateinit var resourceLoader: ResourceLoader
    private var asset: FilamentAsset? = null

    private lateinit var choreographer: android.view.Choreographer
    private lateinit var frameCallback: android.view.Choreographer.FrameCallback

    init {
        surfaceTextureListener = this

        System.loadLibrary("filament-jni")
        System.loadLibrary("gltfio-jni")
    }

    private var gyroX = 0f
    private var gyroY = 0f
    private var gyroZ = 0f

    fun setRotation(x: Float, y: Float, z: Float) {
        if (!::engine.isInitialized || asset == null) return

        gyroX = x * 500f
        gyroY = y * 500f
        gyroZ = z * 500f
        updateModelTransform()
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        val surface = Surface(surfaceTexture)

        engine = Engine.create()
        renderer = engine.createRenderer()
        swapChain = engine.createSwapChain(surface)
        scene = engine.createScene()
        view = engine.createView()
        camera = engine.createCamera(engine.entityManager.create())

        view.scene = scene
        view.camera = camera
        view.viewport = Viewport(0, 0, width, height)

        val aspect = width.toDouble() / height.toDouble()
        camera.setProjection(45.0, aspect, 1.0, 100.0, Camera.Fov.VERTICAL)
        camera.lookAt(
            7.0, 0.0, 0.0,
            0.0, 0.0, 0.0,
            0.0, 1.0, 0.0
        )

        // Add directional light
        val light = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 1.0f, 1.0f)
            .intensity(100_000.0f)
            .direction(-1.0f, 0.0f, 0.0f)
            .castShadows(true)
            .build(engine, light)
        scene.addEntity(light)

        // Add skybox (neutral gray)
        scene.skybox = Skybox.Builder().color(0.1f, 0.1f, 0.1f, 1.0f).build(engine)

        // Load model
        val materialProvider = UbershaderProvider(engine)
        assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
        resourceLoader = ResourceLoader(engine)

        val buffer = context.assets.open("Hand.glb").use {
            ByteBuffer.wrap(it.readBytes())
        }

        asset = assetLoader.createAsset(buffer)
        asset?.let {
            resourceLoader.loadResources(it)
            it.releaseSourceData()
            scene.addEntities(it.entities)
            updateModelTransform()
        }

        // Setup rendering loop
        choreographer = android.view.Choreographer.getInstance()
        frameCallback = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (renderer.beginFrame(swapChain, frameTimeNanos)) {
                    renderer.render(view)
                    renderer.endFrame()
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(frameCallback)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        view.viewport = Viewport(0, 0, width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        if (::choreographer.isInitialized && ::frameCallback.isInitialized) {
            choreographer.removeFrameCallback(frameCallback)
        }

        asset?.let { assetLoader.destroyAsset(it) }

        engine.destroyRenderer(renderer)
        engine.destroyScene(scene)
        engine.destroyView(view)
        engine.destroyCameraComponent(camera.entity)
        engine.destroySwapChain(swapChain)
        engine.destroy()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    private fun updateModelTransform() {
        val tm = engine.transformManager
        val transformInstance = tm.getInstance(asset?.root ?: return)

        val matrix = FloatArray(16)
        Matrix.setIdentityM(matrix, 0)

        Matrix.rotateM(matrix, 0, gyroY, 0f, 1f, 0f) // Yaw
        Matrix.rotateM(matrix, 0, gyroX, 1f, 0f, 0f) // Pitch
        Matrix.rotateM(matrix, 0, gyroZ, 0f, 0f, 1f) // Roll

        tm.setTransform(transformInstance, matrix)
    }


    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

}