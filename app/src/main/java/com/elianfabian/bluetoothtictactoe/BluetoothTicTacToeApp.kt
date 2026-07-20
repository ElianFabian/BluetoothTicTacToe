package com.elianfabian.bluetoothtictactoe

import android.app.Application
import com.elianfabian.bluetoothtictactoe.di.appModule
import com.elianfabian.bluetoothtictactoe.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class BluetoothTicTacToeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@BluetoothTicTacToeApp)
            modules(appModule, viewModelModule)
        }
    }
}
