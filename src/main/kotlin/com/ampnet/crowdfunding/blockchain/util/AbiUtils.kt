package com.ampnet.crowdfunding.blockchain.util

import org.web3j.abi.TypeDecoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.generated.Uint256
import java.math.BigInteger

class AbiUtils {

    companion object {

        fun decodeAddress(input: String): String {
            val refMethod = TypeDecoder::class.java.getDeclaredMethod(
                    "decode",
                    String::class.java,
                    Class::class.java
            )
            refMethod.isAccessible = true
            val address = refMethod.invoke(null, input, Address::class.java) as Address
            return address.value.toLowerCase()
        }

        fun decodeAmount(input: String): BigInteger {
            val refMethod = TypeDecoder::class.java.getDeclaredMethod(
                    "decode",
                    String::class.java,
                    Class::class.java
            )
            refMethod.isAccessible = true
            val amount = refMethod.invoke(null, input, Uint256::class.java) as Uint256
            return amount.value
        }

        fun decodeAddressAndAmount(input: String): Pair<String, BigInteger> {
            val refMethod = TypeDecoder::class.java.getDeclaredMethod(
                    "decode",
                    String::class.java,
                    Int::class.java,
                    Class::class.java
            )
            refMethod.isAccessible = true

            val addressInput = input.substring(0, 64)
            val amountInput = input.substring(64)

            val address = refMethod.invoke(null, addressInput, 0, Address::class.java) as Address
            val amount = refMethod.invoke(null, amountInput, 0, Uint256::class.java) as Uint256

            return Pair(address.value.toLowerCase(), amount.value)
        }
    }
}