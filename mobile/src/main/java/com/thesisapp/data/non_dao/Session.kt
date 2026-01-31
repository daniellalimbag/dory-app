package com.thesisapp.data.non_dao

data class Session(
    val id: Int,
    val fileName: String,
    val date: String,
    val time: String,
    val swimmerName: String,
    val swimmerId: Int,
    val swimmerSpecialty: String? = null
)
