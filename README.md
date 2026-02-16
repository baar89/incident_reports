# Fire Incident Responder (Android Java + PocketBase)

This app is an Android (Java) client for fire department personnel. It authenticates against PocketBase `admins` and reads/updates records in `incident_reports`.

## 1) Connect the app to your PocketBase database

### A. Start PocketBase

```bash
./pocketbase serve --http=0.0.0.0:8090
```

If you run the app on the Android emulator, the app reaches your machine using `http://10.0.2.2:8090`.

### B. Set the PocketBase URL used by Android

The app reads the API base URL from `BuildConfig.POCKETBASE_URL`:

```kts
buildConfigField("String", "POCKETBASE_URL", "\"http://10.0.2.2:8090\"")
```

File: `app/build.gradle.kts`

> If testing on a real device on the same Wi‑Fi, replace `10.0.2.2` with your computer LAN IP (example: `192.168.1.20`).

### C. Create required PocketBase collections

#### `admins` (Auth collection)

Required fields used by this app:
- `first_name` (text)
- `last_name` (text)
- `position` (text)
- `email` (default auth field)
- `password` (default auth field)

#### `incident_reports` (base collection)

Recommended fields:
- `incident_type` (text)
- `description` (text)
- `status` (select: `pending`, `ongoing`, `resolved`)
- `responders` (relation or multi-relation to `admins`)
- `latitude` (text or number)
- `longitude` (text or number)
- `incident_image` (file)

### D. Set API rules (important)

Allow authenticated admins to:
- list/read incidents assigned to themselves
- update status on assigned incidents

Example list rule (adjust to your schema):

```text
@request.auth.id != "" && responders ?= @request.auth.id
```

Example update rule:

```text
@request.auth.id != "" && responders ?= @request.auth.id
```

## 2) Sample API calls

### Register admin

```bash
curl -X POST "http://127.0.0.1:8090/api/collections/admins/records" \
  -H "Content-Type: application/json" \
  -d '{
    "first_name":"Juan",
    "last_name":"Dela Cruz",
    "position":"Fire Officer",
    "email":"juan@fire.local",
    "password":"12345678",
    "passwordConfirm":"12345678"
  }'
```

### Login admin

```bash
curl -X POST "http://127.0.0.1:8090/api/collections/admins/auth-with-password" \
  -H "Content-Type: application/json" \
  -d '{"identity":"juan@fire.local","password":"12345678"}'
```

### Get assigned incidents

```bash
TOKEN="<paste_token>"
ADMIN_ID="<paste_admin_id>"

curl "http://127.0.0.1:8090/api/collections/incident_reports/records?filter=(responders%20%3F%3D%20%22${ADMIN_ID}%22)&sort=-created" \
  -H "Authorization: Bearer ${TOKEN}"
```

### Update incident status (PATCH)

```bash
TOKEN="<paste_token>"
INCIDENT_ID="<incident_record_id>"

curl -X PATCH "http://127.0.0.1:8090/api/collections/incident_reports/records/${INCIDENT_ID}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"status":"ongoing"}'
```

Then:

```bash
curl -X PATCH "http://127.0.0.1:8090/api/collections/incident_reports/records/${INCIDENT_ID}" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"status":"resolved"}'
```

## 3) Troubleshooting

- **Cannot connect from emulator**: ensure PocketBase is running on `0.0.0.0:8090` and Android URL is `10.0.2.2`.
- **HTTP blocked**: app enables cleartext traffic for local development.
- **No records in task list**: verify `responders` includes logged-in admin id and list rule allows it.
- **401/403**: token missing/expired or PocketBase API rules are too strict.
