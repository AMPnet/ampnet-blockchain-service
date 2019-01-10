package com.ampnet.crowdfunding.blockchain.config

import org.springframework.stereotype.Service
import javax.persistence.EntityManager
import javax.transaction.Transactional

@Service
class DatabaseCleanerService(val em: EntityManager) {

    @Transactional
    fun deleteAll() {
        em.createNativeQuery("TRUNCATE transaction").executeUpdate()
        em.createNativeQuery("TRUNCATE wallet").executeUpdate()
    }

}