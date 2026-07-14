package com.elianfabian.bluetoothtictactoe.data

import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    val board: List<List<Cell>> = List(3) { List(3) { Cell.Empty } },
    val hostSymbol: Cell = Cell.X,
    val guestSymbol: Cell = Cell.O,
    val currentTurn: Cell = Cell.X,
    val winner: Cell? = null,
    val isDraw: Boolean = false,
    val status: GameStatus = GameStatus.Waiting
)

@Serializable
enum class Cell { Empty, X, O }

@Serializable
enum class GameStatus {
    Waiting,
    Playing,
    Finished,
    OpponentDisconnected
}
