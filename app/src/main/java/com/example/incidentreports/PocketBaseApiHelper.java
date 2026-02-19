package com.example.incidentreports;

import static com.example.incidentreports.BuildConfig.POCKETBASE_URL;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles REST communication with PocketBase.
 */
public class PocketBaseApiHelper {
    public static final String BASE_URL = POCKETBASE_URL;
    private static final String TAG = "PocketBaseApiHelper";

    private final RequestQueue requestQueue;

    public PocketBaseApiHelper(Context context) {
        requestQueue = Volley.newRequestQueue(context.getApplicationContext());
    }

    public interface AuthCallback {
        void onSuccess(String token, String userId, String fullName);
        void onError(String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface IncidentListCallback {
        void onSuccess(List<IncidentReport> incidents);
        void onError(String message);
    }

    public interface IncidentCallback {
        void onSuccess(IncidentReport incidentReport);
        void onError(String message);
    }

    public void loginAdmin(String email, String password, AuthCallback callback) {
        String url = BASE_URL + "/api/collections/admins/auth-with-password";
        JSONObject body = new JSONObject();

        try {
            body.put("identity", email);
            body.put("password", password);
        } catch (JSONException e) {
            callback.onError(e.getMessage());
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    try {
                        String token = response.getString("token");
                        JSONObject record = response.getJSONObject("record");
                        String responderId = record.optString("responder", "");
                        String fullName = record.optString("first_name", "") + " " + record.optString("last_name", "");
                        Log.d(TAG, "Login successful. ResponderID: " + responderId);
                        callback.onSuccess(token, responderId.trim(), fullName.trim());
                    } catch (JSONException e) {
                        callback.onError("Unable to parse login response.");
                    }
                },
                error -> callback.onError(parseVolleyError(error)));

        requestQueue.add(request);
    }

    public void registerAdmin(String firstName, 
                              String middleName, 
                              String lastName, 
                              String email, 
                              String password, 
                              String contactNumber, 
                              String extension,
                              SimpleCallback callback) {
        
        // 1. Create Responder record first to get an ID
        String responderUrl = BASE_URL + "/api/collections/responders/records";
        JSONObject responderBody = new JSONObject();
        try {
            responderBody.put("unit_name", firstName + "'s Unit"); // Default value
            responderBody.put("department", "Fire");
            responderBody.put("contact_number", contactNumber);
            responderBody.put("is_available", true);
        } catch (JSONException e) {
            callback.onError(e.getMessage());
            return;
        }

        JsonObjectRequest responderRequest = new JsonObjectRequest(Request.Method.POST, responderUrl, responderBody,
                responderResponse -> {
                    String responderId = responderResponse.optString("id");
                    
                    // 2. Now create Admin record with the responderId
                    createAdminRecord(firstName, middleName, lastName, email, password, responderId, extension, callback);
                },
                error -> callback.onError("Failed to create responder: " + parseVolleyError(error)));

        requestQueue.add(responderRequest);
    }

    private void createAdminRecord(String firstName, 
                                   String middleName, 
                                   String lastName, 
                                   String email, 
                                   String password, 
                                   String responderId, 
                                   String extension, 
                                   SimpleCallback callback) {
        String url = BASE_URL + "/api/collections/admins/records";
        JSONObject body = new JSONObject();

        try {
            body.put("first_name", firstName);
            body.put("middle_name", middleName);
            body.put("last_name", lastName);
            body.put("email", email);
            body.put("password", password);
            body.put("passwordConfirm", password);
            body.put("responder", responderId);
            body.put("extension", extension);
            body.put("position", "admin"); // Default position
        } catch (JSONException e) {
            callback.onError(e.getMessage());
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> callback.onSuccess(),
                error -> callback.onError("Failed to create admin: " + parseVolleyError(error)));

        requestQueue.add(request);
    }

