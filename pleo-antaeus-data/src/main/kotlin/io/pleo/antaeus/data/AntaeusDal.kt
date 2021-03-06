/*
    Implements the data access layer (DAL).
    This file implements the database queries used to fetch and insert rows in our database tables.

    See the `mappings` module for the conversions between database rows and Kotlin objects.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.CustomerStatus
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class AntaeusDal(private val db: Database) {
    fun fetchInvoice(id: Int): Invoice? {
        // transaction(db) runs the internal query as a new database transaction.
        return transaction(db) {
            // Returns the first invoice with matching id.
            InvoiceTable
                    .select { InvoiceTable.id.eq(id) }
                    .firstOrNull()
                    ?.toInvoice()
        }
    }

    fun fetchInvoices(): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                    .selectAll()
                    .map { it.toInvoice() }
        }
    }

    fun createInvoice(amount: Money, customer: Customer, status: InvoiceStatus = InvoiceStatus.PENDING): Invoice? {
        val id = transaction(db) {
            // Insert the invoice and returns its new id.
            InvoiceTable
                    .insert {
                        it[this.value] = amount.value
                        it[this.currency] = amount.currency.toString()
                        it[this.status] = status.toString()
                        it[this.customerId] = customer.id
                    } get InvoiceTable.id
        }

        return fetchInvoice(id!!)
    }

    fun updateInvoice(updatedInvoice: Invoice): Invoice? {
        transaction(db) {
            InvoiceTable
                    .update {
                        it[this.id] = updatedInvoice.id
                        it[this.currency] = updatedInvoice.amount.currency.toString()
                        it[this.customerId] = updatedInvoice.customerId
                        it[this.value] = updatedInvoice.amount.value
                        it[this.status] = updatedInvoice.status.toString()
                    }
        }

        return fetchInvoice(updatedInvoice.id)
    }

    fun updateCustomer(updatedCustomer: Customer): Customer? {
        transaction(db) {
            CustomerTable
                    .update {
                        it[this.id] = updatedCustomer.id
                        it[this.currency] = updatedCustomer.currency.toString()
                        it[this.status] = updatedCustomer.status.toString()
                    }
        }

        return fetchCustomer(updatedCustomer.id)
    }

    fun fetchCustomer(id: Int): Customer? {
        return transaction(db) {
            CustomerTable
                    .select { CustomerTable.id.eq(id) }
                    .firstOrNull()
                    ?.toCustomer()
        }
    }

    fun fetchCustomers(): List<Customer> {
        return transaction(db) {
            CustomerTable
                    .selectAll()
                    .map { it.toCustomer() }
        }
    }

    fun createCustomer(currency: Currency, customerStatus: CustomerStatus = CustomerStatus.ACTIVE): Customer? {
        val id = transaction(db) {
            // Insert the customer and return its new id.
            CustomerTable.insert {
                it[this.currency] = currency.toString()
                it[this.status] = customerStatus.toString()
            } get CustomerTable.id
        }

        return fetchCustomer(id!!)
    }
}
