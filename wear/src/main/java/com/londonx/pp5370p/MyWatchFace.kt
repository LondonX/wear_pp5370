package com.londonx.pp5370p

import android.graphics.*
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.SurfaceHolder
import java.util.*
import kotlin.concurrent.schedule

class MyWatchFace : CanvasWatchFaceService() {
    private val paint = Paint()
    private val screenSize by lazy { resources.displayMetrics.widthPixels }

    private val bg by lazy { BitmapFactory.decodeResource(resources, R.mipmap.bg) }
    private val handSec by lazy { BitmapFactory.decodeResource(resources, R.mipmap.ic_hand_sec) }
    private val handMin by lazy { BitmapFactory.decodeResource(resources, R.mipmap.ic_hand_min) }
    private val handHour by lazy { BitmapFactory.decodeResource(resources, R.mipmap.ic_hand_hour) }
    private val handFunc by lazy { BitmapFactory.decodeResource(resources, R.mipmap.ic_hand_func) }
    private val handMinAmbient by lazy { BitmapFactory.decodeResource(resources, R.mipmap.ic_hand_min_ambient) }
    private val handHourAmbient by lazy { BitmapFactory.decodeResource(resources, R.mipmap.ic_hand_hour_ambient) }
    private val insetSec by lazy { (screenSize - handSec.width) / 2f }
    private val insetMin by lazy { (screenSize - handMin.width) / 2f }
    private val insetHour by lazy { (screenSize - handHour.width) / 2f }
    private val insetFuncXL by lazy { 92 - handFunc.width / 2f }
    private val insetFuncYL by lazy { 198 - handHour.height / 4f }
    private val insetFuncXR by lazy { screenSize - 92 - handFunc.width / 2f }
    private val insetFuncYR by lazy { 198 - handHour.height / 4f }
    private var angleH = 0f
    private var angleM = 0f
    private var angleS = 0f
    private var angleFuncL = 0f
    private var angleFuncR = 0f
    private var angleSS = -1f
    private var refreshTask: TimerTask? = null

    private var countingStartAt = 0L
    private var countingPauseAt = 0L
    private var countingPauseDelta = 0L
    private var countingPaused = true
    private var resetRequested = false
    private var ssResetRequested = false
    private var countingTask: TimerTask? = null
    private var resetTask: TimerTask? = null
    private var ssResetTask: TimerTask? = null

    override fun onCreateEngine() =
            object : CanvasWatchFaceService.Engine() {
                override fun onCreate(holder: SurfaceHolder?) {
                    super.onCreate(holder)
                    WatchFaceStyle.Builder(this@MyWatchFace)
                            .setAcceptsTapEvents(true)
                            .build()
                            .also {
                                setWatchFaceStyle(it)
                            }
                }

                override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
                    super.onTapCommand(tapType, x, y, eventTime)
                    if (tapType != WatchFaceService.TAP_TYPE_TAP) {
                        return
                    }
                    if (x < screenSize - 92) {
                        return
                    }
                    if (resetRequested) {
                        return
                    }
                    val hPerButton = 314 / 3
                    when {
                        y < (screenSize - hPerButton) / 2 -> {
                            //btn0 start and pause
                            countingPaused = !countingPaused
                            when {
                                countingPaused -> //Pause
                                    countingPauseAt = System.currentTimeMillis()
                                countingPauseAt == 0L -> //restart
                                    countingStartAt = System.currentTimeMillis()
                                else -> {//resume
                                    countingPauseDelta += (System.currentTimeMillis() - countingPauseAt)
                                    countingPauseAt = 0L
                                }
                            }
                            startCountingTask()
                        }
                        y > (screenSize + hPerButton) / 2 -> {
                            //btn2 reset
                            if (countingStartAt == 0L) {
                                return
                            }
                            resetRequested = true
                            countingStartAt = 0L
                            countingPauseAt = 0L
                            countingPauseDelta = 0L
                            countingPaused = true
                            startResetTask()
                        }
                        else -> {
                            //btn1 split-second
                            if (countingStartAt == 0L) {
                                return
                            }
                            if (ssResetRequested) {
                                return
                            }
                            if (angleSS == -1f) {
                                angleSS = angleS
                            } else {
                                ssResetRequested = true
                            }
                            startSSRestTask()
                        }
                    }
                }

                override fun onVisibilityChanged(visible: Boolean) {
                    super.onVisibilityChanged(visible)
                    startRefreshTask()
                    startCountingTask()
                    startResetTask()
                    startSSRestTask()
                }

                override fun onAmbientModeChanged(inAmbientMode: Boolean) {
                    super.onAmbientModeChanged(inAmbientMode)
                    startRefreshTask()
                    startCountingTask()
                    startResetTask()
                    startSSRestTask()
                }

                private fun startRefreshTask() {
                    refreshTask?.cancel()
                    countingTask?.cancel()
                    if (isInAmbientMode || !isVisible) {
                        return
                    }
                    refreshTask = Timer().schedule(0, 167) {
                        val c = Calendar.getInstance()
                        val second = c.get(Calendar.SECOND) / 60f
                        val ms = c.get(Calendar.MILLISECOND) / 1000f
                        angleFuncL = (second * 360 + ms * 6) % 360f
                        postInvalidate()
                    }
                }

