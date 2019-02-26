package com.ampnet.crowdfunding.blockchain.controller

import com.ampnet.crowdfunding.blockchain.controller.pojo.TransactionListRequest
import com.ampnet.crowdfunding.blockchain.controller.pojo.TransactionListResponse
import com.ampnet.crowdfunding.blockchain.controller.pojo.TransactionRequest
import com.ampnet.crowdfunding.blockchain.enums.TransactionState
import com.ampnet.crowdfunding.blockchain.enums.TransactionType
import com.ampnet.crowdfunding.blockchain.persistence.model.Transaction
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.ZonedDateTime

class TransactionControllerTest : ControllerTestBase() {

    private val pathTransactions = "/transactions"

    private lateinit var testContext: TestContext

    @BeforeEach
    fun init() {
        databaseCleanerService.deleteAll()
        testContext = TestContext()
    }

    @Test
    fun mustBeAbleToGetTransactionResponse() {
        suppose("Some transactions are stored") {
            // TODO: create transactions
            val transactions = mutableListOf<Transaction>()
            transactions.add(createTransaction(
                    "0xed9ef9390d2eb6f6c55b2b646a8ef8a48b4ff572c11ef6e52b0af2b1174530a6",
                    TransactionState.MINED,
                    TransactionType.WALLET_CREATE)
            )
            transactions.add(createTransaction(
                    "0x1c60804d508ce37e3c96e427ea23a3b9dcfecd5e25c247f80d90d8c8c7310dad",
                    TransactionState.PENDING,
                    TransactionType.ORG_CREATE)
            )
            transactions.add(createTransaction(
                    "0xbd811296e7bf304b62671104c3007f656d3f78d5aea976412835005658a5897a",
                    TransactionState.FAILED,
                    TransactionType.INVEST)
            )
            createTransaction("0x85cc3842dfd5e56b6aa7cfd0eafcc4e506b15278eab3eb0b75e4c5b1f9e2714e",
                    TransactionState.MINED,
                    TransactionType.DEPOSIT
            )
            testContext.transactions = transactions
        }

        verify("Controller will return transactions") {
            val transactionHashList = testContext.transactions.map { TransactionRequest(it.hash) }.toMutableList()
            transactionHashList.add(TransactionRequest("non-existing-hash"))
            val request = TransactionListRequest(transactionHashList)

            val result = mockMvc.perform(
                    post(pathTransactions)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk)
                    .andReturn()

            val response: TransactionListResponse = objectMapper.readValue(result.response.contentAsString)
            assertThat(response.transactions).hasSize(3)
            assertThat(response.transactions.map { it.hash }).containsAll(testContext.transactions.map { it.hash })

            val transactionResponse = response.transactions
                    .find { it.hash == "0xbd811296e7bf304b62671104c3007f656d3f78d5aea976412835005658a5897a" }!!
            assertThat(transactionResponse.state).isEqualTo(TransactionState.FAILED)
            assertThat(transactionResponse.type).isEqualTo(TransactionType.INVEST)
        }
    }

    private fun createTransaction(hash: String, state: TransactionState, type: TransactionType): Transaction {
        val time = ZonedDateTime.now()
        val tx = Transaction::class.java.newInstance()
        tx.hash = hash.toLowerCase()
        tx.fromWallet = "from"
        tx.toWallet = "to"
        tx.input = "input"
        tx.type = type
        tx.state = state
        tx.createdAt = time.minusMinutes(11)
        tx.processedAt = time
        transactionRepository.save(tx)
        return tx
    }

    protected class TestContext {
        var transactions = emptyList<Transaction>()
    }
}
