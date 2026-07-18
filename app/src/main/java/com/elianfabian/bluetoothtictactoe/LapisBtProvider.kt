package com.elianfabian.bluetoothtictactoe

import android.content.Context
import com.elianfabian.LapisBtRpcConfig
import com.elianfabian.bluetoothtictactoe.data.PlayerRepository
import com.elianfabian.bluetoothtictactoe.rpc.JsonLapisSerializationStrategy
import com.elianfabian.lapisbt.LapisBt
import com.elianfabian.lapisbt.logger.LapisLogger
import com.elianfabian.lapisbt_rpc.LapisBtRpc
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

object LapisBtProvider {

	val TIC_TAC_TOE_UUID: UUID = UUID.fromString("6d61f1f1-1e1e-4e4e-8e8e-123456789abc")

	private var lapisBt: LapisBt? = null
	private var lapisBtRpc: LapisBtRpc? = null
	private var playerRepository: PlayerRepository? = null

	fun getLapisBt(context: Context): LapisBt {
		return lapisBt ?: synchronized(this) {
			lapisBt ?: LapisBt.newInstance(
				context = context.applicationContext,
				logger = LapisLogger.android(minLevel = LapisLogger.Level.Verbose)
			).also { lapisBt = it }
		}
	}

	fun getLapisBtRpc(context: Context): LapisBtRpc {
		return lapisBtRpc ?: synchronized(this) {
			lapisBtRpc ?: LapisBtRpc.newInstance(
				lapisBt = getLapisBt(context),
				serializationStrategy = JsonLapisSerializationStrategy(),
				logger = LapisLogger.android(minLevel = LapisLogger.Level.Verbose),
				config = LapisBtRpcConfig(serverServiceRegistrationTimeout = 10.seconds)
			).also { lapisBtRpc = it }
		}
	}

	fun getPlayerRepository(context: Context): PlayerRepository {
		return playerRepository ?: synchronized(this) {
			playerRepository ?: PlayerRepository(getLapisBt(context)).also { playerRepository = it }
		}
	}
}
