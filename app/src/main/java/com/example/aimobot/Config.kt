package com.example.aimobot

import android.content.Context

/** Posições em fração da tela (0..1). dpDist = fração da largura; range/keep = fração da altura. */
class Config(ctx: Context) {
    private val sp = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    fun f(k: String): Float = sp.getFloat(k, DEF[k] ?: 0f)
    fun b(k: String): Boolean = f(k) > 0.5f
    fun put(k: String, v: Float) { sp.edit().putFloat(k, v).apply() }

    fun selected(): Set<String> = sp.getStringSet("selMobs", emptySet()) ?: emptySet()
    fun setSelected(s: Set<String>) { sp.edit().putStringSet("selMobs", HashSet(s)).apply() }
    var captureMob: String
        get() = sp.getString("capMob", "") ?: ""
        set(v) { sp.edit().putString("capMob", v).apply() }

    companion object {
        // Padrões estimados a partir do seu print (paisagem 2576x1159). Use ⌖ (ou "Pular") para ajustar.
        val DEF: Map<String, Float> = mapOf(
            "hpAx" to 0.068f, "hpAy" to 0.057f, "hpBx" to 0.216f, "hpBy" to 0.057f,   // barra vermelha (vida)
            "mpAx" to 0.068f, "mpAy" to 0.102f, "mpBx" to 0.194f, "mpBy" to 0.102f,   // barra azul (mana)
            "dpx" to 0.136f, "dpy" to 0.746f, "dpDist" to 0.0738f,                    // direcional
            "anx" to 0.50f, "any" to 0.445f,                                          // personagem (câmera centrada nele)
            "atx" to 0.924f, "aty" to 0.850f,                                         // botão de ataque
            "lootx" to 0.953f, "looty" to 0.643f,                                     // botão de loot (palpite pelo print: confirme)
            "s1x" to 0.802f, "s1y" to 0.746f, "s1on" to 1f,                           // skills (posições do print)
            "s2x" to 0.807f, "s2y" to 0.910f, "s2on" to 0f,
            "s3x" to 0.833f, "s3y" to 0.595f, "s3on" to 0f,
            "s4x" to 0.887f, "s4y" to 0.470f, "s4on" to 0f,
            "s5x" to 0.959f, "s5y" to 0.405f, "s5on" to 0f,
            "hpLow" to 0.40f, "hpOk" to 0.85f, "mpLow" to 0.15f, "mpOk" to 0.60f,
            "thr" to 40f, "range" to 0.20f, "keep" to 0.12f, "lootTaps" to 2f,
            "tapTarget" to 1f, "lootWalk" to 1f
        )
        val NUMS: List<Pair<String, String>> = listOf(
            "hpLow" to "Descansar se VIDA abaixo de (0-1)",
            "hpOk" to "Voltar a caçar com VIDA acima de (0-1)",
            "mpLow" to "Descansar se MANA abaixo de (0-1)",
            "mpOk" to "Voltar a caçar com MANA acima de (0-1)",
            "range" to "Distância MÁXIMA para atacar (fração da altura da tela, ex 0.20)",
            "keep" to "Distância que o personagem QUER manter do mob (ex 0.12). Se o mob chegar mais perto, bate e recua",
            "lootTaps" to "Toques no botão de loot ao chegar",
            "thr" to "Tolerância do reconhecimento do sprite (menor = mais rígido)"
        )
        val BOOLS: List<Pair<String, String>> = listOf(
            "s1on" to "Usar skill 1", "s2on" to "Usar skill 2", "s3on" to "Usar skill 3",
            "s4on" to "Usar skill 4", "s5on" to "Usar skill 5",
            "tapTarget" to "Tocar no monstro antes de atacar",
            "lootWalk" to "Andar até onde o monstro morreu antes de coletar"
        )
    }
}
