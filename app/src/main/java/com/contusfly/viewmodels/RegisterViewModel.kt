package com.contusfly.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contusfly.models.ContactSyncData
import com.contusfly.models.RegisterData
import com.contusfly.network.RetrofitClientNetwork
import kotlinx.coroutines.launch
import java.util.HashMap
import javax.inject.Inject

/**
 *
 * @author ContusTeam <developers@contus.in>
 * @version 1.0
 */
class RegisterViewModel @Inject
constructor(): ViewModel() {

    val registerDataResponse: MutableLiveData<RegisterData> = MutableLiveData()
    val contactSyncDataResponse: MutableLiveData<ContactSyncData> = MutableLiveData()

    fun getRegisterData(params: HashMap<String, String>) {
        viewModelScope.launch {
            registerDataResponse.value = RetrofitClientNetwork.retrofit.registerAsync(params)
        }
    }

    fun getContactSyncData(params: HashMap<String, String>) {
        viewModelScope.launch {
            contactSyncDataResponse.value = RetrofitClientNetwork.retrofit.contactSyncAsync(params)
        }
    }
}