package com.elianfabian.bluetoothtictactoe.rpc

import com.elianfabian.bluetoothtictactoe.data.GameState
import com.elianfabian.lapisbt.LapisMethod
import com.elianfabian.lapisbt.LapisParam
import com.elianfabian.lapisbt.LapisRpc
import kotlinx.coroutines.flow.Flow

@LapisRpc("TicTacToeService")
interface TicTacToeService {
    /**
     * Called by the Guest to invite the Host to a new game.
     * The implementation on the Host side should suspend until the user accepts or declines.
     */
    @LapisMethod("requestGame")
    suspend fun requestGame(): Boolean

    /**
     * Streams the authoritative game state from the Host to the Guest.
     */
    @LapisMethod("gameState")
    fun gameState(): Flow<GameState>

    /**
     * Called by either player to make a move on the 3x3 grid.
     */
    @LapisMethod("makeMove")
    suspend fun makeMove(
        @LapisParam("row") row: Int,
        @LapisParam("col") col: Int
    ): Boolean
}
