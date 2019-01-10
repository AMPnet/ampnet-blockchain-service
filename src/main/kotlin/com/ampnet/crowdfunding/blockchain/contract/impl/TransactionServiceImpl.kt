package com.ampnet.crowdfunding.blockchain.contract.impl

import com.ampnet.crowdfunding.blockchain.config.ApplicationProperties
import com.ampnet.crowdfunding.blockchain.contract.TransactionService
import com.ampnet.crowdfunding.blockchain.enums.TransactionState
import com.ampnet.crowdfunding.blockchain.enums.TransactionType
import com.ampnet.crowdfunding.blockchain.enums.WalletType
import com.ampnet.crowdfunding.blockchain.exception.TransactionParseException
import com.ampnet.crowdfunding.blockchain.persistence.model.Transaction
import com.ampnet.crowdfunding.blockchain.persistence.model.Wallet
import com.ampnet.crowdfunding.blockchain.persistence.repository.TransactionRepository
import com.ampnet.crowdfunding.blockchain.persistence.repository.WalletRepository
import org.springframework.stereotype.Service
import org.web3j.abi.TypeDecoder
import org.web3j.abi.datatypes.Address
import org.web3j.crypto.Hash
import org.web3j.protocol.Web3j

@Service
class TransactionServiceImpl(
        val web3j: Web3j,
        val transactionRepository: TransactionRepository,
        val walletRepository: WalletRepository,
        val properties: ApplicationProperties
) : TransactionService {

    override fun postTransaction(txData: String): String {
        val result = web3j.ethSendRawTransaction(txData).send()
        return result.transactionHash
    }

    override fun persistTransaction(txHash: String): Transaction {
        val ethTransaction = web3j.ethGetTransactionByHash(txHash).send().transaction.get()
        val toAddress = ethTransaction.to
        val input = ethTransaction.input
        val functionHash = ethTransaction.input.substring(0, 10)
        if (toAddress == properties.contracts.ampnetAddress) {
            when (functionHash) {
                Hash.sha3String("addWallet(address)").substring(0, 10) -> {
                    val refMethod = TypeDecoder::class.java.getDeclaredMethod(
                            "decode",
                            String::class.java,
                            Class::class.java
                    )
                    refMethod.isAccessible = true
                    val addressInput = input.substring(10)
                    val address = refMethod.invoke(null, addressInput, Address::class.java) as Address

                    val tx = Transaction::class.java.newInstance()
                    tx.hash = txHash
                    tx.fromAddress = ethTransaction.from
                    tx.toAddress = ethTransaction.to
                    tx.state = TransactionState.PENDING
                    tx.type = TransactionType.WALLET_CREATE
                    tx.amount = null
                    transactionRepository.save(tx)

                    val wallet = Wallet::class.java.newInstance()
                    wallet.hash = txHash
                    wallet.address = address.toString()
                    wallet.type = WalletType.USER
                    wallet.confirmed = false
                    walletRepository.save(wallet)

                    return tx
                }
                else -> {
                    throw TransactionParseException("Only addWallet(address) function currently supported!")
                }
            }
        } else {
            throw TransactionParseException("Only transacting with AMPnet contract currently supported!")
        }
    }
}