    public void fetchAssignedIncidents(String token, String responderId, IncidentListCallback callback) {
        // Use ?= which is the standard PocketBase operator for relation fields
        String filter = "responders ?= \"" + responderId + "\"";
        
        Log.d(TAG, "Fetching incidents for responderId: " + responderId + " with filter: " + filter);

        String url = Uri.parse(BASE_URL + "/api/collections/incident_reports/records")
                .buildUpon()
                .appendQueryParameter("filter", filter)
                .appendQueryParameter("sort", "-created")
                .toString();

        JsonObjectRequest request = new AuthJsonRequest(Request.Method.GET, url, null, token,
                response -> {
                    List<IncidentReport> incidents = new ArrayList<>();
                    JSONArray items = response.optJSONArray("items");
                    Log.d(TAG, "Response items count: " + (items != null ? items.length() : 0));
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject obj = items.optJSONObject(i);
                            if (obj != null) {
                                incidents.add(parseIncident(obj));
                            }
                        }
                    }
                    callback.onSuccess(incidents);
                },
                error -> {
                    Log.e(TAG, "Error fetching incidents: " + parseVolleyError(error));
                    callback.onError(parseVolleyError(error));
                });

        requestQueue.add(request);
    }

    public void fetchIncidentById(String token, String incidentId, IncidentCallback callback) {
        String url = BASE_URL + "/api/collections/incident_reports/records/" + incidentId;
        JsonObjectRequest request = new AuthJsonRequest(Request.Method.GET, url, null, token,
                response -> callback.onSuccess(parseIncident(response)),
                error -> callback.onError(parseVolleyError(error)));
        requestQueue.add(request);
    }

    public void updateIncidentStatus(String token, String incidentId, String newStatus, SimpleCallback callback) {
        String url = BASE_URL + "/api/collections/incident_reports/records/" + incidentId;
        JSONObject body = new JSONObject();
        try {
            body.put("status", newStatus);
        } catch (JSONException e) {
            callback.onError(e.getMessage());
            return;
        }

        JsonObjectRequest request = new AuthJsonRequest(Request.Method.PATCH, url, body, token,
                response -> callback.onSuccess(),
                error -> callback.onError(parseVolleyError(error)));

        requestQueue.add(request);
    }

    public String getFileUrl(IncidentReport report) {
        if (!report.hasImage()) {
            return "";
        }
        return BASE_URL + "/api/files/" + report.getCollectionId() + "/" + report.getId() + "/" + report.getImageFileName();
    }

    private IncidentReport parseIncident(JSONObject obj) {
        String id = obj.optString("id", "");
        String collectionId = obj.optString("collectionId", "");
        String type = obj.optString("type", "Unknown");
        String description = obj.optString("description", "No description");
        String status = obj.optString("status", "pending");
        String created = obj.optString("created", "");
        String latitude = obj.optString("latitude", "");
        String longitude = obj.optString("longitude", "");
        String address = obj.optString("address", "No address");

        String image = "";
        Object imgField = obj.opt("incident_image");
        if (imgField instanceof JSONArray) {
            JSONArray arr = (JSONArray) imgField;
            if (arr.length() > 0) {
                image = arr.optString(0, "");
            }
        } else if (imgField instanceof String) {
            image = (String) imgField;
        }

        return new IncidentReport(id, collectionId, type, description, status, created, latitude, longitude, address, image);
    }

    private String parseVolleyError(VolleyError error) {
        if (error.networkResponse != null && error.networkResponse.data != null) {
            return new String(error.networkResponse.data);
        }
        return error.getMessage() != null ? error.getMessage() : "Network error";
    }

    private static class AuthJsonRequest extends JsonObjectRequest {
        private final String token;

        public AuthJsonRequest(int method,
                               String url,
                               JSONObject jsonRequest,
                               String token,
                               com.android.volley.Response.Listener<JSONObject> listener,
                               com.android.volley.Response.ErrorListener errorListener) {
            super(method, url, jsonRequest, listener, errorListener);
            this.token = token;
        }

        @NonNull
        @Override
        public Map<String, String> getHeaders() throws AuthFailureError {
            Map<String, String> headers = new HashMap<>(super.getHeaders());
            headers.put("Authorization", "Bearer " + token);
            headers.put("Content-Type", "application/json");
            return headers;
        }
    }
}
