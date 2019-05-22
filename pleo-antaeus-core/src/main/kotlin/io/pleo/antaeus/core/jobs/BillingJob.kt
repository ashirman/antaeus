package io.pleo.antaeus.core.jobs

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.models.InvoiceStatus
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.quartz.SimpleScheduleBuilder
import org.quartz.TriggerBuilder
import org.slf4j.LoggerFactory

internal class BillingJob : Job {
    companion object {
        const val PAYMENT_PROVIDER = "paymentProvider"
        const val INVOICE_SERVICE = "invoiceService"
        const val CUSTOMER_SERVICE = "customerService"
        const val INVOICE_ID = "invoiceId"
        const val RETRY = "retry"

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

        try {
            invoiceService.fetch(executionContext.jobDetail.jobDataMap.getIntValue(INVOICE_ID)).apply {
                // need to check the invoice status right before provider's charge() call.
                // the job could be planned long time ago and the invoice can be processed/cancelled already somehow
                // (eg alternate way of payments, via support resolution, etc).
                if (status == InvoiceStatus.PENDING) {
                    if (paymentProvider.charge(this)) {
                        invoiceService.update(copy(status = InvoiceStatus.PAID))
                    } else {
                        // customer has not enough money to be able to pay the invoice.
                        //TODO process it somehow
                    }
                } else {
                    logger.info("invoice ${id} processing has been skipped due to ${status} status")
                }
            }

        } catch (cnfe: CustomerNotFoundException) {
            logger.info("customer ${cnfe.id} not found", cnfe)
            handleCustomerNotFound(executionContext, customerService, cnfe)
        } catch (cme: CurrencyMismatchException) {
            // when the currency does not match the customer account.
            //convert currency and try to pay it as another invoice.
            //TODO add convertion service and align invoice currency with customer currency
        } catch (ne: NetworkException) {
            logger.info("failed to charge invoice due to network error ")
            handleNetworkError(executionContext, ne)
        } catch (infe: InvoiceNotFoundException) {
            logger.info("invoice ${infe.id} can not be found. associated job ${executionContext.jobDetail.key} will be deleted")
            deleteCurrentJobFromScheduling(executionContext)
        }
    }

    private fun handleCustomerNotFound(executionContext: JobExecutionContext, customerService: CustomerService, cnfe: CustomerNotFoundException) {
        deleteCurrentJobFromScheduling(executionContext)
        customerService.deactivateCustomer(cnfe.id)
    }

    /**
     * either re-schedule existing job and give one more attempts to be executed until number of attempts exceeds configured <code>RETRY</code> value
     * or throw JobExecutionException otherwise
     * */
    private fun handleNetworkError(executionContext: JobExecutionContext, ne: NetworkException) {
        val jobDataMap = executionContext.jobDetail.jobDataMap
        var retry = jobDataMap.getIntValue(RETRY)
        if (retry > 0) {
            jobDataMap[RETRY] = --retry
            executionContext.scheduler.scheduleJob(buildRetryTrigger(executionContext))
        } else {
            throw JobExecutionException("Retries exceeded", ne).apply { setUnscheduleAllTriggers(true) }
        }
    }

    private fun buildRetryTrigger(context: JobExecutionContext) = TriggerBuilder
            .newTrigger()
            .withIdentity(context.jobDetail.key.name, context.jobDetail.key.group)
            .withSchedule(repeatSchedule())
            .build()

    //TODO avoid hardcode and replace by conf value
    private fun repeatSchedule(): SimpleScheduleBuilder = SimpleScheduleBuilder
            .simpleSchedule()
            .withIntervalInMinutes(5)
            .withRepeatCount(0)

    private fun deleteCurrentJobFromScheduling(context: JobExecutionContext) {
        context.scheduler.deleteJob(context.jobDetail.key)
    }
}