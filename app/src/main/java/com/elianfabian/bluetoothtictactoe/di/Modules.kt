package com.elianfabian.bluetoothtictactoe.di

import com.elianfabian.LapisBtRpcConfig
import com.elianfabian.bluetoothtictactoe.data.GameSessionManager
import com.elianfabian.bluetoothtictactoe.rpc.JsonLapisSerializationStrategy
import com.elianfabian.bluetoothtictactoe.ui.discovery.DeviceDiscoveryViewModel
import com.elianfabian.bluetoothtictactoe.ui.game.GameViewModel
import com.elianfabian.lapisbt.LapisBt
import com.elianfabian.lapisbt.logger.LapisLogger
import com.elianfabian.lapisbt.model.BluetoothDevice
import com.elianfabian.lapisbt_rpc.LapisBtRpc
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

val appModule = module {
	single {
		LapisBt.newInstance(
			context = get(),
			logger = LapisLogger.android(minLevel = LapisLogger.Level.Verbose)
		)
	}
	single {
		LapisBtRpc.newInstance(
			lapisBt = get(),
			serializationStrategy = JsonLapisSerializationStrategy(),
			logger = LapisLogger.android(minLevel = LapisLogger.Level.Verbose),
			config = LapisBtRpcConfig(serverServiceRegistrationTimeout = 10.seconds)
		)
	}
	single {
		GameSessionManager(
			lapisBt = get(),
			lapisBtRpc = get(),
			serviceUuid = UUID.fromString("6d61f1f1-1e1e-4e4e-8e8e-123456789abc")
		)
	}
}

val viewModelModule = module {
	viewModel { DeviceDiscoveryViewModel(get(), get(), get()) }
	viewModel { (address: BluetoothDevice.Address, isHost: Boolean) ->
		GameViewModel(get(), get(), get(), address, isHost)
	}
}
