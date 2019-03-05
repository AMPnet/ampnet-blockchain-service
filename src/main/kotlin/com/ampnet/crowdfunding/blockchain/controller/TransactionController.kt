package com.ampnet.crowdfunding.blockchain.controller

import com.ampnet.crowdfunding.blockchain.controller.pojo.TransactionStatusRequest
import com.ampnet.crowdfunding.blockchain.controller.pojo.TransactionListResponse
import com.ampnet.crowdfunding.blockchain.controller.pojo.TransactionResponse
import com.ampnet.crowdfunding.blockchain.persistence.model.Transaction
import com.ampnet.crowdfunding.blockchain.service.TransactionService
import io.grpc.StatusRuntimeException
import mu.KLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class TransactionController(private val transactionService: TransactionService) {

    companion object : KLogging()

    @PostMapping("/transaction/status")
    fun checkTransactionsStatus(@RequestBody request: TransactionStatusRequest): ResponseEntity<TransactionListResponse> {
        logger.debug { "Received request to check transaction status for list: $request" }
        val transactions = request.txHashes.map { getTransaction(it) }
        val response = transactions
                .filterNotNull()
                .map { transaction -> TransactionResponse(transaction) }

        return ResponseEntity.ok(TransactionListResponse(response))
    }

    private fun getTransaction(hash: String): Transaction? {
        return try {
            transactionService.getTransaction(hash)
        } catch (ex: StatusRuntimeException) {
            logger.info { "Could not get transaction with hash: $hash" }
            null
        }
    }
}
