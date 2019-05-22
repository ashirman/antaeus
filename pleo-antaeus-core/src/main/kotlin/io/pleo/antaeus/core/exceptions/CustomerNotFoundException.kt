package io.pleo.antaeus.core.exceptions

class CustomerNotFoundException(val id: Int) : EntityNotFoundException("Customer", id)