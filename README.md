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

## 2) Compatibility with your exact schema (admins + responders)

Your schema has:
- `admins` auth collection (used for login/register)
- `incident_reports.responders` relation to `responders` collection (not to `admins`)

This app is now adjusted for that design by using:
- `admins.extension` as the **linked responder record id**
- incident query filter on `incident_reports.responders` using that responder id

### Required setup steps

1. Create or identify a record in `responders` for each fire staff account.
2. Copy that responders record `id`.
3. Set `admins.extension = <responders.id>` for the corresponding admin account.
   - You can set this during registration in the app using **Responder ID** field, or from PocketBase admin panel.

### Required `incident_reports` rules for this schema

Because auth identity is `admins`, but assignment field points to `responders`, rules should compare against `@request.auth.extension`:

- **List Rule**
  ```text
  @request.auth.id != "" && (responders = @request.auth.extension || responders ?= @request.auth.extension)
  ```
- **View Rule**
  ```text
  @request.auth.id != "" && (responders = @request.auth.extension || responders ?= @request.auth.extension)
  ```
- **Update Rule**
  ```text
  @request.auth.id != "" && (responders = @request.auth.extension || responders ?= @request.auth.extension)
  ```

> If `extension` is empty, the app cannot resolve assignments and will show a guidance message.

---

## 3) Collection fields expected by the app

### `admins` (Auth collection)

Used fields:
- `first_name` (text)
- `last_name` (text)
- `position` (text)
- `extension` (**stores linked responders id**)
- `email` (default auth field)
- `password` (default auth field)

### `incident_reports` (base collection)

Used fields:
- `type` (select/text, e.g. fire/accident/landslide)
- `description` (text)
- `status` (select: `pending`, `ongoing`, `resolved`)
- `responders` (relation to `responders`)
- `latitude` (number)
- `longitude` (number)
- `incident_image` (file)

---

## 4) Sample API calls

### Register admin (with linked responder id in extension)

```bash
curl -X POST "http://127.0.0.1:8090/api/collections/admins/records" \
  -H "Content-Type: application/json" \
  -d '{
    "first_name":"Juan",
    "last_name":"Dela Cruz",
    "position":"Fire Officer",
    "extension":"<responders_record_id>",
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
RESPONDER_ID="<responders_record_id>"

curl "http://127.0.0.1:8090/api/collections/incident_reports/records?filter=(responders%20%3D%20%22${RESPONDER_ID}%22%20%7C%7C%20responders%20%3F%3D%20%22${RESPONDER_ID}%22)&sort=-created" \
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

- **No records in task list**:
  - ensure `admins.extension` is set to a valid `responders.id`
  - ensure each incident has matching `incident_reports.responders`
  - ensure list/view/update rules use `@request.auth.extension`
- **401/403**: token missing/expired or PocketBase API rules are too strict.
- **Cannot connect from emulator**: ensure PocketBase is running on `0.0.0.0:8090` and Android URL is `10.0.2.2`.
- **HTTP blocked**: app enables cleartext traffic for local development.
