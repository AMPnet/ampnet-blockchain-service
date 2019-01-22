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
import org.springframework.stereotype.Service
import org.web3j.abi.TypeDecoder
import org.web3j.abi.datatypes.Address
import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.SignedRawTransaction
import org.web3j.crypto.TransactionDecoder
import org.web3j.protocol.Web3j
import javax.transaction.Transactional

@Service
class TransactionServiceImpl(
        val web3j: Web3j,
        val transactionRepository: TransactionRepository,
        val walletRepository: WalletRepository,
        val properties: ApplicationProperties
) : TransactionService {

    override fun postTransaction(txData: String): String {
        throwIfCallerNotMemberOfAmpnet(txData)
        val result = web3j.ethSendRawTransaction(txData).send()
//        ### maybe here persist transaction (move persistTransaction() from interface -> private fun)
//        web3j.ethGetTransactionReceipt(result.transactionHash).flowable().subscribe { t ->
//            t.transactionReceipt.ifPresent {
//                  ### do stuff... (transaction mined here) ###
//            }
//        }
        return result.transactionHash
    }

    @Transactional
    override fun persistTransaction(txHash: String): Transaction {
        val ethTransaction = web3j.ethGetTransactionByHash(txHash).send().transaction.get()
        val toAddress = ethTransaction.to
        val input = ethTransaction.input
        val functionHash = ethTransaction.input.substring(0, 10)
        if (toAddress == properties.contracts.ampnetAddress) {
            when (functionHash) {
                Hash.sha3String("addWallet(address)").substring(0, 10) -> {

                    // Example input field parsing to get Address parameter
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
                    tx.input = ethTransaction.input
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

                    }
                    TransactionType.ORG_CREATE -> {

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
                tx.state = TransactionState.valueOf(it.status) // TODO: - 0x0 - failed, 0x1 mined
                transactionRepository.save(tx)
            }
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