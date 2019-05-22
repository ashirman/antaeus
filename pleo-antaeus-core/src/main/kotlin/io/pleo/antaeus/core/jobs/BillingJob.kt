package io.pleo.antaeus.core.jobs

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory

internal class BillingJob : Job {
    companion object {
        const val PAYMENT_PROVIDER = "paymentProvider"
        const val INVOICE_SERVICE = "invoiceService"
        const val CUSTOMER_SERVICE = "customerService"
        const val INVOICE_ID = "invoiceId"

        // see https://www.baeldung.com/kotlin-logging
        @Suppress("JAVA_CLASS_ON_COMPANION")
        @JvmStatic
        private val logger = LoggerFactory.getLogger(javaClass.enclosingClass)
    }

    override fun execute(executionContext: JobExecutionContext) {
        val context = executionContext.scheduler.context
        val paymentProvider = context[PAYMENT_PROVIDER] as PaymentProvider
        val invoiceService = context[INVOICE_SERVICE] as InvoiceService
        val customerService = context[CUSTOMER_SERVICE] as CustomerService

        val invoiceId = executionContext.jobDetail.jobDataMap.getIntValue(INVOICE_ID)

        var invoice: Invoice? = null
        try {
            invoice = invoiceService.fetch(invoiceId)

            // need to check the invoice status right before provider's charge() call.
            // the job could be planned long time ago and the invoice can be processed/cancelled already somehow
            // (eg alternate way of payments, via support resolution, etc).
            if (invoice.status == InvoiceStatus.PENDING) {
                if (paymentProvider.charge(invoice)) {
                    invoiceService.update(invoice.copy(status = InvoiceStatus.PAID))
                } else {
                    // customer has not enough money to be able to pay the invoice.
                    //TODO process it somehow
                }
            } else {
                logger.info("invoice ${invoice.id} processing has been skipped due to ${invoice.status} status")
            }
        } catch (cnfe: CustomerNotFoundException) {
            // when no customer has the given id.
            deleteCurrentJobFromScheduling(executionContext)

            invoice?.let {
                logger.info("customer ${it.customerId} is not available on")
                customerService.deactivateCustomer(it.customerId)
            }
        } catch (cme: CurrencyMismatchException) {
            // when the currency does not match the customer account.
            //convert currency and try to pay it as another invoice.
            //TODO add convertion service and align invoice currency with customer currency
        } catch (ne: NetworkException) {
            // when a network error happens.
            // retry payment after some time, 3rd part payment provider may not be available.
            //TODO add numOfRepeats support. retry payments after a while
        } catch (infe: InvoiceNotFoundException) {
            logger.info("invoice ${invoice?.id} can not be found. associated job ${executionContext.jobDetail.key} will be deleted")
            deleteCurrentJobFromScheduling(executionContext)
        }
    }

    private fun deleteCurrentJobFromScheduling(context: JobExecutionContext) {
        context.scheduler.deleteJob(context.jobDetail.key)
    }
}