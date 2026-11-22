package com.example.aimobot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.hypot

class BotService : AccessibilityService() {
    companion object { @Volatile var inst: BotService? = null }

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var cfg: Config
    private lateinit var wm: WindowManager
    private var panel: View? = null
    private var overlay: View? = null
    private var statusTv: TextView? = null
    private var playTv: TextView? = null
    private var targetTv: TextView? = null
    private val tpls = CopyOnWriteArrayList<Pair<String, Vision.Tpl>>()   // (nome seguro do mob, sprite)

    @Volatile private var worker: Thread? = null
    @Volatile private var running = false
    @Volatile private var sw = 1
    @Volatile private var sh = 1
    @Volatile private var lastErr = 0
    private var speed = 0.15f // px/ms: valor inicial; é reajustado sozinho durante as aproximações

    private val DX = intArrayOf(1, 0, -1, 0)   // 0=direita 1=baixo 2=esquerda 3=cima
    private val DY = intArrayOf(0, 1, 0, -1)

    // ---------------------------------------------------------------- ciclo de vida
    override fun onServiceConnected() {
        inst = this
        cfg = Config(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        loadTemplates()
        showPanel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        stopBot(); removeView(panel); removeView(overlay); inst = null
        return super.onUnbind(intent)
    }

    // ---------------------------------------------------------------- painel flutuante
    private fun overlayParams(w: Int, h: Int): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        return WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT)
    }

    private fun removeView(v: View?) {
        if (v != null) try { wm.removeView(v) } catch (e: Exception) { }
    }

    private fun showPanel() {
        fun btn(t: String, a: () -> Unit) = TextView(this).apply {
            text = t; textSize = 18f; setTextColor(Color.WHITE); setPadding(22, 8, 22, 8)
            setBackgroundColor(0xAA222222.toInt()); setOnClickListener { a() }
        }
        val play = btn("▶") { toggle() }
        playTv = play
        val target = btn(if (cfg.captureMob.isEmpty()) "🎯 —" else "🎯 ${cfg.captureMob}") { cycleTarget() }
        targetTv = target
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(play)
            addView(btn("⌖") { startCalibration() })
            addView(target)
            addView(btn("👾") { startCapture() })
        }
        val st = TextView(this).apply {
            textSize = 11f; setTextColor(Color.WHITE); setBackgroundColor(0xAA000000.toInt()); text = "pronto"
        }
        statusTv = st
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(row); addView(st) }
        val p = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
        p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        panel = col
        wm.addView(col, p)
    }

    private fun status(s: String) { ui.post { statusTv?.text = s } }
    private fun toast(s: String) { ui.post { Toast.makeText(this, s, Toast.LENGTH_LONG).show() } }
    private fun pct(v: Float) = "${(v * 100).toInt()}%"

    /** Escolhe, entre os mobs marcados no app, para qual deles o botão 👾 vai salvar sprites. */
    private fun cycleTarget() {
        val sel = Mobs.ALL.map { it.name }.filter { it in cfg.selected() }
        if (sel.isEmpty()) { toast("Marque os mobs no app Aimo Bot primeiro."); return }
        val next = sel[(sel.indexOf(cfg.captureMob) + 1) % sel.size]
        cfg.captureMob = next
        ui.post { targetTv?.text = "🎯 $next" }
    }

    // ---------------------------------------------------------------- sprites salvos (tpl/<mob>/*.png)
    private fun tplRoot(): File = File(filesDir, "tpl").apply { mkdirs() }

    fun loadTemplates() {
        tpls.clear()
        val dirs = tplRoot().listFiles() ?: return
        for (d in dirs) {
            if (!d.isDirectory) continue
            val files = d.listFiles() ?: continue
            for (f in files.sortedBy { it.name }) {
                val b = BitmapFactory.decodeFile(f.path) ?: continue
                Vision.makeTemplate(b)?.let { tpls.add(Pair(d.name, it)) }
                b.recycle()
            }
        }
    }

    // ---------------------------------------------------------------- tela e toques (bloqueantes, rodam na thread do bot)
    private fun shot(): Bitmap? {
        val latch = CountDownLatch(1)
        var out: Bitmap? = null
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(r: ScreenshotResult) {
                try {
                    val hb = r.hardwareBuffer
                    val hw = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)
                    out = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    hw?.recycle()
                    hb.close()
                } catch (e: Exception) { lastErr = -1 }
                latch.countDown()
            }
            override fun onFailure(errorCode: Int) { lastErr = errorCode; latch.countDown() }
        })
        latch.await(3, TimeUnit.SECONDS)
        return out
    }

    private fun gesture(x: Float, y: Float, ms: Long): Boolean {
        val latch = CountDownLatch(1)
        var ok = false
        val path = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, ms.coerceAtLeast(1))).build()
        val sent = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { ok = true; latch.countDown() }
            override fun onCancelled(gestureDescription: GestureDescription?) { latch.countDown() }
        }, null)
        if (!sent) return false
        latch.await(ms + 1500, TimeUnit.MILLISECONDS)
        return ok
    }

    private fun tap(x: Float, y: Float) { gesture(x, y, 60) }

    private fun move(d: Int, ms: Long) {
        val dist = cfg.f("dpDist") * sw
        gesture(cfg.f("dpx") * sw + DX[d] * dist, cfg.f("dpy") * sh + DY[d] * dist, ms)
    }

    // ---------------------------------------------------------------- controle
    private fun toggle() { if (running) stopBot() else startBot() }

    private fun activeTemplates(): List<Vision.Tpl> {
        val sel = cfg.selected().map { Mobs.safe(it) }.toSet()
        return tpls.filter { it.first in sel }.map { it.second }
    }

    private fun startBot() {
        if (running) return
        if (activeTemplates().isEmpty()) { toast("Marque mobs no app e capture ao menos 1 sprite deles (🎯 e 👾)."); return }
        running = true
        ui.post { playTv?.text = "⏸" }
        worker = Thread {
            try { loop() }
            catch (e: InterruptedException) { }
            catch (e: Exception) { status("erro: ${e.message}") }
            finally { running = false; ui.post { playTv?.text = "▶" } }
        }.also { it.start() }
    }

    private fun stopBot() { running = false; worker?.interrupt() }

    // ---------------------------------------------------------------- o bot
    private fun loop() {
        var resting = false; var restSince = 0L
        var engaged = false; var miss = 0
        var lastDx = 0f; var lastDy = 0f
        var patrol = 0; var skillIdx = 0
        var prevAxis = -1f; var prevBurst = 0L

        while (running) {
            val bmp = shot()
            if (bmp == null) { status("screenshot falhou (cód $lastErr)"); Thread.sleep(800); continue }
            val W = bmp.width; val H = bmp.height; sw = W; sh = H

            val hp = Vision.bar(bmp, cfg.f("hpAx"), cfg.f("hpAy"), cfg.f("hpBx"), cfg.f("hpBy"), false)
            val mp = Vision.bar(bmp, cfg.f("mpAx"), cfg.f("mpAy"), cfg.f("mpBx"), cfg.f("mpBy"), true)
            val px = IntArray(W * H); bmp.getPixels(px, 0, W, 0, 0, W, H); bmp.recycle()
            if (hp < 0.02f && mp < 0.02f) {
                status("Não consegui ler HP/MP. Recalibre (⌖)."); toast("Não consegui ler as barras de HP/MP. Recalibre (⌖)."); break
            }

            // ---- vida/mana baixas: espera regenerar
            if (!resting && (hp < cfg.f("hpLow") || mp < cfg.f("mpLow"))) { resting = true; restSince = System.currentTimeMillis(); engaged = false }
            if (resting) {
                if (hp >= cfg.f("hpOk") && mp >= cfg.f("mpOk")) resting = false
                else {
                    status("Descansando  HP ${pct(hp)}  MP ${pct(mp)}")
                    if (System.currentTimeMillis() - restSince > 300_000) { toast("Descanso passou de 5 min; parei."); break }
                    Thread.sleep(1500); continue
                }
            }

            // ---- procura só os mobs marcados, na área livre de HUD
            val active = activeTemplates()
            if (active.isEmpty()) { status("Sem sprites dos mobs marcados"); toast("Sem sprites dos mobs marcados. Parei."); break }
            val dw = W / Vision.S; val dh = H / Vision.S
            val dp = Vision.down(px, W, H)
            val hits = Vision.find(dp, dw, active, (0.29f * dw).toInt(), (0.12f * dh).toInt(), (0.76f * dw).toInt(), (0.76f * dh).toInt(), cfg.f("thr"))
            val ax = cfg.f("anx") * W; val ay = cfg.f("any") * H
            val range = cfg.f("range") * H
            val keep = minOf(cfg.f("keep") * H, range * 0.9f)
            val t = hits.minByOrNull { hypot(it.x - ax, it.y - ay) }

            if (t != null) {
                miss = 0
                val dx = t.x - ax; val dy = t.y - ay
                // alvo "pulou" de lugar durante o combate => o anterior morreu: coleta o loot dele primeiro
                if (engaged && abs(dx - lastDx) + abs(dy - lastDy) > 0.2f * H) {
                    status("Coletando loot"); loot(lastDx, lastDy, W, H); engaged = false; continue
                }
                lastDx = dx; lastDy = dy
                val dist = hypot(dx, dy)
                val axisDist = maxOf(abs(dx), abs(dy))
                if (prevAxis > 0f && prevBurst > 0L) {      // aprende a velocidade do personagem
                    val moved = prevAxis - axisDist
                    if (moved in 15f..700f) speed = 0.5f * speed + 0.5f * (moved / prevBurst)
                }
                prevAxis = -1f
                if (dist <= range) {
                    // ---- BATE: ataque + 1 skill da rotação (só as marcadas)
                    if (cfg.b("tapTarget")) tap(t.x.toFloat(), t.y.toFloat())
                    tap(cfg.f("atx") * W, cfg.f("aty") * H)
                    val skills = (1..5).filter { cfg.b("s${it}on") }
                    if (skills.isNotEmpty() && mp > cfg.f("mpLow") + 0.1f) {
                        val k = skills[skillIdx++ % skills.size]
                        Thread.sleep(120); tap(cfg.f("s${k}x") * W, cfg.f("s${k}y") * H)
                    }
                    engaged = true
                    status("Atacando  HP ${pct(hp)}  MP ${pct(mp)}  match ${t.score.toInt()}")
                    // ---- ANDA: mob chegou mais perto que a distância desejada => recua
                    if (dist < keep) {
                        val d = if (abs(dx) >= abs(dy)) (if (dx > 0) 2 else 0) else (if (dy > 0) 3 else 1)
                        val ms = ((keep - dist) / speed).toLong().coerceIn(120L, 500L)
                        move(d, ms)
                        lastDx -= DX[d] * speed * ms; lastDy -= DY[d] * speed * ms   // mantém a posição estimada do mob
                    }
                } else {
                    val ms = ((axisDist - range * 0.5f) / speed).toLong().coerceIn(120L, 800L)
                    val d = if (abs(dx) >= abs(dy)) (if (dx > 0) 0 else 2) else (if (dy > 0) 1 else 3)
                    prevAxis = axisDist; prevBurst = ms
                    move(d, ms)
                    status("Indo ao monstro  HP ${pct(hp)}  MP ${pct(mp)}  match ${t.score.toInt()}")
                }
            } else {
                miss++
                if (engaged && miss >= 2) {
                    status("Coletando loot"); loot(lastDx, lastDy, W, H); engaged = false; miss = 0
                } else if (!engaged && miss >= 3) {
                    status("Procurando monstro  HP ${pct(hp)}  MP ${pct(mp)}")
                    move(patrol++ % 4, 700); miss = 2
                }
            }
            Thread.sleep(350)
        }
    }

    /** Anda até onde o monstro estava (deslocamento em relação ao personagem) e toca no botão de loot. */
    private fun loot(dx: Float, dy: Float, W: Int, H: Int) {
        if (cfg.b("lootWalk")) {
            for ((d, dist) in listOf(Pair(if (dx > 0) 0 else 2, abs(dx)), Pair(if (dy > 0) 1 else 3, abs(dy)))) {
                if (dist > 25f) move(d, (dist / speed).toLong().coerceIn(80L, 1500L))
            }
        }
        Thread.sleep(200)
        repeat(cfg.f("lootTaps").toInt().coerceIn(1, 6)) {
            tap(cfg.f("lootx") * W, cfg.f("looty") * H); Thread.sleep(350)
        }
    }

    // ---------------------------------------------------------------- calibração (toques guiados, com "Pular")
    private fun fullOverlay(msg: String, skip: ((TextView) -> Unit)?, onUp: (MotionEvent, TextView) -> Unit) {
        stopBot(); removeView(overlay)
        val tv = TextView(this).apply {
            text = msg; textSize = 16f; setTextColor(Color.WHITE)
            setBackgroundColor(0xCC000000.toInt()); setPadding(24, 12, 24, 12)
        }
        val root = FrameLayout(this)
        root.setBackgroundColor(0x22000000)
        root.addView(tv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        if (skip != null) {
            val sk = TextView(this).apply {
                text = "Pular este passo (manter padrão)"; textSize = 16f; setTextColor(Color.WHITE)
                setBackgroundColor(0xCC224488.toInt()); setPadding(28, 16, 28, 16)
                setOnClickListener { skip(tv) }
            }
            root.addView(sk, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        }
        root.setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_UP) onUp(e, tv); true }
        overlay = root
        wm.addView(root, overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT))
    }

    private fun startCalibration() {
        val steps = listOf(
            "Toque onde a barra de VIDA (vermelha) COMEÇA — se estiver escondida atrás do retrato, toque onde ela começaria",
            "Toque na PONTA DIREITA da barra de VIDA",
            "Toque onde a barra de MANA (azul) COMEÇA (mesmo critério)",
            "Toque na PONTA DIREITA da barra de MANA",
            "Toque no CENTRO do direcional (setas)",
            "Toque na seta DIREITA do direcional",
            "Toque no seu PERSONAGEM (meio do corpo)",
            "Toque no botão de ATAQUE",
            "Toque no botão de LOOT (ao lado do ataque)",
            "Toque na SKILL 1", "Toque na SKILL 2", "Toque na SKILL 3", "Toque na SKILL 4", "Toque na SKILL 5"
        )
        val pts = arrayOfNulls<PointF>(steps.size)
        var i = 0
        fun advance(tv: TextView) {
            i++
            if (i < steps.size) tv.text = "${i + 1}/${steps.size}: ${steps[i]}" else finishCalibration(pts)
        }
        fullOverlay("1/${steps.size}: ${steps[0]}", { tv -> advance(tv) }) { e, tv ->
            pts[i] = PointF(e.rawX, e.rawY); advance(tv)
        }
    }

    private fun finishCalibration(p: Array<PointF?>) {
        removeView(overlay); overlay = null
        Thread {
            Thread.sleep(300)
            val b = shot()
            val w = (b?.width ?: resources.displayMetrics.widthPixels).toFloat()
            val h = (b?.height ?: resources.displayMetrics.heightPixels).toFloat()
            b?.recycle()
            fun s(k: String, i: Int) { p[i]?.let { cfg.put(k + "x", it.x / w); cfg.put(k + "y", it.y / h) } }
            s("hpA", 0); s("hpB", 1); s("mpA", 2); s("mpB", 3)
            s("dp", 4); s("an", 6); s("at", 7); s("loot", 8)
            for (k in 1..5) s("s$k", 8 + k)
            val a = p[4]; val r = p[5]
            if (a != null && r != null) cfg.put("dpDist", abs(r.x - a.x) / w)
            toast("Calibrado ✅")
        }.start()
    }

    // ---------------------------------------------------------------- captura de sprite do mob escolhido (🎯)
    private fun startCapture() {
        val mob = cfg.captureMob
        if (mob.isEmpty() || mob !in cfg.selected()) { toast("Escolha o mob com 🎯 (marque-o antes no app)."); return }
        fullOverlay("Toque no meio do mob: $mob. Repita com outras poses.", null) { e, _ ->
            val x = e.rawX.toInt(); val y = e.rawY.toInt()
            removeView(overlay); overlay = null
            Thread {
                Thread.sleep(300)
                val b = shot()
                if (b == null) { toast("screenshot falhou (cód $lastErr)"); return@Thread }
                val c = Vision.crop(b, x, y); b.recycle()
                val t = Vision.makeTemplate(c)
                if (t == null) toast("Sprite quase igual ao fundo. Toque de novo no centro do mob.")
                else {
                    val dir = File(tplRoot(), Mobs.safe(mob)).apply { mkdirs() }
                    File(dir, "m${System.currentTimeMillis()}.png").outputStream().use { c.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    tpls.add(Pair(Mobs.safe(mob), t)); toast("Sprite de $mob salvo (${dir.listFiles()?.size ?: 1} no total)")
                }
                c.recycle()
            }.start()
        }
    }
}
