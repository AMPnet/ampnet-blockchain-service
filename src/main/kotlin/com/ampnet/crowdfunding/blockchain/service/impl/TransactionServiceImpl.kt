package com.ampnet.crowdfunding.blockchain.service.impl

import com.ampnet.crowdfunding.blockchain.config.ApplicationProperties
import com.ampnet.crowdfunding.blockchain.enums.TransactionState
import com.ampnet.crowdfunding.blockchain.enums.TransactionType
import com.ampnet.crowdfunding.blockchain.enums.WalletType
import com.ampnet.crowdfunding.blockchain.exception.TransactionParseException
import com.ampnet.crowdfunding.blockchain.persistence.model.Transaction
import com.ampnet.crowdfunding.blockchain.persistence.model.Wallet
import com.ampnet.crowdfunding.blockchain.persistence.repository.TransactionRepository
import com.ampnet.crowdfunding.blockchain.persistence.repository.WalletRepository
import com.ampnet.crowdfunding.blockchain.service.TransactionService
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import org.springframework.stereotype.Service
import org.web3j.abi.TypeDecoder
import org.web3j.abi.datatypes.Address
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.SignedRawTransaction
import org.web3j.crypto.TransactionDecoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.response.EthSendTransaction
import javax.transaction.Transactional

@Service
class TransactionServiceImpl(
        val web3j: Web3j,
        val transactionRepository: TransactionRepository,
        val walletRepository: WalletRepository,
        val properties: ApplicationProperties
) : TransactionService {

    override fun postAndCacheTransaction(txData: String, onComplete: (Transaction) -> Unit) {
        throwIfCallerNotMemberOfAmpnet(txData)
        web3j.ethSendRawTransaction(txData).sendAsync().thenAccept { sendTx ->
            val txHash = sendTx.transactionHash
            val tx = persistTransaction(txData, txHash)

            // Try to wait for mined event and update tx right away (if fails scheduled job will handle it anyway)
            web3j.ethGetTransactionReceipt(sendTx.transactionHash).flowable().subscribe { receipt ->
                if (receipt.result.isStatusOK) {
                    tx.state = TransactionState.MINED
                } else {
                    tx.state = TransactionState.FAILED
                }
                transactionRepository.save(tx)
            }

            onComplete(tx)
        }
    }

    override fun getTransaction(txHash: String): Transaction {
        val tx = transactionRepository.findByHash(txHash)
        if(!tx.isPresent) {
            // TODO: - throw error
        }
        return tx.get()
    }

    override fun getAllTransactions(wallet: String): List<Transaction> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getAddressFromHash(hash: String): String {
        val tx = getTransaction(hash)
        when (tx.state) {
            TransactionState.MINED -> {
                val receipt = web3j.ethGetTransactionReceipt(hash).send().transactionReceipt.get()
                when (tx.type) {
                    TransactionType.WALLET_CREATE -> {
                        val refMethod = TypeDecoder::class.java.getDeclaredMethod(
                                "decode",
                                String::class.java,
                                Class::class.java
                        )
                        refMethod.isAccessible = true
                        val addressInput = receipt.logs.first().topics[1]
                        val address = refMethod.invoke(null, addressInput, Address::class.java) as Address
                        return address.value
                    }
                    TransactionType.ORG_CREATE -> {
                        val refMethod = TypeDecoder::class.java.getDeclaredMethod(
                                "decode",
                                String::class.java,
                                Class::class.java
                        )
                        refMethod.isAccessible = true
                        val addressInput = receipt.logs.first().topics[1]
                        val address = refMethod.invoke(null, addressInput, Address::class.java) as Address
                        return address.value
                    }
                    TransactionType.ORG_ADD_PROJECT -> {

                    }
                    else -> {
                        // TODO: - throw error
                    }
                }
            }
            TransactionState.PENDING -> {
                // TODO: - throw error
            }
            TransactionState.FAILED -> {
                // TODO: - throw error
            }
        }
        TODO("finish")
    }

    override fun updateTransactionStates() {
        val pendingTransactions = transactionRepository.findAllPending()
        for (tx in pendingTransactions) {
            web3j.ethGetTransactionReceipt(tx.hash).send().transactionReceipt.ifPresent {
                if (it.isStatusOK) {
                    tx.state = TransactionState.MINED
                } else {
                    tx.state = TransactionState.FAILED
                }
                transactionRepository.save(tx)
            }
        }
    }

    @Transactional
    fun persistTransaction(txData: String, txHash: String): Transaction {
        val signedTx = TransactionDecoder.decode(txData) as SignedRawTransaction
        val toAddress = signedTx.to
        val input = signedTx.data
        val functionHash = "0x${input.substring(0, 8)}"
        if (toAddress == properties.contracts.ampnetAddress) {
            when (functionHash) {
                Hash.sha3String("addWallet(address)").substring(0, 10) -> {
                    val tx = Transaction::class.java.newInstance()
                    tx.hash = txHash
                    tx.fromAddress = signedTx.from
                    tx.toAddress = signedTx.to
                    tx.input = signedTx.data
                    tx.state = TransactionState.PENDING
                    tx.type = TransactionType.WALLET_CREATE
                    tx.amount = null
                    transactionRepository.save(tx)

                    val wallet = Wallet::class.java.newInstance()
                    wallet.hash = txHash
                    wallet.address = null
                    wallet.type = WalletType.USER
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

    private fun throwIfCallerNotMemberOfAmpnet(txData: String) {

        // Extract caller's address from signed transaction
        val signedTx = TransactionDecoder.decode(txData) as SignedRawTransaction
        val fromAddress = signedTx.from

        // Check if caller is either AMPnet or Issuing Authority
        val issuingAuthority = properties.accounts.issuingAuthorityAddress
        val ampnetAuthority = Credentials.create(properties.accounts.ampnetPrivateKey).address
        if (fromAddress == issuingAuthority || fromAddress == ampnetAuthority) {
            return
        }

        // Check if caller is registered AMPnet user
        val wallet = walletRepository.findByAddress(fromAddress)
        if (wallet.isPresent) {
            return
        }

        // User is forbidden to broadcast this transaction, throw error
        // TODO - throw error

    }

}