package com.ampnet.crowdfunding.blockchain.exception

class TransactionParseException(exceptionMessage: String) : Exception(exceptionMessage)
class TransactionDoesNotExistException(exceptionMessage: String) : Exception(exceptionMessage)
class TransactionPendingException(exceptionMessage: String): Exception(exceptionMessage)
class TransactionFailedException(exceptionMessage: String): Exception(exceptionMessage)
class TransactionNotWalletCreateException(exceptionMessage: String): Exception(exceptionMessage)