package com.ampnet.crowdfunding.blockchain.service.impl

import com.ampnet.crowdfunding.blockchain.enums.ErrorCode
import com.ampnet.crowdfunding.blockchain.enums.TransactionState
import com.ampnet.crowdfunding.blockchain.enums.TransactionType
import com.ampnet.crowdfunding.blockchain.persistence.model.Transaction
import com.ampnet.crowdfunding.blockchain.persistence.model.Wallet
import com.ampnet.crowdfunding.blockchain.persistence.repository.WalletRepository
import com.ampnet.crowdfunding.blockchain.service.WalletService
import com.ampnet.crowdfunding.blockchain.util.AbiUtils
import io.grpc.Status
import org.springframework.stereotype.Service
import org.web3j.protocol.Web3j
import javax.transaction.Transactional

@Service
class WalletServiceImpl(
    val walletRepository: WalletRepository,
    val web3j: Web3j
) : WalletService {

    @Transactional
    override fun getByAddress(address: String): Wallet {
        return walletRepository.findByAddress(address.toLowerCase()).orElseThrow {
            Status.NOT_FOUND
                    .withDescription(ErrorCode.WALLET_DOES_NOT_EXIST.withMessage("Wallet $address is not registered!"))
                    .asRuntimeException()
        }
    }

    @Transactional
    override fun getByTxHash(txHash: String): Wallet {
        val wallet = walletRepository.findByTransaction_Hash(txHash).orElseThrow {
            throw Status.NOT_FOUND
                    .withDescription(
                            ErrorCode.WALLET_DOES_NOT_EXIST
                                    .withMessage("Wallet creation tx with hash $txHash does not exist")
                    )
                    .asRuntimeException()
        }
        return wallet.address?.let {
            when (wallet.transaction.state) {
                TransactionState.MINED -> { wallet }
                TransactionState.PENDING -> {
                    throw Status.FAILED_PRECONDITION
                            .withDescription(
                                    ErrorCode.WALLET_CREATION_PENDING.withMessage("Wallet creation tx still pending.")
                            )
                            .asRuntimeException()
                }
                TransactionState.FAILED -> {
                    throw Status.FAILED_PRECONDITION
                            .withDescription(
                                    ErrorCode.WALLET_CREATION_FAILED.withMessage("Wallet creation tx failed.")
                            )
                            .asRuntimeException()
                }
            }
        } ?: run {
            val address = parseAddressFromTransaction(wallet.transaction)
            cacheAddressInWallet(address, wallet)
            return wallet
        }
    }

    @Transactional
    override fun getTxHash(address: String): String {
        val wallet = walletRepository.findByAddress(address.toLowerCase()).orElseThrow {
            throw Status.NOT_FOUND
                    .withDescription("Wallet with address: $address does not exist!")
                    .asRuntimeException()
        }
        return wallet.transaction.hash
    }

    @Transactional
    override fun storePublicKey(publicKey: String, forTxHash: String) {
        val wallet = walletRepository.findByTransaction_Hash(forTxHash.toLowerCase()).orElseThrow {
            throw Status.NOT_FOUND
                    .withDescription("Wallet with txHash: $forTxHash does not exist")
                    .asRuntimeException()
        }
        wallet.publicKey = publicKey
        walletRepository.save(wallet)
    }

    private fun parseAddressFromTransaction(tx: Transaction): String {
        when (tx.state) {
            TransactionState.MINED -> {
                val receipt = web3j.ethGetTransactionReceipt(tx.hash).send().transactionReceipt.get()
                when (tx.type) {
                    TransactionType.WALLET_CREATE, TransactionType.ORG_CREATE, TransactionType.ORG_ADD_PROJECT -> {
                        return AbiUtils.decodeAddress(receipt.logs.first().topics[1])
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

    private fun cacheAddressInWallet(address: String, wallet: Wallet) {
        wallet.address = address
        walletRepository.save(wallet)
    }
}