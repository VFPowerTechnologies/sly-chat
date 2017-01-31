package io.slychat.messenger.services.ui

/**
 * @property successful Whether or not the reset request was successful. When false, errorMessage is non-null.
 * @property emailIsReleased Wheter or not the email is already released from the account. Only set when successful is true.
 * @property phoneNumberIsReleased Wheter or not the phone number is already released from the account. Only set when successful is true.
 * @property errorMessage Only set when successful is false.
 */
data class UIRequestResetAccountResult(
    val successful: Boolean,
    val emailIsReleased: Boolean?,
    val phoneNumberIsReleased: Boolean?,
    val errorMessage: String?
)