package com.ampnet.crowdfunding.blockchain.service.impl

import com.ampnet.crowdfunding.blockchain.persistence.model.Wallet
import com.ampnet.crowdfunding.blockchain.persistence.repository.WalletRepository
import com.ampnet.crowdfunding.blockchain.service.WalletService
import io.grpc.Status
import org.springframework.stereotype.Service
import org.web3j.protocol.Web3j
import java.util.Optional
import javax.transaction.Transactional

@Service
class WalletServiceImpl(
    val walletRepository: WalletRepository,
    val web3j: Web3j
) : WalletService {

    override fun get(address: String): Optional<Wallet> {
        return walletRepository.findByAddress(address)
    }

    @Transactional
    override fun getPublicKey(txHash: String): String? {
        val wallet = walletRepository.findByHash(txHash).orElseThrow {
            throw Status.NOT_FOUND
                    .withDescription("Wallet creation tx: $txHash does not exist!")
                    .asRuntimeException()
        }
        return wallet.publicKey
    }

    override fun getTxHash(address: String): String {
        val wallet = walletRepository.findByAddress(address).orElseThrow {
            throw Status.NOT_FOUND
                    .withDescription("Wallet with address: $address does not exist!")
                    .asRuntimeException()
        }
        return wallet.hash
    }

    @Transactional
    override fun storePublicKey(publicKey: String, forTxHash: String) {
        val wallet = walletRepository.findByHash(forTxHash).orElseThrow {
            throw Status.NOT_FOUND
                    .withDescription("Wallet with txHash: $forTxHash does not exist")
                    .asRuntimeException()
        }
        wallet.publicKey = publicKey
        walletRepository.save(wallet)
    }
}