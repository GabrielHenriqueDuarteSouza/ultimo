package com.example.aimobot

import java.io.File

/** Catálogo de mobs (nomes/níveis da wiki do jogo). Sprites são capturados por você no jogo e ligados ao nome. */
object Mobs {
    class Mob(val name: String, val area: String, val level: Int?, val note: String = "") {
        fun label(sprites: Int) =
            "$name  (nv ${level ?: "?"}${if (note.isEmpty()) "" else ", $note"})\n$area • sprites: $sprites"
    }

    val ALL: List<Mob> = listOf(
        Mob("Red Crab", "Wingfril Island Beach / Ilha do Farol", 10),
        Mob("Sandman", "Wingfril Island Beach", 11),
        Mob("Woopa", "Wingfril Island Beach / Ilha do Farol", 12),
        Mob("Roopa", "Wingfril Island Beach / Ilha do Farol", 13),
        Mob("Mutant Woopa", "Wingfril Island Beach / Ilha do Farol", 13, "mini-boss"),
        Mob("Chief Wooparoopa", "Wingfril Island Beach / Ilha do Farol", 14, "boss"),
        Mob("Turtle Z", "Wingfril Island Beach", 18, "boss"),
        Mob("Testing Woopa", "Lighthouse Dungeon 1F", 15),
        Mob("Marsh Donguri", "Lighthouse Dungeon 1F", null),
        Mob("Skeleton", "Lighthouse Dungeon 1F", null),
        Mob("Recluse", "Lighthouse Dungeon 1F", 15, "boss"),
        Mob("Beholder", "Lighthouse Dungeon 2F", null),
        Mob("Drunken Kooii", "Lighthouse Dungeon 2F", null),
        Mob("Mushroom Spore", "Mushroom Marshland", null),
        Mob("Poisonous Mushy", "Mushroom Marshland", null),
        Mob("Flower Elemental", "Mushroom Marshland", null),
        Mob("Fox", "Forest of Grave / Lanos Plains", null),
        Mob("Crow", "Forest of Grave / Lanos Plains", null),
        Mob("Outro (manual)", "qualquer mapa", null)
    )

    fun safe(name: String) = name.lowercase().replace(Regex("[^a-z0-9]+"), "_")
    fun dir(filesDir: File, m: Mob) = File(File(filesDir, "tpl"), safe(m.name))
}
