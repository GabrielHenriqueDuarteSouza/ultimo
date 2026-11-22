package com.example.aimobot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var cfg: Config
    private val rows = ArrayList<Pair<CheckBox, Mobs.Mob>>()

    private fun count(m: Mobs.Mob) = Mobs.dir(filesDir, m).listFiles()?.size ?: 0
    private fun refreshRows() { for ((cb, m) in rows) cb.text = m.label(count(m)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cfg = Config(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 48) }

        fun txt(t: String, size: Float = 15f) = TextView(this).apply { text = t; textSize = size; setPadding(0, 16, 0, 8) }
        fun btn(t: String, a: () -> Unit) = Button(this).apply { text = t; setOnClickListener { a() } }

        status = txt("")
        col.addView(txt("Aimo Bot — passos:\n1) Ative o serviço em Acessibilidade.\n2) Marque abaixo os mobs que quer atacar.\n3) Abra o jogo (paisagem). No painel do topo: ▶ iniciar/pausar · ⌖ calibrar · 🎯 escolher o mob a capturar · 👾 capturar sprite (toque no mob).\n4) Capture 1+ sprites de cada mob marcado e toque ▶."))
        col.addView(status)
        col.addView(btn("Abrir configurações de Acessibilidade") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })

        col.addView(txt("Mobs para atacar (segure um mob para apagar os sprites dele)", 17f))
        val sel = cfg.selected()
        for (m in Mobs.ALL) {
            val cb = CheckBox(this).apply {
                isChecked = m.name in sel
                setOnCheckedChangeListener { _, on ->
                    val s = cfg.selected().toMutableSet(); if (on) s.add(m.name) else s.remove(m.name); cfg.setSelected(s)
                }
                setOnLongClickListener {
                    Mobs.dir(filesDir, m).deleteRecursively(); BotService.inst?.loadTemplates(); refreshRows()
                    Toast.makeText(this@MainActivity, "Sprites de ${m.name} apagados", Toast.LENGTH_SHORT).show(); true
                }
            }
            rows.add(Pair(cb, m)); col.addView(cb)
        }

        col.addView(txt("Skills e opções", 17f))
        for ((k, label) in Config.BOOLS) {
            col.addView(CheckBox(this).apply {
                text = label; isChecked = cfg.b(k)
                setOnCheckedChangeListener { _, on -> cfg.put(k, if (on) 1f else 0f) }
            })
        }

        col.addView(txt("Ajustes numéricos", 17f))
        val edits = HashMap<String, EditText>()
        for ((k, label) in Config.NUMS) {
            col.addView(txt(label))
            val e = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(cfg.f(k).toString())
            }
            edits[k] = e; col.addView(e)
        }
        col.addView(btn("Salvar ajustes numéricos") {
            for ((k, e) in edits) e.text.toString().toFloatOrNull()?.let { cfg.put(k, it) }
            Toast.makeText(this, "Salvo", Toast.LENGTH_SHORT).show()
        })
        col.addView(btn("Apagar TODOS os sprites") {
            File(filesDir, "tpl").deleteRecursively(); BotService.inst?.loadTemplates(); refreshRows()
        })
        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        refreshRows()
        status.text = if (BotService.inst != null) "Serviço: ATIVO ✅" else "Serviço: desligado ❌ (ative em Acessibilidade → Aimo Bot)"
    }
}
