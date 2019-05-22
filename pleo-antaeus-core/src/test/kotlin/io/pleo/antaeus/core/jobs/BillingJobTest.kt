package io.pleo.antaeus.core.jobs

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.pleo.antaeus.core.TestUtils.Companion.givenCustomer
import io.pleo.antaeus.core.TestUtils.Companion.givenInvoice
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.services.CurrencyConversionService
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.models.CustomerStatus
import io.pleo.antaeus.models.InvoiceStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.quartz.JobDetail
import org.quartz.JobExecutionContext
import org.quartz.Scheduler
import org.quartz.SchedulerContext
import org.quartz.impl.JobDetailImpl

@ExtendWith(MockKExtension::class)
internal class BillingJobTest {

    @MockK
    lateinit var paymentProvider: PaymentProvider

    @MockK
    lateinit var invoiceService: InvoiceService

    @MockK
    lateinit var customerService: CustomerService

    @MockK
    lateinit var currencyConversionService: CurrencyConversionService

    @MockK
    lateinit var executionContext: JobExecutionContext

    @MockK(relaxed = true)
    lateinit var scheduler: Scheduler

    lateinit var schedulerContext: SchedulerContext

    lateinit var jobdetails: JobDetail

    @BeforeEach
    internal fun setUp() {
        schedulerContext = SchedulerContext().apply {
            this[BillingJob.PAYMENT_PROVIDER] = paymentProvider
            this[BillingJob.INVOICE_SERVICE] = invoiceService
            this[BillingJob.CUSTOMER_SERVICE] = customerService
            this[BillingJob.CURRENCY_CONVERSION_SERVICE] = currencyConversionService
            this[BillingJob.RETRY] = 1
        }

        jobdetails = JobDetailImpl().apply {
            jobDataMap[BillingJob.RETRY] = 1
            jobDataMap[BillingJob.INVOICE_ID] = 1
        }

        every { executionContext.scheduler } returns scheduler
        every { executionContext.jobDetail } returns jobdetails
        every { scheduler.context } returns schedulerContext

        every { invoiceService.fetch(1) } returns givenInvoice
    }

    @Test
    fun `happy path test`() {
        val paidInvoice = givenInvoice.copy(status = InvoiceStatus.PAID)

        every { paymentProvider.charge(givenInvoice) } returns true
        every { invoiceService.update(paidInvoice) } returns paidInvoice

        BillingJob().execute(executionContext)

        verify { paymentProvider.charge(givenInvoice) }
        verify { invoiceService.update(paidInvoice) }
    }

    @Test
    fun `should deactivate customer if balance is not enough`() {
        every { paymentProvider.charge(givenInvoice) } returns false
        every { customerService.deactivateCustomer(givenInvoice.customerId) } returns givenCustomer.copy(status = CustomerStatus.INACTIVE)

        BillingJob().execute(executionContext)

        verify { paymentProvider.charge(givenInvoice) }
        verify { customerService.deactivateCustomer(givenInvoice.customerId) }
    }

    @Test
    fun `should deactivate customer on customer not found exception`() {
        every { paymentProvider.charge(givenInvoice) } throws CustomerNotFoundException(givenCustomer.id)
        every { customerService.deactivateCustomer(givenInvoice.customerId) } returns givenCustomer.copy(status = CustomerStatus.INACTIVE)

        BillingJob().execute(executionContext)

        verify { paymentProvider.charge(givenInvoice) }
        verify { customerService.deactivateCustomer(givenInvoice.customerId) }
        verify { scheduler.deleteJob(jobdetails.key) }
    }
}