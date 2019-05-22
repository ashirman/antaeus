package io.pleo.antaeus.core

import io.pleo.antaeus.core.jobs.BillingJob
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.TriggerBuilder
import java.math.BigDecimal

class TestUtils {
    companion object {
        val givenInvoice = Invoice(1,
                2,
                Money(BigDecimal.valueOf(123L), currency = Currency.USD),
                InvoiceStatus.PENDING)

        val givenTrigger = TriggerBuilder
                .newTrigger()
                .withIdentity("trigger_invoice_1")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 12 1 1/1 ? *"))
                .build()

        val givenJobDetail = JobBuilder
                .newJob()
                .ofType(BillingJob::class.java)
                .withIdentity("job_invoice_1")
                .usingJobData(BillingJob.INVOICE_ID, 1)
                .build()
    }
}