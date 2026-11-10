package com.mphone.service

import android.telecom.Call
import android.telecom.CallScreeningService
import com.mphone.data.CallLogRepository
import com.mphone.data.CountryCodeFilter
import com.mphone.data.TurkeySpamNumberBlocker

class MphoneCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: ""

        val callLogRepo = CallLogRepository(applicationContext)
        val isBlocked = callLogRepo.isNumberBlocked(phoneNumber) ||
                CountryCodeFilter.isFromOutsideTurkey(phoneNumber) ||
                TurkeySpamNumberBlocker.isTurkishSpamNumber(phoneNumber)

        if (isBlocked) {
            // Automatically reject, silence ringer completely, and block incoming call
            val response = CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSilenceCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build()
            respondToCall(callDetails, response)
        } else {
            val response = CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .build()
            respondToCall(callDetails, response)
        }
    }
}
