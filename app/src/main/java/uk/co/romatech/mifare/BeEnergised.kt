package uk.co.romatech.mifare

import android.content.Context
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request.Method.*
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class BeEnergised {
    private val uri = "https://romatech.beenergised.cloud/api"
    private val apiToken = "HvJgeil1ksjC3wzBi0seKmuiETjrKkvsbs~f8cjGI93qi8GvEPv2pdrYCBpb"

    fun getCard(activity: BeenergisedInterface, cardId: String) {
        doJsonRequest(GET, "/card/", activity, cardId)
    }

    fun getContact(activity: BeenergisedInterface, contact: String) {
        doJsonRequest(GET, "/contact/", activity, contact)
    }

    fun getEvse(activity: BeenergisedInterface, evse: String) {
        doJsonRequest(GET, "/evse/", activity, "?evse=$evse")
    }

    fun getCp(activity: BeenergisedInterface, cp: String) {
        doJsonRequest(GET, "/cp/", activity, cp)
    }

    fun putCard(activity: BeenergisedInterface, tag: String, number: String, reference: String) {
        val params = HashMap<String, String>()
        params["tag"] = tag
        params["number"] = number
        params["reference"] = reference
        params["active"] = "true"
        doJsonRequest(PUT, "/card/", activity, params)
    }

    fun remoteStart(activity: BeenergisedInterface, identifier: String, connector: String) {
        val params = HashMap<String, String>()
        params["identifier"] = identifier
        params["connector"] = connector
        doJsonRequest(POST, "/remote_start/", activity, params)
    }

    fun remoteStop(activity: BeenergisedInterface, transaction: String) {
        val params = HashMap<String, String>()
        params["transaction"] = transaction
        doJsonRequest(POST, "/remote_stop/", activity, params)
    }

    private fun doJsonRequest(method: Int, api: String, activity: BeenergisedInterface, params: Any? = null) {
        val url = StringBuilder("$uri$api")
        var jsonObject: JSONObject? = null
        when(method) {
            GET -> {
                params?.let {
                    url.append(params as String)
                }
            }
            POST -> {
                params?.let { map ->
                    @Suppress("UNCHECKED_CAST")
                    jsonObject = JSONObject(map as Map<String, String>)
                }
            }
            PUT -> {
                params?.let { map ->
                    @Suppress("UNCHECKED_CAST")
                    jsonObject = JSONObject(map as Map<String, String>)
                }
            }
        }
        val queue = Volley.newRequestQueue(activity as Context)
        val request = object: JsonObjectRequest(
            method,
            url.toString(),
            jsonObject,
            { response ->
                activity.beEnergisedCallback(api, response)
            },
            { error ->
                if (error is com.android.volley.TimeoutError) {
                    activity.timeoutCallback()
                } else if (error is com.android.volley.NoConnectionError) {
                    activity.noConnectionCallback()
                } else {
                    if (error.networkResponse.statusCode != java.net.HttpURLConnection.HTTP_NOT_FOUND) {
                        val jsonError = JSONObject(String(error.networkResponse.data))
                        activity.beEnergisedErrorCallback(
                            api,
                            error.networkResponse.statusCode,
                            jsonError.optString("message")
                        )
                    } else {
                        activity.pageNotFoundCallback(api)
                    }
                }
            }
        )
        {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["x-api-token"] = apiToken
                return headers
            }
        }
        request.retryPolicy = DefaultRetryPolicy(5000, 1, 1.0F)
        queue.add(request)
    }
}