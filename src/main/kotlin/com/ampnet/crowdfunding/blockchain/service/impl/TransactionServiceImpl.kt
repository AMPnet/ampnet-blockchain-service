package com.ampnet.crowdfunding.blockchain.service.impl

import com.ampnet.crowdfunding.blockchain.config.ApplicationProperties
import com.ampnet.crowdfunding.blockchain.enums.TransactionState
import com.ampnet.crowdfunding.blockchain.enums.TransactionType
import com.ampnet.crowdfunding.blockchain.enums.WalletType
import com.ampnet.crowdfunding.blockchain.persistence.model.Transaction
import com.ampnet.crowdfunding.blockchain.persistence.model.Wallet
import com.ampnet.crowdfunding.blockchain.persistence.repository.TransactionRepository
import com.ampnet.crowdfunding.blockchain.persistence.repository.WalletRepository
import com.ampnet.crowdfunding.blockchain.service.TransactionService
import io.grpc.Status
import org.springframework.stereotype.Service
import org.web3j.abi.TypeDecoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.generated.Uint256
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
        if (!tx.isPresent) {
            throw Status.NOT_FOUND
                    .withDescription("Transaction with txHash: $txHash does not exist!")
                    .asRuntimeException()
        }
        return tx.get()
    }

    override fun getAllTransactions(wallet: String): List<Transaction> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun getAddressFromHash(hash: String): String {
        val tx = getTransaction(hash)
        when (tx.state) {
            TransactionState.MINED -> {
                val receipt = web3j.ethGetTransactionReceipt(hash).send().transactionReceipt.get()
                when (tx.type) {
                    TransactionType.WALLET_CREATE, TransactionType.ORG_CREATE, TransactionType.ORG_ADD_PROJECT -> {
                        return decodeAddress(receipt.logs.first().topics[1])
                    }
                    else -> {
                        throw Status.INVALID_ARGUMENT
                                .withDescription("Provided txHash: ${tx.hash} does not reference wallet creation transaction!")
                                .asRuntimeException()
                    }
                }
            }
            TransactionState.PENDING -> {
                throw Status.UNAVAILABLE
                        .withDescription("Transaction with txHash: ${tx.hash} not yet mined!")
                        .asRuntimeException()
            }
            TransactionState.FAILED -> {
                throw Status.INTERNAL
                        .withDescription("Transaction with txHash: ${tx.hash} failed!")
                        .asRuntimeException()
            }
        }
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
        val inputData = input.substring(8)

        // TODO: - refactor
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
                    throw Status.INVALID_ARGUMENT
                            .withDescription("Only addWallet(address) function currently supported!")
                            .asRuntimeException()
                }
            }
        } else if (toAddress == properties.contracts.eurAddress) {
            when (functionHash) {
                Hash.sha3String("mint(address,uint256)").substring(0, 10) -> {
                    val tx = Transaction::class.java.newInstance()

                    val refMethod = TypeDecoder::class.java.getDeclaredMethod(
                            "decode",
                            String::class.java,
                            Int::class.java,
                            Class::class.java
                    )
                    refMethod.isAccessible = true
                    val addressInput = inputData.substring(0, 64)
                    val amountInput = inputData.substring(64)
                    val address = refMethod.invoke(null, addressInput, 0, Address::class.java) as Address
                    val amount = refMethod.invoke(null, amountInput, 0, Uint256::class.java) as Uint256

                    tx.hash = txHash
                    tx.fromAddress = signedTx.from
                    tx.toAddress = address.value
                    tx.input = signedTx.data
                    tx.state = TransactionState.PENDING
                    tx.type = TransactionType.DEPOSIT
                    tx.amount = amount.value

                    transactionRepository.save(tx)

                    return tx
                }
                Hash.sha3String("burnFrom(address,uint256)").substring(0, 10) -> {
                    val tx = Transaction::class.java.newInstance()

                    val refMethod = TypeDecoder::class.java.getDeclaredMethod(
                            "decode",
                            String::class.java,
                            Int::class.java,
                            Class::class.java
                    )
                    refMethod.isAccessible = true
                    val addressInput = inputData.substring(0, 64)
                    val amountInput = inputData.substring(64)
                    val address = refMethod.invoke(null, addressInput, 0, Address::class.java) as Address
                    val amount = refMethod.invoke(null, amountInput, 0, Uint256::class.java) as Uint256

                    tx.hash = txHash
                    tx.fromAddress = address.value
                    tx.toAddress = signedTx.from
                    tx.input = signedTx.data
                    tx.state = TransactionState.PENDING
                    tx.type = TransactionType.WITHDRAW
                    tx.amount = amount.value

                    transactionRepository.save(tx)

                    return tx
                }
                Hash.sha3String("approve(address,uint256)").substring(0, 10) -> {
                    val tx = Transaction::class.java.newInstance()

                    val refMethod = TypeDecoder::class.java.getDeclaredMethod(
                            "decode",
                            String::class.java,
                            Int::class.java,
                            Class::class.java
                    )
                    refMethod.isAccessible = true
                    val addressInput = inputData.substring(0, 64)
                    val amountInput = inputData.substring(64)
                    val address = refMethod.invoke(null, addressInput, 0, Address::class.java) as Address
                    val amount = refMethod.invoke(null, amountInput, 0, Uint256::class.java) as Uint256

                    tx.hash = txHash
                    tx.fromAddress = signedTx.from
                    tx.toAddress = address.value
                    tx.input = signedTx.data
                    tx.state = TransactionState.PENDING
                    tx.type = TransactionType.PENDING_WITHDRAW
                    tx.amount = amount.value

                    transactionRepository.save(tx)

                    return tx
                }
                Hash.sha3String("transfer(address,uint256)").substring(0, 10) -> {
                    val refMethod = TypeDecoder::class.java.getDeclaredMethod(
                            "decode",
                            String::class.java,
                            Int::class.java,
                            Class::class.java
                    )
                    refMethod.isAccessible = true
                    val addressInput = inputData.substring(0, 64)
                    val amountInput = inputData.substring(64)
                    val address = refMethod.invoke(null, addressInput, 0, Address::class.java) as Address
                    val amount = refMethod.invoke(null, amountInput, 0, Uint256::class.java) as Uint256

                    val tx = Transaction::class.java.newInstance()

                    tx.hash = txHash
                    tx.fromAddress = signedTx.from
                    tx.toAddress = address.value
                    tx.input = signedTx.data
                    tx.state = TransactionState.PENDING
                    tx.type = TransactionType.TRANSFER
                    tx.amount = amount.value

                    transactionRepository.save(tx)

                    return tx
                }
                else -> {
                    throw Status.INVALID_ARGUMENT
                            .withDescription(
                                    """"
                                    |Only mint(address,uint256), burnFrom(address,uint256),
                                    |approve(address,uint256) and transfer(address,uint256)
                                    |functions currently supported!
                                    """.trimMargin())
                            .asRuntimeException()
                }
            }
        } else {
            throw Status.INVALID_ARGUMENT
                    .withDescription("Only transacting with AMPnet and EUR contracts currently supported!")
                    .asRuntimeException()
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
        throw Status.FAILED_PRECONDITION
                .withDescription("Transaction processing rejected! Caller not platform user.")
                .asRuntimeException()
    }

    private fun decodeAddress(input: String): String {
        val refMethod = TypeDecoder::class.java.getDeclaredMethod(
                "decode",
                String::class.java,
                Class::class.java
        )
        refMethod.isAccessible = true
        val address = refMethod.invoke(null, input, Address::class.java) as Address
        return address.value
    }
}