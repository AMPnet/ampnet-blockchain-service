package com.ampnet.crowdfunding.blockchain.service.impl

import com.ampnet.crowdfunding.blockchain.persistence.model.Wallet
import com.ampnet.crowdfunding.blockchain.persistence.repository.WalletRepository
import com.ampnet.crowdfunding.blockchain.service.AddressService
import com.ampnet.crowdfunding.blockchain.service.TransactionService
import io.grpc.Status
import org.springframework.stereotype.Service

@Service
class AddressServiceImpl(
    private val walletRepository: WalletRepository,
    private val transactionService: TransactionService
) : AddressService {

    override fun getAddress(txHash: String): String {
        val wallet = walletRepository.findByHash(txHash).orElseThrow {
            throw Status.NOT_FOUND
                    .withDescription("Wallet creation tx: $txHash does not exist!")
                    .asRuntimeException()
        }
        return wallet.address?.let { it } ?: run {
            val address = transactionService.getAddressFromHash(txHash)
            cacheAddressInWallet(wallet, address)
            return address
        }
    }

    private fun cacheAddressInWallet(wallet: Wallet, address: String) {
        wallet.address = address
        walletRepository.save(wallet)
    }
}