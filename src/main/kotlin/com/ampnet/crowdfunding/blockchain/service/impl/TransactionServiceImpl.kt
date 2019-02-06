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
import io.grpc.Status
import org.springframework.stereotype.Service
import org.web3j.abi.TypeDecoder
import org.web3j.abi.datatypes.Address
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
                throw Status.INTERNAL
                        .withDescription(
                                ErrorCode.WALLET_CREATION_PENDING
                                        .withMessage("Transaction with txHash: ${tx.hash} not yet mined!")
                        )
                        .asRuntimeException()
            }
            TransactionState.FAILED -> {
                throw Status.INTERNAL
                        .withDescription(
                                ErrorCode.WALLET_CREATION_FAILED
                                        .withMessage("Transaction with txHash: ${tx.hash} failed!")
                        )
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

    private fun persistTransaction(txData: String, txHash: String, type: ContractType): Transaction {
        val signedTx = TransactionDecoder.decode(txData) as SignedRawTransaction
        val input = signedTx.data
        val functionHash = input.substring(0, 8).toLowerCase()
        val inputData = input.substring(8)

        when (type) {
            ContractType.AMPNET -> {
                when (functionHash) {
                    TransactionType.WALLET_CREATE.functionHash -> {
                        saveWallet(txHash, WalletType.USER)
                        return saveTransaction(
                                hash = txHash,
                                from = signedTx.from,
                                to = txHash,
                                input = signedTx.data,
                                type = TransactionType.WALLET_CREATE
                        )
                    }
                    TransactionType.ORG_CREATE.functionHash -> {
                        saveWallet(txHash, WalletType.ORG)
                        return saveTransaction(
                                hash = txHash,
                                from = walletService.getTxHash(signedTx.from),
                                to = txHash,
                                input = signedTx.data,
                                type = TransactionType.ORG_CREATE
                        )
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
                        val (address, amount) = decodeAddressAndAmount(inputData)
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
                        val (address, amount) = decodeAddressAndAmount(inputData)
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
                        val (address, amount) = decodeAddressAndAmount(inputData)
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
                        val (address, amount) = decodeAddressAndAmount(inputData)
                        return saveTransaction(
                                hash = txHash,
                                from = walletService.getTxHash(signedTx.from),
                                to = walletService.getTxHash(address),
                                input = signedTx.data,
                                type = TransactionType.TRANSFER,
                                amount = amount
                        )
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
                        saveWallet(txHash, WalletType.PROJECT)
                        return saveTransaction(
                                hash = txHash,
                                from = walletService.getTxHash(signedTx.from),
                                to = txHash,
                                input = signedTx.data,
                                type = TransactionType.ORG_ADD_PROJECT
                        )
                    }
                    TransactionType.PENDING_ORG_WITHDRAW.functionHash -> {
                        val (address, amount) = decodeAddressAndAmount(inputData)
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
                TODO("not implemented")
            }
        }
    }

    private fun getTargetContractType(txData: String): ContractType {
        val tx = TransactionDecoder.decode(txData) as SignedRawTransaction
        val toAddress = tx.to.toLowerCase()
        if (toAddress == properties.contracts.ampnetAddress.toLowerCase()) {
            return ContractType.AMPNET
        } else if (toAddress == properties.contracts.eurAddress.toLowerCase()) {
            return ContractType.EUR
        } else {
            val wallet = walletService.get(toAddress)
            if (!wallet.isPresent) {
                throw Status.INVALID_ARGUMENT
                        .withDescription("<To> address not one of AMPnet smart contracts!")
                        .asRuntimeException()
            }
            val walletType = wallet.get().type
            if (walletType == WalletType.ORG) {
                return ContractType.ORG
            } else if (walletType == WalletType.PROJECT) {
                return ContractType.PROJECT
            } else {
                throw Status.INVALID_ARGUMENT
                        .withDescription("<To> address not one of AMPnet smart contracts!")
                        .asRuntimeException()
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

    private fun saveWallet(hash: String, type: WalletType) {
        if (walletRepository.findByHash(hash.toLowerCase()).isPresent) {
            throw Status.ABORTED
                    .withDescription("Wallet with txHash: $hash already exists!")
                    .asRuntimeException()
        }

        val wallet = Wallet::class.java.newInstance()
        wallet.hash = hash.toLowerCase()
        wallet.type = type
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
        if (transactionRepository.findByHash(hash.toLowerCase()).isPresent) {
            throw Status.ABORTED
                    .withDescription("Transaction with txHash: $hash already exists!")
                    .asRuntimeException()
        }

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

    private fun decodeAddress(input: String): String {
        val refMethod = TypeDecoder::class.java.getDeclaredMethod(
                "decode",
                String::class.java,
                Class::class.java
        )
        refMethod.isAccessible = true
        val address = refMethod.invoke(null, input, Address::class.java) as Address
        return address.value.toLowerCase()
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

        return Pair(address.value.toLowerCase(), amount.value)
    }
}