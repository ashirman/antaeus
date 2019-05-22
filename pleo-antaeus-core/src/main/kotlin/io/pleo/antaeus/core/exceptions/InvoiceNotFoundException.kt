package io.pleo.antaeus.core.exceptions

class InvoiceNotFoundException(val id: Int) : EntityNotFoundException("Invoice", id)