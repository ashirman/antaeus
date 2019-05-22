package io.pleo.antaeus.core.exceptions

class CurrencyMismatchException(val invoiceId: Int, val customerId: Int) :
    Exception("Currency of invoice '$invoiceId' does not match currency of customer '$customerId'")
