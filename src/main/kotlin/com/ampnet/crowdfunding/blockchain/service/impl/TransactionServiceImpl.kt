package com.ampnet.crowdfunding.blockchain.service.impl

import com.ampnet.crowdfunding.blockchain.config.ApplicationProperties
import com.ampnet.crowdfunding.blockchain.enums.ErrorCode
import com.ampnet.crowdfunding.blockchain.enums.TransactionState
import com.ampnet.crowdfunding.blockchain.enums.TransactionType
import com.ampnet.crowdfunding.blockchain.enums.WalletType
import com.ampnet.crowdfunding.blockchain.persistence.model.Transaction
import com.ampnet.crowdfunding.blockchain.persistence.model.Wallet
import com.ampnet.crowdfunding.blockchain.persistence.repository.TransactionRepository
import com.ampnet.crowdfunding.blockchain.persistence.repository.WalletRepository
import com.ampnet.crowdfunding.blockchain.service.TransactionService
import com.ampnet.crowdfunding.blockchain.service.WalletService
import com.ampnet.crowdfunding.blockchain.util.AbiUtils
import io.grpc.Status
import org.springframework.stereotype.Service
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

    enum class ContractType {
        AMPNET, EUR, ORG, PROJECT;
    }

    @Transactional
    override fun postAndCacheTransaction(txData: String, txType: TransactionType): Transaction {

        // Call three functions as a filter before broadcasting transaction (all can throw):
        // 1) check if caller member of ampnet
        // 2) check if tx type actually matches signed transaction's content
        // 3) check if callee is one of ampnet smart contracts
        throwIfCallerNotMemberOfAmpnet(txData)
        throwIfTxTypeDoesNotMatchActualTx(txData, txType)
        val type = getTargetContractType(txData)

        // Broadcast transaction to network
        val txHash = web3j.ethSendRawTransaction(txData).send().transactionHash
        val tx = persistTransaction(txData, txHash, type)

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
        val tx = transactionRepository.findByHash(txHash.toLowerCase())
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

    private fun persistTransaction(txData: String, txHash: String, type: ContractType): Transaction {
        val signedTx = TransactionDecoder.decode(txData) as SignedRawTransaction
        val input = signedTx.data
        val functionHash = input.substring(0, 8).toLowerCase()
        val inputData = input.substring(8)

        throwIfTxAlreadyExists(txHash)

        when (type) {
            ContractType.AMPNET -> {
                when (functionHash) {
                    TransactionType.WALLET_CREATE.functionHash -> {
                        val address = AbiUtils.decodeAddress(inputData)
                        throwIfAddressAlreadyExists(address)
                        val tx = saveTransaction(
                                hash = txHash,
                                from = signedTx.from,
                                to = txHash,
                                input = signedTx.data,
                                type = TransactionType.WALLET_CREATE
                        )
                        saveWallet(address, WalletType.USER, tx)
                        return tx
                    }
                    TransactionType.ORG_CREATE.functionHash -> {
                        val tx = saveTransaction(
                                hash = txHash,
                                from = walletService.getTxHash(signedTx.from),
                                to = txHash,
                                input = signedTx.data,
                                type = TransactionType.ORG_CREATE
                        )
                        saveWallet(type = WalletType.ORG, transaction = tx)
                        return tx
                    }
                    else -> {
                        throw Status.INVALID_ARGUMENT
                                .withDescription("Only addWallet(address) function currently supported!")
                                .asRuntimeException()
                    }
                }
            }
            ContractType.EUR -> {
                when (functionHash) {
                    TransactionType.DEPOSIT.functionHash -> {
                        val (address, amount) = AbiUtils.decodeAddressAndAmount(inputData)
                        return saveTransaction(
                                hash = txHash,
                                from = signedTx.from,
                                to = walletService.getTxHash(address),
                                input = signedTx.data,
                                type = TransactionType.DEPOSIT,
                                amount = amount
                        )
                    }
                    TransactionType.WITHDRAW.functionHash -> {
                        val (address, amount) = AbiUtils.decodeAddressAndAmount(inputData)
                        return saveTransaction(
                                hash = txHash,
                                from = walletService.getTxHash(address),
                                to = signedTx.from,
                                input = signedTx.data,
                                type = TransactionType.WITHDRAW,
                                amount = amount
                        )
                    }
                    TransactionType.PENDING_WITHDRAW.functionHash -> {
                        val (address, amount) = AbiUtils.decodeAddressAndAmount(inputData)
                        return saveTransaction(
                                hash = txHash,
                                from = walletService.getTxHash(signedTx.from.toLowerCase()),
                                to = address,
                                input = signedTx.data,
                                type = TransactionType.PENDING_WITHDRAW,
                                amount = amount
                        )
                    }
                    TransactionType.TRANSFER.functionHash -> {
                        val (address, amount) = AbiUtils.decodeAddressAndAmount(inputData)
                        return saveTransaction(
                                hash = txHash,
                                from = walletService.getTxHash(signedTx.from),
                                to = walletService.getTxHash(address),
                                input = signedTx.data,
                                type = TransactionType.TRANSFER,
                                amount = amount
                        )
                    }
                    TransactionType.INVEST.functionHash -> {
                        val (address, amount) = AbiUtils.decodeAddressAndAmount(inputData)
                        return saveTransaction(
                                hash = txHash,
                                from = walletService.getTxHash(signedTx.from),
                                to = walletService.getTxHash(address),
                                input = signedTx.data,
                                type = TransactionType.INVEST,
                                amount = amount
                        )
                    }
                    else -> {
                        throw Status.INVALID_ARGUMENT
                                .withDescription(
                                        ErrorCode.INVALID_FUNCTION_CALL.withMessage(
                                                "Function call not recognized!"
                                        )
                                ).asRuntimeException()
                    }
                }
            }
            ContractType.ORG -> {
                when (functionHash) {
                    TransactionType.ORG_ACTIVATE.functionHash -> {
                        return saveTransaction(
                                hash = txHash,
                                from = signedTx.from,
                                to = walletService.getTxHash(signedTx.to.toLowerCase()),
                                input = signedTx.data,
                                type = TransactionType.ORG_ACTIVATE
                        )
                    }
                    TransactionType.ORG_ADD_MEMBER.functionHash -> {
                        return saveTransaction(
                                hash = txHash,
                                from = walletService.getTxHash(signedTx.from.toLowerCase()),
                                to = walletService.getTxHash(signedTx.to.toLowerCase()),
                                input = signedTx.data,
                                type = TransactionType.ORG_ADD_MEMBER
                        )
                    }
                    TransactionType.ORG_ADD_PROJECT.functionHash -> {
                        val tx = saveTransaction(
                                hash = txHash,
                                from = walletService.getTxHash(signedTx.from),
                                to = txHash,
                                input = signedTx.data,
                                type = TransactionType.ORG_ADD_PROJECT
                        )
                        saveWallet(type = WalletType.PROJECT, transaction = tx)
                        return tx
                    }
                    TransactionType.PENDING_ORG_WITHDRAW.functionHash -> {
                        val (address, amount) = AbiUtils.decodeAddressAndAmount(inputData)
                        return saveTransaction(
                                hash = txHash,
                                from = walletService.getTxHash(signedTx.to),
                                to = address,
                                input = signedTx.data,
                                type = TransactionType.PENDING_ORG_WITHDRAW,
                                amount = amount
                        )
                    }
                    else -> {
                        throw Status.INVALID_ARGUMENT
                                .withDescription("Unsupported Organization contract function called!")
                                .asRuntimeException()
                    }
                }
            }
            ContractType.PROJECT -> {
                when (functionHash) {
                    TransactionType.PENDING_PROJ_WITHDRAW.functionHash -> {
                        val (address, amount) = AbiUtils.decodeAddressAndAmount(inputData)
                        return saveTransaction(
                                hash = txHash,
                                from = walletService.getTxHash(signedTx.to),
                                to = address,
                                input = signedTx.data,
                                type = TransactionType.PENDING_PROJ_WITHDRAW,
                                amount = amount
                        )
                    }
                    else -> {
                        throw Status.INVALID_ARGUMENT
                                .withDescription(
                                        ErrorCode.INVALID_FUNCTION_CALL.withMessage(
                                            "Invalid Project contract function call!"
                                        )
                                ).asRuntimeException()
                    }
                }
            }
        }
    }

    private fun getTargetContractType(txData: String): ContractType {
        val tx = TransactionDecoder.decode(txData) as SignedRawTransaction
        val toAddress = tx.to.toLowerCase()
        return when (toAddress) {
            properties.contracts.ampnetAddress.toLowerCase() -> ContractType.AMPNET
            properties.contracts.eurAddress.toLowerCase() -> ContractType.EUR
            else -> {
                val wallet = walletService.getByAddress(toAddress)
                when (wallet.type) {
                    WalletType.ORG -> ContractType.ORG
                    WalletType.PROJECT -> ContractType.PROJECT
                    else -> throw Status.INVALID_ARGUMENT
                            .withDescription("<To> address not one of AMPnet smart contracts!")
                            .asRuntimeException()
                }
            }
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
                .withDescription(ErrorCode.CALLER_NOT_REGISTERED.withMessage("Transaction processing rejected! Caller $fromAddress not platform user."))
                .asRuntimeException()
    }

    private fun throwIfTxTypeDoesNotMatchActualTx(txData: String, txType: TransactionType) {
        val tx = TransactionDecoder.decode(txData)
        val actualFunction = tx.data.substring(0, 8).toLowerCase()
        val providedFunction = txType.functionHash
        if (actualFunction != providedFunction) {
            throw Status.PERMISSION_DENIED
                    .withDescription(ErrorCode.INVALID_TX_TYPE.withMessage("Signed transaction does not match provided tx type."))
                    .asRuntimeException()
        }
    }

    private fun throwIfAddressAlreadyExists(address: String) {
        if (walletRepository.findByAddress(address).isPresent) {
            throw Status.ABORTED
                    .withDescription(ErrorCode.WALLET_ALREADY_EXISTS.withMessage("Wallet with address $address already exists!"))
                    .asRuntimeException()
        }
    }

    private fun throwIfTxAlreadyExists(hash: String) {
        if (transactionRepository.findByHash(hash.toLowerCase()).isPresent) {
            throw Status.ABORTED
                    .withDescription("Transaction with txHash: $hash already exists!")
                    .asRuntimeException()
        }
    }

    private fun saveWallet(address: String? = null, type: WalletType, transaction: Transaction) {
        val wallet = Wallet::class.java.newInstance()
        wallet.type = type
        wallet.transaction = transaction
        wallet.address = address
        walletRepository.save(wallet)
    }

    private fun saveTransaction(
        hash: String,
        from: String,
        to: String,
        input: String,
        type: TransactionType,
        amount: BigInteger? = null
    ): Transaction {
        val tx = Transaction::class.java.newInstance()
        tx.hash = hash.toLowerCase()
        tx.fromWallet = from.toLowerCase()
        tx.toWallet = to.toLowerCase()
        tx.input = input
        tx.type = type
        tx.state = TransactionState.PENDING
        tx.amount = amount
        tx.createdAt = ZonedDateTime.now()
        transactionRepository.save(tx)

        return tx
    }
}