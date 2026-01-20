package com.amurayada.yallego.model

import java.util.Date

data class User(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val phone: String = "",
    val userType: String = "user",
    val isOnline: Boolean = false,
    val createdAt: Date? = null
)