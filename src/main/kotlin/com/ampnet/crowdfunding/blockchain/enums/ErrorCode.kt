package com.ampnet.crowdfunding.blockchain.enums

enum class ErrorCode(val code: Int) {

    WALLET_CREATION_FAILED(50),
    WALLET_CREATION_PENDING(51);

    fun withMessage(message: String) = "$code > $message"

    companion object {
        fun fromMessage(message: String) = message.split(">")[0].trim().toInt()
    }
}
