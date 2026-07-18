package com.elianfabian.bluetoothtictactoe.rpc

import com.elianfabian.bluetoothtictactoe.data.InvitationResponse
import com.elianfabian.bluetoothtictactoe.data.PlayerState
import com.elianfabian.lapisbt_rpc.annotation.LapisMethod
import com.elianfabian.lapisbt_rpc.annotation.LapisParam
import com.elianfabian.lapisbt_rpc.annotation.LapisRpc
import kotlinx.coroutines.flow.Flow

@LapisRpc("InvitationService")
interface InvitationService {
    /**
     * Called by the Requester (Host) to invite the other device (Guest).
     */
    @LapisMethod("requestGameInvitation")
    suspend fun requestGameInvitation(
        @LapisParam("sessionId") sessionId: String
    ): InvitationResponse

    /**
     * Called by peers to observe this player's availability.
     */
    @LapisMethod("playerState")
    fun playerState(): Flow<PlayerState>
}
