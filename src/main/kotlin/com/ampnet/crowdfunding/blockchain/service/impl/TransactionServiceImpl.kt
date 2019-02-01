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
import com.ampnet.crowdfunding.blockchain.service.WalletService
import io.grpc.Status
import org.springframework.stereotype.Service
import org.web3j.abi.TypeDecoder
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.crypto.Credentials
import org.web3j.crypto.SignedRawTransaction
import org.web3j.crypto.TransactionDecoder
import org.web3j.protocol.Web3j
import java.math.BigInteger
import java.time.ZonedDateTime
import javax.transaction.Transactional

@Service
class TransactionServiceImpl(
        val web3j: Web3j,
        val transactionRepository: TransactionRepository,
        val walletRepository: WalletRepository,
        val walletService: WalletService,
        val properties: ApplicationProperties
) : TransactionService {

    @Transactional
    override fun postAndCacheTransaction(txData: String, txType: TransactionType): Transaction {
        throwIfCallerNotMemberOfAmpnet(txData)
        throwIfTxTypeDoesNotMatchActualTx(txData, txType)

        val txHash = web3j.ethSendRawTransaction(txData).send().transactionHash.toLowerCase()
        val tx = persistTransaction(txData, txHash)

        // Try to wait for mined event and update tx right away (if fails scheduled job will handle it anyway)
        web3j.ethGetTransactionReceipt(txHash).flowable().subscribe { receipt ->
            if (receipt.result.isStatusOK) {
                tx.state = TransactionState.MINED
            } else {
                tx.state = TransactionState.FAILED
            }
            tx.processedAt = ZonedDateTime.now()
            transactionRepository.save(tx)
        }

        return tx
    }

    @Transactional
    override fun getTransaction(txHash: String): Transaction {
        val tx = transactionRepository.findByHash(txHash)
        if (!tx.isPresent) {
            throw Status.NOT_FOUND
                    .withDescription("Transaction with txHash: $txHash does not exist!")
                    .asRuntimeException()
        }
        return tx.get()
    }

    @Transactional
    override fun getAllTransactions(wallet: String): List<Transaction> {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    @Transactional
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

    @Transactional
    override fun updateTransactionStates() {
        val pendingTransactions = transactionRepository.findAllPending()
        for (tx in pendingTransactions) {
            web3j.ethGetTransactionReceipt(tx.hash).send().transactionReceipt.ifPresent {
                if (it.isStatusOK) {
                    tx.state = TransactionState.MINED
                } else {
                    tx.state = TransactionState.FAILED
                }
                tx.processedAt = ZonedDateTime.now()
                transactionRepository.save(tx)
            }
        }
    }

    private fun persistTransaction(txData: String, txHash: String): Transaction {
        val signedTx = TransactionDecoder.decode(txData) as SignedRawTransaction
        val toAddress = signedTx.to.toLowerCase()
        val input = signedTx.data
        val functionHash = input.substring(0, 8).toLowerCase()
        val inputData = input.substring(8)

        // TODO: - refactor
        if (toAddress == properties.contracts.ampnetAddress.toLowerCase()) {
            when (functionHash) {
                TransactionType.WALLET_CREATE.functionHash -> {
                    val tx = Transaction::class.java.newInstance()
                    tx.hash = txHash
                    tx.fromWallet = signedTx.from.toLowerCase()
                    tx.toWallet = txHash
                    tx.input = signedTx.data
                    tx.state = TransactionState.PENDING
                    tx.type = TransactionType.WALLET_CREATE
                    tx.amount = null
                    tx.createdAt = ZonedDateTime.now()
                    transactionRepository.save(tx)

                    val wallet = Wallet::class.java.newInstance()
                    wallet.hash = txHash
                    wallet.address = null
                    wallet.type = WalletType.USER
                    walletRepository.save(wallet)

                    return tx
                }
                TransactionType.ORG_CREATE.functionHash -> {
                    val tx = Transaction::class.java.newInstance()
                    tx.hash = txHash
                    tx.fromWallet = walletService.getTxHash(signedTx.from.toLowerCase())
                    tx.toWallet = signedTx.to.toLowerCase()
                    tx.input = signedTx.data
                    tx.state = TransactionState.PENDING
                    tx.type = TransactionType.ORG_CREATE
                    tx.amount = null
                    tx.createdAt = ZonedDateTime.now()
                    transactionRepository.save(tx)

                    val wallet = Wallet::class.java.newInstance()
                    wallet.hash = txHash
                    wallet.address = null
                    wallet.type = WalletType.ORG
                    walletRepository.save(wallet)

                    return tx
                }
                TransactionType.ORG_ACTIVATE.functionHash -> {
                    val tx = Transaction::class.java.newInstance()
                    val orgAddress = decodeAddress(inputData)

                    tx.hash = txHash
                    tx.fromWallet = signedTx.from.toLowerCase()
                    tx.toWallet = walletService.getTxHash(orgAddress).toLowerCase()
                    tx.input = signedTx.data
                    tx.state = TransactionState.PENDING
                    tx.type = TransactionType.ORG_ACTIVATE
                    tx.amount = null
                    tx.createdAt = ZonedDateTime.now()
                    transactionRepository.save(tx)

                    return tx
                }
                else -> {
                    throw Status.INVALID_ARGUMENT
                            .withDescription("Only addWallet(address) function currently supported!")
                            .asRuntimeException()
                }
            }
        } else if (toAddress == properties.contracts.eurAddress.toLowerCase()) {
            when (functionHash) {
                TransactionType.DEPOSIT.functionHash -> {
                    val tx = Transaction::class.java.newInstance()
                    val (address, amount) = decodeAddressAndAmount(inputData)

                    tx.hash = txHash
                    tx.fromWallet = signedTx.from.toLowerCase()
                    tx.toWallet = walletService.getTxHash(address.toLowerCase())
                    tx.input = signedTx.data
                    tx.state = TransactionState.PENDING
                    tx.type = TransactionType.DEPOSIT
                    tx.amount = amount
                    tx.createdAt = ZonedDateTime.now()

                    transactionRepository.save(tx)

                    return tx
                }
                TransactionType.WITHDRAW.functionHash -> {
                    val tx = Transaction::class.java.newInstance()
                    val (address, amount) = decodeAddressAndAmount(inputData)

                    tx.hash = txHash
                    tx.fromWallet = walletService.getTxHash(address.toLowerCase())
                    tx.toWallet = signedTx.from.toLowerCase()
                    tx.input = signedTx.data
                    tx.state = TransactionState.PENDING
                    tx.type = TransactionType.WITHDRAW
                    tx.amount = amount
                    tx.createdAt = ZonedDateTime.now()

                    transactionRepository.save(tx)

                    return tx
                }
                TransactionType.PENDING_WITHDRAW.functionHash -> {
                    val tx = Transaction::class.java.newInstance()
                    val (address, amount) = decodeAddressAndAmount(inputData)

                    tx.hash = txHash
                    tx.fromWallet = walletService.getTxHash(signedTx.from.toLowerCase())
                    tx.toWallet = address.toLowerCase()
                    tx.input = signedTx.data
                    tx.state = TransactionState.PENDING
                    tx.type = TransactionType.PENDING_WITHDRAW
                    tx.amount = amount
                    tx.createdAt = ZonedDateTime.now()

                    transactionRepository.save(tx)

                    return tx
                }
                TransactionType.TRANSFER.functionHash -> {
                    val tx = Transaction::class.java.newInstance()
                    val (address, amount) = decodeAddressAndAmount(inputData)

                    tx.hash = txHash
                    tx.fromWallet = walletService.getTxHash(signedTx.from.toLowerCase())
                    tx.toWallet = walletService.getTxHash(address.toLowerCase())
                    tx.input = signedTx.data
                    tx.state = TransactionState.PENDING
                    tx.type = TransactionType.TRANSFER
                    tx.amount = amount
                    tx.createdAt = ZonedDateTime.now()

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
        val fromAddress = signedTx.from.toLowerCase()

        // Check if caller is either AMPnet or Issuing Authority
        val issuingAuthority = properties.accounts.issuingAuthorityAddress.toLowerCase()
        val ampnetAuthority = Credentials.create(properties.accounts.ampnetPrivateKey).address.toLowerCase()
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

    private fun throwIfTxTypeDoesNotMatchActualTx(txData: String, txType: TransactionType) {
        val tx = TransactionDecoder.decode(txData)
        val functionSignature = tx.data.substring(0, 8).toLowerCase()
        val actualType = TransactionType.fromFunctionHash(functionSignature)
        if (actualType != txType) {
            throw Status.PERMISSION_DENIED
                    .withDescription("Signed transaction does not match provided tx type.")
                    .asRuntimeException()
        }
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

    private fun decodeAddressAndAmount(input: String): Pair<String, BigInteger> {
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

        return Pair(address.value, amount.value)
    }
}