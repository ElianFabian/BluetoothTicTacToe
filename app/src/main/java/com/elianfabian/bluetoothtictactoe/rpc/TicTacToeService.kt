package com.elianfabian.bluetoothtictactoe.rpc

import com.elianfabian.bluetoothtictactoe.data.GameState
import com.elianfabian.lapisbt_rpc.annotation.LapisMethod
import com.elianfabian.lapisbt_rpc.annotation.LapisParam
import com.elianfabian.lapisbt_rpc.annotation.LapisRpc
import kotlinx.coroutines.flow.Flow

@LapisRpc("TicTacToeService")
interface TicTacToeService {
    /**
     * Called by the Guest to observe the authoritative state from the Host.
     */
    @LapisMethod("gameState")
    fun gameState(): Flow<GameState>

    /**
     * Called by the Guest to perform a move on the Host's board.
     */
    @LapisMethod("makeMove")
    suspend fun makeMove(
        @LapisParam("row") row: Int,
        @LapisParam("col") col: Int
    ): Boolean

    /**
     * Called by the Guest to request a game restart from the Host.
     */
    @LapisMethod("restartGame")
    suspend fun restartGame(): Boolean
}
