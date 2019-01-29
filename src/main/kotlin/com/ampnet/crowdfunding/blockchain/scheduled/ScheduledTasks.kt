package com.ampnet.crowdfunding.blockchain.scheduled

import com.ampnet.crowdfunding.blockchain.service.TransactionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ScheduledTasks {

    @Autowired
    private lateinit var transactionService: TransactionService

    @Scheduled(fixedRate = 3000)
    fun updateTransactionsState() {
        transactionService.updateTransactionStates()
    }
}