                private fun startCountingTask() {
                    countingTask?.cancel()
                    if (isInAmbientMode || !isVisible) {
                        return
                    }
                    if (countingStartAt == 0L || countingPaused) {
                        return
                    }
                    val minInMills = 60 * 1000f
                    countingTask = Timer().schedule(0, 167) {
                        if (resetRequested) {
                            countingTask?.cancel()
                            return@schedule
                        }
                        val millsDelta = System.currentTimeMillis() - countingStartAt - countingPauseDelta
                        val s = (millsDelta % minInMills) / minInMills
                        val m = millsDelta / minInMills
                        angleS = (s * 360f) % 360f
                        angleFuncR = (m * 12f) % 360f
                    }
                }

                private fun startResetTask() {
                    resetTask?.cancel()
                    ssResetTask?.cancel()
                    ssResetRequested = false
                    if (isInAmbientMode || !isVisible) {
                        return
                    }
                    if (!resetRequested) {
                        return
                    }
                    angleFuncR = 0f
                    resetTask = Timer().schedule(0, 33) {
                        if (angleSS != -1f) {
                            angleSS += 5f
                            if (angleSS > 356) {
                                angleSS = -1f
                            }
                        }
                        if (angleS != 0f) {
                            angleS += 5f
                            if (angleS > 356) {
                                angleS = 0f
                            }
                        }
                        if (angleS == 0f && angleSS == -1f) {
                            resetRequested = false
                            resetTask?.cancel()
                        }
                        postInvalidate()
                    }
                }

                private fun startSSRestTask() {
                    ssResetTask?.cancel()
                    if (isInAmbientMode || !isVisible) {
                        return
                    }
                    if (!ssResetRequested) {
                        return
                    }
                    ssResetTask = Timer().schedule(0, 33) {
                        angleSS += 5f
                        if (angleSS > angleS - 5f) {
                            angleSS = -1f
                            ssResetRequested = false
                            ssResetTask?.cancel()
                        }
                        postInvalidate()
                    }
                }

                override fun onTimeTick() {
                    super.onTimeTick()
                    val c = Calendar.getInstance()
                    val hour = c.get(Calendar.HOUR) / 12f
                    val minute = c.get(Calendar.MINUTE) / 60f
                    val second = c.get(Calendar.SECOND) / 60f

                    angleH = (hour * 360 + minute * 30) % 360f
                    angleM = (minute * 360 + second * 5) % 360f
                    postInvalidate()
                }

                override fun onDraw(canvas: Canvas?, bounds: Rect?) {
                    super.onDraw(canvas, bounds)
                    canvas ?: return
                    val afL = angleFuncL
                    val afR = angleFuncR
                    val aS = angleS
                    val aM = angleM
                    val aH = angleH
                    val aSS = angleSS
                    if (isInAmbientMode) {//draw BG
                        canvas.drawColor(Color.BLACK)
                        paint.isFilterBitmap = false
                        paint.alpha = 128
                    } else {
                        paint.alpha = 255
                        paint.isFilterBitmap = true
                        canvas.drawBitmap(bg, 0f, 0f, paint)
                    }
                    if (!isInAmbientMode) {//draw other function hands
                        canvas.rotate(afL, 92f, 198f)
                        canvas.drawBitmap(handFunc, insetFuncXL, insetFuncYL, paint)
                        canvas.rotate(-afL, 92f, 198f)

                        //counting hands(other thread)
                        canvas.rotate(afR, screenSize - 92f, 198f)
                        canvas.drawBitmap(handFunc, insetFuncXR, insetFuncYR, paint)
                        canvas.rotate(-afR, screenSize - 92f, 198f)

                        if (aSS != -1f) {
                            canvas.rotate(aSS, screenSize / 2f, screenSize / 2f)
                            canvas.drawBitmap(handSec, insetSec, insetSec, paint)
                            canvas.rotate(-aSS, screenSize / 2f, screenSize / 2f)
                        }
                        canvas.rotate(aS, screenSize / 2f, screenSize / 2f)
                        canvas.drawBitmap(handSec, insetSec, insetSec, paint)
                        canvas.rotate(-aS, screenSize / 2f, screenSize / 2f)
                    }
                    //draw hour and minute hands
                    canvas.rotate(aH, screenSize / 2f, screenSize / 2f)
                    canvas.drawBitmap(
                            if (isInAmbientMode) handHourAmbient else handHour,
                            insetHour,
                            insetHour,
                            paint)
                    canvas.rotate(-aH, screenSize / 2f, screenSize / 2f)
                    canvas.rotate(aM, screenSize / 2f, screenSize / 2f)
                    canvas.drawBitmap(if (isInAmbientMode) handMinAmbient else handMin,
                            insetMin,
                            insetMin,
                            paint)
                    canvas.rotate(-aM, screenSize / 2f, screenSize / 2f)
                }
            }
}

