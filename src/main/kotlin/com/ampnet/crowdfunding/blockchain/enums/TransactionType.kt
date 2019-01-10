package com.ampnet.crowdfunding.blockchain.enums

enum class TransactionType {
    WALLET_CREATE,
    ORG_CREATE,
    DEPOSIT,
    PENDING_WITHDRAW,
    WITHDRAW,
    INVEST,
    TRANSFER,
    ORG_ADD_MEMBER,
    ORG_ADD_PROJECT,
    ORG_ACTIVATE,
    TRANSFER_OWNERSHIP,
    CANCEL_INVESTMENT
}