package com.example.incidentreports;

import android.content.Context;
import android.net.Uri;

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
 * Sample endpoints used in this app:
 * - POST /api/collections/admins/auth-with-password
 * - POST /api/collections/admins/records
 * - GET /api/collections/incident_reports/records?filter=(responders = "<responderId>" || responders ?= "<responderId>")
 * - PATCH /api/collections/incident_reports/records/{recordId}
 */
public class PocketBaseApiHelper {
    public static final String BASE_URL = BuildConfig.POCKETBASE_URL;

    private final RequestQueue requestQueue;

    public PocketBaseApiHelper(Context context) {
        requestQueue = Volley.newRequestQueue(context.getApplicationContext());
    }

    public interface AuthCallback {
        void onSuccess(String token, String userId, String fullName, String responderId);

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
                        String userId = record.getString("id");
                        String fullName = record.optString("first_name", "") + " " + record.optString("last_name", "");
                        // In your DB schema, admins.extension can store the linked responders record id.
                        String responderId = record.optString("extension", "");
                        callback.onSuccess(token, userId.trim(), fullName.trim(), responderId.trim());
                    } catch (JSONException e) {
                        callback.onError("Unable to parse login response.");
                    }
                },
                error -> callback.onError(parseVolleyError(error)));

        requestQueue.add(request);
    }

    public void registerAdmin(String firstName,
                              String lastName,
                              String position,
                              String email,
                              String password,
                              String responderId,
                              SimpleCallback callback) {
        String url = BASE_URL + "/api/collections/admins/records";
        JSONObject body = new JSONObject();

        try {
            body.put("first_name", firstName);
            body.put("last_name", lastName);
            body.put("position", position);
            body.put("email", email);
            body.put("password", password);
            body.put("passwordConfirm", password);
            if (responderId != null && !responderId.trim().isEmpty()) {
                body.put("extension", responderId.trim());
            }
        } catch (JSONException e) {
            callback.onError(e.getMessage());
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> callback.onSuccess(),
                error -> callback.onError(parseVolleyError(error)));

        requestQueue.add(request);
    }

    public void fetchAssignedIncidents(String token, String responderId, IncidentListCallback callback) {
        // incident_reports.responders is linked to the responders collection in the provided DB schema.
        String filter = "(responders = \"" + responderId + "\" || responders ?= \"" + responderId + "\")";
        String url = Uri.parse(BASE_URL + "/api/collections/incident_reports/records")
                .buildUpon()
                .appendQueryParameter("filter", filter)
                .appendQueryParameter("sort", "-created")
                .toString();

        JsonObjectRequest request = new AuthJsonRequest(Request.Method.GET, url, null, token,
                response -> {
                    List<IncidentReport> incidents = new ArrayList<>();
                    JSONArray items = response.optJSONArray("items");
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
                error -> callback.onError(parseVolleyError(error)));

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
        // Your current PocketBase schema uses field name "type".
        // Keep a fallback to "incident_type" for compatibility with earlier schema versions.
        String type = obj.optString("type", obj.optString("incident_type", "Unknown"));
        String description = obj.optString("description", "No description");
        String status = obj.optString("status", "pending");
        String created = obj.optString("created", "");
        String latitude = obj.optString("latitude", "");
        String longitude = obj.optString("longitude", "");

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

        return new IncidentReport(id, collectionId, type, description, status, created, latitude, longitude, image);
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
