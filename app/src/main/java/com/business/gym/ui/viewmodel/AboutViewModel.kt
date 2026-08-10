package com.business.gym.ui.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class AboutViewModel : ViewModel() {
    private val aboutDatabase = FirebaseDatabase.getInstance().getReference("info_about")
    private val contactDatabase = FirebaseDatabase.getInstance().getReference("info_contact")

    private val _aboutTitle = mutableStateOf("")
    val aboutTitle: State<String> = _aboutTitle

    private val _aboutDescription = mutableStateOf("")
    val aboutDescription: State<String> = _aboutDescription

    private val _aboutServices = mutableStateOf("")
    val aboutServices: State<String> = _aboutServices

    private val _aboutFooter = mutableStateOf("")
    val aboutFooter: State<String> = _aboutFooter

    private val _contactTitle = mutableStateOf("")
    val contactTitle: State<String> = _contactTitle

    private val _contactPhone = mutableStateOf("")
    val contactPhone: State<String> = _contactPhone

    init {
        fetchAboutInfo()
        fetchContactInfo()
    }

    private fun fetchAboutInfo() {
        aboutDatabase.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _aboutTitle.value = snapshot.child("title").getValue(String::class.java) ?: ""
                _aboutDescription.value = snapshot.child("description").getValue(String::class.java) ?: ""
                _aboutServices.value = snapshot.child("services").getValue(String::class.java) ?: ""
                _aboutFooter.value = snapshot.child("footer").getValue(String::class.java) ?: ""
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun fetchContactInfo() {
        contactDatabase.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _contactTitle.value = snapshot.child("title").getValue(String::class.java) ?: ""
                _contactPhone.value = snapshot.child("phone").getValue(String::class.java) ?: ""
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun updateAboutTitle(title: String) = aboutDatabase.child("title").setValue(title)
    fun updateAboutDescription(desc: String) = aboutDatabase.child("description").setValue(desc)
    fun updateAboutServices(services: String) = aboutDatabase.child("services").setValue(services)
    fun updateAboutFooter(footer: String) = aboutDatabase.child("footer").setValue(footer)
    fun updateContactTitle(title: String) = contactDatabase.child("title").setValue(title)
    fun updateContactPhone(phone: String) = contactDatabase.child("phone").setValue(phone)
}
