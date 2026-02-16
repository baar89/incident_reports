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

---

## 2) Important schema notes for **your current DB**

Based on the JSON schema you shared:

- `admins` auth collection exists ✅
- `incident_reports` exists ✅
- incident type field is named **`type`** (not `incident_type`) ✅
- `incident_reports.responders` relation points to **`responders` collection** (`pbc_3325602110`) ⚠️

### Why tasks may not show

Your app logs in as `admins`, but `incident_reports.responders` currently references a different collection (`responders`).

So this rule in `incident_reports`:

```text
responders = @request.auth.id
```

will only work if `responders` stores IDs from the same auth collection. Right now it does not, so assigned-task filtering can fail.

### Recommended fix (best)

In PocketBase Admin UI:

1. Open `incident_reports` collection.
2. Edit field `responders` relation.
3. Change relation target collection from `responders` to **`admins`**.
4. Keep `maxSelect = 1` (or change to multi-select if you want multiple assigned fire admins).

Then use these rules:

- **List Rule**
  ```text
  @request.auth.id != "" && (responders = @request.auth.id || responders ?= @request.auth.id)
  ```
- **View Rule**
  ```text
  @request.auth.id != "" && (responders = @request.auth.id || responders ?= @request.auth.id)
  ```
- **Update Rule**
  ```text
  @request.auth.id != "" && (responders = @request.auth.id || responders ?= @request.auth.id)
  ```

> The app now supports both single-relation (`=`) and multi-relation (`?=`) filters.

---

## 3) Collection fields expected by the app

### `admins` (Auth collection)

Used fields:
- `first_name` (text)
- `last_name` (text)
- `position` (text)
- `email` (default auth field)
- `password` (default auth field)

### `incident_reports` (base collection)

Used fields:
- `type` (select/text, e.g. fire/accident/landslide)
- `description` (text)
- `status` (select: `pending`, `ongoing`, `resolved`)
- `responders` (relation to `admins`) ← important
- `latitude` (number)
- `longitude` (number)
- `incident_image` (file)

---

## 4) Sample API calls

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

curl "http://127.0.0.1:8090/api/collections/incident_reports/records?filter=(responders%20%3D%20%22${ADMIN_ID}%22%20%7C%7C%20responders%20%3F%3D%20%22${ADMIN_ID}%22)&sort=-created" \
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

---

## 5) Troubleshooting

- **No records in task list**: check `incident_reports.responders` relation target and rules first.
- **401/403**: token missing/expired or PocketBase API rules are too strict.
- **Cannot connect from emulator**: ensure PocketBase is running on `0.0.0.0:8090` and Android URL is `10.0.2.2`.
- **HTTP blocked**: app enables cleartext traffic for local development.
