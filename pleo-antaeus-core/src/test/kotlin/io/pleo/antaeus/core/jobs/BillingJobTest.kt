package io.pleo.antaeus.core.jobs

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import io.pleo.antaeus.core.TestUtils.Companion.givenCustomer
import io.pleo.antaeus.core.TestUtils.Companion.givenInvoice
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.services.CurrencyConversionService
import io.pleo.antaeus.core.services.CustomerService
import io.pleo.antaeus.core.services.InvoiceService
import io.pleo.antaeus.models.CustomerStatus
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.quartz.JobDetail
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
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

        jobdetails = JobDetailImpl("name", "group", BillingJob::class.java).apply {
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

    @Test
    fun `should convert currency on CurrencyMismatchException`() {
        val cancelledInvoice = givenInvoice.copy(status = InvoiceStatus.CANCELLED)
        val createdInvoice = Invoice(2, givenCustomer.id, givenInvoice.amount, InvoiceStatus.PENDING)
        val fromCurrenvy = givenInvoice.amount.currency
        val toCurrency = givenCustomer.currency
        val amount = givenInvoice.amount.value

        every { paymentProvider.charge(givenInvoice) } throws CurrencyMismatchException(givenInvoice.id, givenCustomer.id)
        every { customerService.fetch(givenCustomer.id) } returns givenCustomer
        every { invoiceService.update(cancelledInvoice) } returns cancelledInvoice
        every { invoiceService.create(givenInvoice.amount, givenCustomer) } returns createdInvoice
        every { currencyConversionService.convert(fromCurrenvy, toCurrency, amount) } returns amount

        assertThrows<JobExecutionException> { BillingJob().execute(executionContext) }
        assertTrue(jobdetails.jobDataMap[BillingJob.INVOICE_ID] == createdInvoice.id)//must have value associated with re-created invoice in job context map

        verify { paymentProvider.charge(givenInvoice) }
        verify { invoiceService.create(givenInvoice.amount, givenCustomer) }
        verify { currencyConversionService.convert(fromCurrenvy, toCurrency, amount) }
    }

    @Test
    fun `should resubmit job on network error`() {
        every { paymentProvider.charge(givenInvoice) } throws NetworkException()
        every { customerService.deactivateCustomer(givenInvoice.customerId) } returns givenCustomer.copy(status = CustomerStatus.INACTIVE)

        BillingJob().execute(executionContext)

        verify { paymentProvider.charge(givenInvoice) }
        verify { scheduler.scheduleJob(any()) }// don't check the details of generated trigger in unit tests for now
        assertTrue(jobdetails.jobDataMap[BillingJob.RETRY] == 0)//the value has been decresed by 1
    }

    @Test
    fun `should raise exception aftfer given attempts to handle NetworkException`() {
        jobdetails.jobDataMap[BillingJob.RETRY] = 0 // as if we already spent all attempts to retry
        every { paymentProvider.charge(givenInvoice) } throws NetworkException()
        every { customerService.deactivateCustomer(givenInvoice.customerId) } returns givenCustomer.copy(status = CustomerStatus.INACTIVE)

        assertThrows<JobExecutionException> { BillingJob().execute(executionContext) }

        verify { paymentProvider.charge(givenInvoice) }
    }
}