package io.pleo.antaeus.data

import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.Connection

internal class AntaeusDalTest {

    companion object {
        lateinit var db: Database
        @BeforeAll
        @JvmStatic
        fun setup() {
            val tables = arrayOf(InvoiceTable, CustomerTable)

            db = Database
                    .connect("jdbc:sqlite:/tmp/data.db", "org.sqlite.JDBC")
                    .also {
                        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
                        transaction(it) {
                            addLogger(StdOutSqlLogger)
                            // Drop all existing tables to ensure a clean slate on each run
                            SchemaUtils.drop(*tables)
                            // Create all tables
                            SchemaUtils.create(*tables)
                        }
                    }
        }
    }

    @Test
    fun createCustomerTest() {
        doInTestTransaction {
            val customer = it.createCustomer(Currency.USD)

            assertEquals(customer, it.fetchCustomer(customer!!.id))
        }
    }

    @Test
    fun createInvoiceTest() {
        doInTestTransaction {
            val customer = it.createCustomer(Currency.USD)
            val invoice = it.createInvoice(Money(BigDecimal.valueOf(123L), Currency.USD), customer!!, InvoiceStatus.PENDING)

            assertEquals(invoice, it.fetchInvoice(invoice!!.id))
        }
    }

    @Test
    fun fetchInvoicesTest() {
        doInTestTransaction {
            val customer = it.createCustomer(Currency.USD)
            val invoice = it.createInvoice(Money(BigDecimal.valueOf(123L), Currency.USD), customer!!, InvoiceStatus.PENDING)

            val invoices = it.fetchInvoices()
            assertEquals(1, invoices.size)
            assertEquals(invoice, invoices[0])
        }
    }

    @Test
    fun updateInvoiceTest() {
        doInTestTransaction {
            val customer = it.createCustomer(Currency.USD)
            val invoice = it.createInvoice(Money(BigDecimal.valueOf(123L), Currency.USD), customer!!, InvoiceStatus.PENDING)

            val modified = invoice!!.copy(status = InvoiceStatus.PAID)

            assertEquals(modified, it.updateInvoice(modified))
        }
    }


    //helper method which performs transaction rollback and minimizes boilerplate on client side
    private fun doInTestTransaction(body: (AntaeusDal) -> Unit) {
        transaction(db) {

            body(AntaeusDal(db))

            TransactionManager.current().rollback()
        }
    }
}
