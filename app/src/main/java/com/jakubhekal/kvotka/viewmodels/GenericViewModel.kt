package com.jakubhekal.kvotka.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel

open class GenericViewModel(private val dataStore: DataStore<Preferences>) : ViewModel() {
    enum class State { LOADING, UPDATING, SUCCESS, ERROR }
    var state: State by mutableStateOf(State.LOADING)
        protected set
}