package com.bomjRogue

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.collections.HashMap
import kotlin.random.Random

class Game {
    fun run() {
        while (true) {
            runBlocking { delay(10) }
            logic()
        }
    }

    private val players = mutableListOf<Player>()
    private val exitDoor = ExitDoor()
    private val defaultNpcCount = 5
    private var npcCount = defaultNpcCount
    private val map = LevelGenerator.generateMap()
    private var npcs = mutableListOf<Npc>()
    private var items = mutableListOf<Item>()
    private var updates = mutableListOf<Update>()
    private val updatesMutex = Mutex()

    private fun addUpdate(update: Update) {
        runBlocking {
            updatesMutex.withLock {
                updates.add(update)
            }
        }
    }

    suspend fun getUpdates(): List<Update> {
        updatesMutex.withLock {
            val upd = ArrayList(updates)
            updates = mutableListOf()
            return upd
        }
    }

    fun join(playerName: String): Character {
        val player = Player(
            playerName, Characteristics(
                mutableMapOf(
                    CharacteristicType.Health to 100,
                    CharacteristicType.Armor to 10,
                    CharacteristicType.Force to 20
                )
            )
        )
        players.add(player)
        map.add(player, Position(Coordinates(20f, 20f), Size(34f, 19f)))
        return player
    }

    fun makeMove(name: String, x: Float, y: Float) {
        val player = players.firstOrNull { it.name == name } ?: return
        map.move(player, x, y)
        if (map.objectsConnect(player, exitDoor)) {
            if (npcs.isNotEmpty()) {
                npcCount++
            }
            initialize(false)
        }
    }

    fun hit(name: String) {
        val player = players.firstOrNull { it.name == name } ?: return
        makeDamage(player)
    }

    fun initialize(reset: Boolean = true) {
        map.reset()
        initializeNpcs()
        initializeItems()
        if (reset) {
            players.forEach { it.reset() }
        }
        players.forEach {
            map.add(it, Position(Coordinates(20f, 20f), Size(34f, 19f)))
        }
        map.add(exitDoor, Position(Coordinates(1220f, 10f), Size(46f, 32f)))
    }

    private fun initializeItems() {
        items.clear()
        val weapon = Sword(5)
        val health = Health(50)
        items.add(weapon)
        items.add(health)
        map.addRandomPlace(weapon, Size(40f, 40f))
        map.addRandomPlace(health, Size(25f, 25f))
    }

    fun getGameItems(): GameItems {
        return HashMap(map.location)
    }

    private fun initializeNpcs() {
        npcs.clear()
        for (i in 0 until npcCount) {
            npcs.add(
                Npc(
                    "Npc_$i", Characteristics(
                        mutableMapOf(
                            CharacteristicType.Health to 100,
                            CharacteristicType.Armor to 10,
                            CharacteristicType.Force to 20
                        )
                    )
                )
            )
        }
        npcs.forEach { map.addRandomPlace(it, Size(36f, 20f)) }
    }

    fun makeDamage(hitman: Character): Boolean {
        var noDamage = true
        for (pl in npcs + players) {
            if (pl != hitman && map.objectsConnect(hitman, pl)) {
                if (pl is Player) {
                    addUpdate(PlayerUpdate(pl))
                }
                noDamage = false
                pl.takeDamage(hitman.getForce())
                if (pl.getHealth() < 0) {
                    map.remove(pl)
                    npcs.remove(pl)
                    if (players.count() == 0) {
                        npcCount = defaultNpcCount
                        initialize()
                    }
                }
            }
        }
        return !noDamage
    }

    private fun logic() {
        moveNpcs()
        pickItems()
    }

    private fun moveNpcs() {
        for (npc in npcs) {
            for (player in players) {
                if (map.objectsConnect(npc, player)) {
                    if (Random.nextInt(100) < 5) {
                        makeDamage(npc)
                    }
                }
            }
            if (Random.nextInt(100) < 5) {
                npc.direction = Direction.values()[Random.nextInt(4)]
            } else {
                val x = when (npc.direction) {
                    Direction.Left -> -1f
                    Direction.Right -> +1f
                    else -> 0f
                }
                val y = when (npc.direction) {
                    Direction.Down -> +1f
                    Direction.Up -> -1f
                    else -> 0f
                }
                map.move(npc, x, y)
            }
        }
    }


    private fun pickItems() {
        val toRemove = mutableListOf<Item>()
        for (item in items) {
            for (player in players) {
                if (map.objectsConnect(item, player)) {
                    if (item is Health) {
                        player.addHealth(item.healthPoints)
                    } else if (item is Sword) {
                        player.addForce(item.damage)
                    }
                    addUpdate(PlayerUpdate(player))
                    toRemove.add(item)
                }
            }
        }
        toRemove.forEach {
            items.remove(it)
            map.remove(it)
        }
    }
}