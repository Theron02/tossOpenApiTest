package com.autotrading

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AutoTradingApplication

fun main(args: Array<String>) {
    runApplication<AutoTradingApplication>(*args)
}