package uk.co.romatech.mifare

import org.json.JSONObject

interface BeenergisedInterface {
    fun beEnergisedCallback(api: String, response: JSONObject)
    fun beEnergisedErrorCallback(api: String, code: Int, message: String)
    fun timeoutCallback()
    fun pageNotFoundCallback(api: String)
    fun noConnectionCallback()
}