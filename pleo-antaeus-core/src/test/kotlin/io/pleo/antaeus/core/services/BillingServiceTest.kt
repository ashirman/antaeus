package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.pleo.antaeus.core.TestUtils.Companion.givenInvoice
import io.pleo.antaeus.core.TestUtils.Companion.givenJobDetail
import io.pleo.antaeus.core.TestUtils.Companion.givenTrigger
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.jobs.BillingJob
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.Scheduler
import org.quartz.SchedulerContext
import org.quartz.TriggerBuilder
import java.math.BigDecimal

@ExtendWith(MockKExtension::class)
internal class BillingServiceTest {
    @MockK
    lateinit var paymentProvider: PaymentProvider

    @MockK
    lateinit var invoiceService: InvoiceService

    @MockK
    lateinit var customerService: CustomerService

    @MockK
    lateinit var currencyConversionService: CurrencyConversionService

    @MockK(relaxed = true)
    lateinit var scheduler: Scheduler

    @Test
    fun `should start given scheduler if not started`() {
        every { scheduler.isStarted } returns false
        every { scheduler.context } returns SchedulerContext()

        BillingService(paymentProvider, invoiceService, customerService, currencyConversionService, scheduler)
        verify(exactly = 1) { scheduler.start() }
    }

    @Test
    fun `should not start given scheduler if already started`() {
        every { scheduler.isStarted } returns true
        every { scheduler.context } returns SchedulerContext()

        BillingService(paymentProvider, invoiceService, customerService, currencyConversionService, scheduler)
        verify(exactly = 0) { scheduler.start() }
    }

    @Test
    fun `should fill scheduler context`() {
        val schedulerContext = SchedulerContext()
        every { scheduler.isStarted } returns false
        every { scheduler.context } returns schedulerContext

        BillingService(paymentProvider, invoiceService, customerService, currencyConversionService, scheduler)

        assertTrue(scheduler.context[BillingJob.PAYMENT_PROVIDER] == paymentProvider)
        assertTrue(scheduler.context[BillingJob.INVOICE_SERVICE] == invoiceService)
        assertTrue(scheduler.context[BillingJob.CUSTOMER_SERVICE] == customerService)
        assertTrue(scheduler.context[BillingJob.CURRENCY_CONVERSION_SERVICE] == currencyConversionService)
    }

    @Test
    fun `should schedule job on given scheduler`() {
        every { scheduler.isStarted } returns false
        every { scheduler.context } returns SchedulerContext()

        BillingService(paymentProvider, invoiceService, customerService, currencyConversionService, scheduler)
                .scheduleInvoicePayment(givenInvoice, "0 0 12 1 1/1 ? *")

        verify(exactly = 1) { scheduler.scheduleJob(givenJobDetail, givenTrigger) }
    }

    @Test
    fun `should throw exception for invalid cron schedule`() {
        every { scheduler.isStarted } returns false
        every { scheduler.context } returns SchedulerContext()

        assertThrows<RuntimeException> {
            BillingService(paymentProvider, invoiceService, customerService, currencyConversionService, scheduler)
                    .scheduleInvoicePayment(givenInvoice, "bla bla bla")
        }
    }
}