# Local Development Guide

## Running with Admin Endpoints

The admin endpoints for Bible data management are **only available in local development**. They are completely disabled in production.

### To enable admin endpoints locally:

1. **Set the `local` profile:**
   ```bash
   export SPRING_PROFILES_ACTIVE=local
   ```

2. **Or run with profile flag:**
   ```bash
   java -jar app.jar --spring.profiles.active=local
   ```

3. **Or in application.yml:**
   ```yaml
   spring:
     profiles:
       active: local
   ```

### Admin Endpoints (Local Only)

When running with `local` profile, these endpoints are available:

- `GET /api/admin/bible/status` - Check Bible data status
- `POST /api/admin/bible/load-data` - Manually load Bible data
- `POST /api/admin/bible/generate-embeddings` - Generate embeddings
- `GET /api/admin/bible/embedding-progress` - Check embedding progress
- `POST /api/admin/bible/import-embeddings?filePath=...` - Import embeddings from JSON

**Security:** These endpoints require ADMIN role and are only available when:
- Running with `local` profile
- Authenticated as a user with ADMIN role

### Production

In production (without `local` profile):
- AdminController is **completely disabled** via `@Profile("local")`
- These endpoints do not exist in production
- No security risk - the controller class is not even loaded

### Testing

Tests use both `test` and `local` profiles to enable admin endpoints for testing:
```java
@ActiveProfiles({"test", "local"})
```

