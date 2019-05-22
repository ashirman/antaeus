package io.pleo.antaeus.core.services

import io.pleo.antaeus.models.Currency
import java.math.BigDecimal

//dummy version of CurrencyConvertionService which convert currency one-to-one without any actual exchange rates
//just to show the basic idea so
class CurrencyConversionService {
    fun convert(from: Currency, to: Currency, amount: BigDecimal) = amount
}