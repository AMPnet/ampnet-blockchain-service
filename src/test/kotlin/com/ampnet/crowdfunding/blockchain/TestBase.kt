package com.ampnet.crowdfunding.blockchain

import com.ampnet.crowdfunding.BlockchainServiceGrpc
import com.ampnet.crowdfunding.blockchain.service.BlockchainService
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.web3j.crypto.Credentials
import java.util.concurrent.TimeUnit

@SpringBootTest
abstract class TestBase {

    class Accounts {
        val ampnetOwner = Credentials.create("0xa88400ec75febb4244f4a04d8290ae2fbdbedb874553eb86b91f10c9de4f5fa8")
        val eurOwner = Credentials.create("0xec3cd0b40d2952cc77cac778461e89dd958684b43320ff0ba1cf3ee435badf32")
        val alice = Credentials.create("0x16675095b2ebbe3402d71c018158a8cef7b8cdad650e716de17c487190133932")
        val bob = Credentials.create("0xb93de5fb1b8a74a2a1858b1e336185331a3e40a266ca3afb9b689f12ff0e8e8b")
    }

    val accounts = Accounts()

    lateinit var server: Server
    lateinit var channel: ManagedChannel
    lateinit var grpc: BlockchainServiceGrpc.BlockchainServiceBlockingStub

    @Autowired
    private lateinit var blockchainService: BlockchainService

    @BeforeEach
    fun setUp() {
        val serverName = InProcessServerBuilder.generateName()
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(blockchainService)
                .build()
                .start()
        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build()
        grpc = BlockchainServiceGrpc.newBlockingStub(channel)
    }

    @AfterEach
    fun tearDown() {

        channel.shutdown()
        server.shutdown()


        try {
            Assertions.assertThat(
                    channel.awaitTermination(5, TimeUnit.SECONDS)
            ).isTrue()

            server.awaitTermination(5, TimeUnit.SECONDS)
        } finally {
            channel.shutdownNow()
            server.shutdownNow()
        }
    }

    protected fun suppose(@Suppress("UNUSED_PARAMETER") description: String, function: () -> Unit) {
        function.invoke()
    }

    protected fun verify(@Suppress("UNUSED_PARAMETER") description: String, function: () -> Unit) {
        function.invoke()
    }

}
