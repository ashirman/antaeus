package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.jobs.BillingJob
import io.pleo.antaeus.models.Invoice
import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.Scheduler
import org.quartz.TriggerBuilder
import org.quartz.impl.StdSchedulerFactory

class BillingService(paymentProvider: PaymentProvider,
                     invoiceService: InvoiceService,
                     customerService: CustomerService,
                     currencyConversionService: CurrencyConversionService,
                     val scheduler: Scheduler = StdSchedulerFactory().scheduler,
                     numOfRetries: Int = 5) {

    init {
        //using scheduler context is the simplest way to pass instance variables (service instances) to further job instances without using DI frameworks
        //and/or any kind of global static objects. @see https://stackoverflow.com/questions/12777057/how-to-pass-instance-variables-into-quartz-job
        //keys/values need to be consistent with what is retrieved in BillingJob class
        scheduler.context[BillingJob.PAYMENT_PROVIDER] = paymentProvider
        scheduler.context[BillingJob.INVOICE_SERVICE] = invoiceService
        scheduler.context[BillingJob.CUSTOMER_SERVICE] = customerService
        scheduler.context[BillingJob.CURRENCY_CONVERSION_SERVICE] = currencyConversionService
        scheduler.context[BillingJob.RETRY] = numOfRetries

        if (!scheduler.isStarted) scheduler.start()
    }

    fun scheduleInvoicePayment(invoice: Invoice, cronSchedule: String = "0 0 12 1 1/1 ? *") {
        scheduler.scheduleJob(buildJobDetail(invoice), buildTrigger(invoice, cronSchedule))
    }

    private fun buildTrigger(invoice: Invoice, cronSchedule: String) = TriggerBuilder
            .newTrigger()
            .withIdentity("trigger_invoice_" + invoice.id)
            .withSchedule(CronScheduleBuilder.cronSchedule(cronSchedule))
            .build()

    private fun buildJobDetail(invoice: Invoice) = JobBuilder
            .newJob()
            .ofType(BillingJob::class.java)
            .withIdentity("job_invoice_" + invoice.id)
            .usingJobData(BillingJob.INVOICE_ID, invoice.id)
            .build()
}