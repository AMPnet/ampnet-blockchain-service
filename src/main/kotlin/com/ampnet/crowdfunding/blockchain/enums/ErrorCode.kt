package com.ampnet.crowdfunding.blockchain.enums

/**
 * Custom error codes sent as grpc response.
 * Format: {id} > {custom message text}
 * Example: "52 > Wallet with address 0x1235.. already exists!"
 */
enum class ErrorCode(val code: Int) {

    WALLET_CREATION_FAILED(50),
    WALLET_CREATION_PENDING(51),
    WALLET_ALREADY_EXISTS(52),
    TX_HASH_ALREADY_EXISTS(53),
    CALLER_NOT_REGISTERED(54),
    INVALID_TX_TYPE(55),
    WALLET_DOES_NOT_EXIST(56),
    INVALID_FUNCTION_CALL(57);

    fun withMessage(message: String) = "$code > $message"

    companion object {
        fun fromMessage(message: String) = message.split(">")[0].trim().toInt()
    }
}
